package dev.gabrie.brainwave.mail

import java.io.File

/**
 * Copies a recording to a short-lived share folder under a friendly name.
 *
 * Two reasons not to hand the original to the mail app. The recording lives
 * under an internal name (`brainwave-1790946…​.m4a`) that would show up as the
 * attachment's name; and sharing a copy means the mail app is only ever given
 * access to that one file, not to the folder the recordings live in.
 *
 * Pure `java.io`, so it is covered by ordinary unit tests.
 */
object AttachmentStaging {

    private const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000

    /** Copies [attachment] into [exportsDir] and returns the copy. Old copies are tidied away. */
    fun stage(exportsDir: File, attachment: MailAttachment, now: Long = System.currentTimeMillis()): File {
        exportsDir.mkdirs()
        exportsDir.listFiles()
            ?.filter { now - it.lastModified() > MAX_AGE_MILLIS }
            ?.forEach { it.delete() }

        val target = File(exportsDir, safeName(attachment.fileName))
        attachment.file.copyTo(target, overwrite = true)
        // copyTo keeps the source's timestamp; the age check should count from now.
        target.setLastModified(now)
        return target
    }

    /** A bare file name: no directories, nothing a filesystem or a mail client would choke on. */
    fun safeName(name: String): String {
        val bare = name.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = bare.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").trim().trimStart('.')
        return cleaned.ifBlank { "brainwave.m4a" }
    }
}
