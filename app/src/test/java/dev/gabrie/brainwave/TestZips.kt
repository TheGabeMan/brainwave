package dev.gabrie.brainwave

import dev.gabrie.brainwave.ai.ModelInstaller
import dev.gabrie.brainwave.ai.SpeechModel
import dev.gabrie.brainwave.settings.NoteLanguage
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds small archives shaped like the real model zips, or deliberately broken ones. */
object TestZips {

    const val TOP = "vosk-model-test-1.0"

    /** An entry named [name]; a null body makes it a directory. */
    fun build(into: File, vararg entries: Pair<String, ByteArray?>): File {
        ZipOutputStream(into.outputStream()).use { zip ->
            for ((name, body) in entries) {
                zip.putNextEntry(ZipEntry(name))
                if (body != null) zip.write(body)
                zip.closeEntry()
            }
        }
        return into
    }

    /** A well-formed model archive: one top-level folder holding am/final.mdl and more. */
    fun valid(into: File, extra: Map<String, String> = emptyMap()): File = build(
        into,
        "$TOP/" to null,
        "$TOP/am/" to null,
        "$TOP/am/final.mdl" to "acoustic-model".toByteArray(),
        "$TOP/conf/model.conf" to "--sample-frequency=16000".toByteArray(),
        *extra.map { (k, v) -> "$TOP/$k" to v.toByteArray() }.toTypedArray(),
    )

    fun modelFor(archive: File, language: NoteLanguage = NoteLanguage.DUTCH) = SpeechModel(
        language = language,
        upstreamName = TOP,
        url = "http://unused.invalid/model.zip",
        sizeBytes = archive.length(),
        sha256 = ModelInstaller().sha256(archive),
    )
}
