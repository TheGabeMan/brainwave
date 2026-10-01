package dev.gabrie.brainwave.ai

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Verifies a downloaded model archive and unpacks it into place.
 *
 * It handles a file that arrived over the internet, so it assumes nothing:
 *
 * - the SHA-256 is checked **before** a single byte is unpacked;
 * - every entry is confined to the target folder, so an entry named
 *   `../../something` ("zip-slip") is refused rather than written outside it;
 * - sizes are counted as bytes are actually inflated, not read from the zip's
 *   headers, which a hostile archive can understate, and capped;
 * - nothing replaces an existing model until the new one is complete and
 *   validated, so a failed update never destroys one that works.
 *
 * It has no Android dependencies, so it is covered by plain JVM unit tests.
 */
class ModelInstaller(
    private val maxUncompressedBytes: Long = 400L * 1024 * 1024,
    private val maxEntries: Int = 200,
) {

    class InstallException(message: String, cause: Throwable? = null) : IOException(message, cause)

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Installs [archive] as [destination], or throws and leaves [destination] untouched. */
    fun install(archive: File, model: SpeechModel, destination: File) {
        if (!sha256(archive).equals(model.sha256, ignoreCase = true)) {
            throw InstallException("The download did not match the expected checksum, so it was discarded.")
        }

        val parent = destination.absoluteFile.parentFile
            ?: throw InstallException("Invalid install location.")
        parent.mkdirs()
        val staging = File(parent, destination.name + ".partial")
        val backup = File(parent, destination.name + ".old")
        staging.deleteRecursively()
        backup.deleteRecursively()

        try {
            unzip(archive, staging, model.upstreamName)

            for (required in REQUIRED_FILES) {
                if (!File(staging, required).isFile) {
                    throw InstallException("The speech model is incomplete (missing $required).")
                }
            }
            // Recorded so a later catalogue update can tell this copy is out of date.
            File(staging, MARKER).writeText(model.sha256)

            // Swap in last. The old copy is only moved aside, and put back if the
            // move fails, so there is no moment with neither copy in place.
            val hadOld = destination.exists()
            if (hadOld && !destination.renameTo(backup)) {
                throw InstallException("Could not replace the existing speech model.")
            }
            if (!staging.renameTo(destination)) {
                if (hadOld) backup.renameTo(destination)
                throw InstallException("Could not move the speech model into place.")
            }
            backup.deleteRecursively()
        } catch (e: Throwable) {
            staging.deleteRecursively()
            throw e
        }
    }

    private fun unzip(archive: File, into: File, topLevel: String) {
        into.mkdirs()
        val root = into.canonicalFile
        var entries = 0
        var written = 0L

        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > maxEntries) throw InstallException("The archive contains too many files.")

                val name = entry.name.replace('\\', '/')
                if (!name.startsWith("$topLevel/")) {
                    throw InstallException("Unexpected entry in the archive: ${entry.name}")
                }
                val relative = name.removePrefix("$topLevel/")
                if (relative.isEmpty()) continue

                val target = File(root, relative).canonicalFile
                if (!target.path.startsWith(root.path + File.separator)) {
                    throw InstallException("An archive entry tried to leave the target folder: ${entry.name}")
                }

                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > maxUncompressedBytes) {
                            throw InstallException("The archive expands to more than expected.")
                        }
                        sink.write(buffer, 0, read)
                    }
                }
            }
        }
        if (entries == 0) throw InstallException("The archive is empty.")
    }

    companion object {
        /** The acoustic model: if this is present the model unpacked completely. */
        val REQUIRED_FILES = listOf("am/final.mdl")
        const val MARKER = ".sha256"
    }
}
