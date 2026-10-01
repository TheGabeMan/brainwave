package dev.gabrie.brainwave.ai

import dev.gabrie.brainwave.settings.NoteLanguage
import kotlin.math.roundToInt

/**
 * One downloadable on-device speech model.
 *
 * [sha256] and [sizeBytes] are pinned in the app, not read from the server: the
 * download is trusted only if it is byte-for-byte the file that was reviewed.
 * A server that is compromised, or a connection that is intercepted, can then
 * at worst make the download fail — never make the phone unpack something else.
 */
data class SpeechModel(
    val language: NoteLanguage,
    /** Also the name of the single top-level folder inside the zip. */
    val upstreamName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    val sizeMegabytes: Int get() = (sizeBytes / 1_000_000.0).roundToInt()
}

/**
 * The models the app can download. Both are published by Alpha Cephei under
 * Apache 2.0, as listed on <https://alphacephei.com/vosk/models>, and the sizes
 * and checksums below were verified against the upstream catalogue's MD5 values.
 *
 * Updating a model means changing the entry here: the new checksum makes the
 * installed copy count as stale, and it is downloaded again.
 */
object SpeechModelCatalog {

    private val models = listOf(
        SpeechModel(
            language = NoteLanguage.ENGLISH,
            upstreamName = "vosk-model-small-en-us-0.15",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            sizeBytes = 41_205_931L,
            sha256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498",
        ),
        SpeechModel(
            language = NoteLanguage.DUTCH,
            upstreamName = "vosk-model-small-nl-0.22",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-nl-0.22.zip",
            sizeBytes = 40_441_176L,
            sha256 = "039811c3b829de64e4f123a9f684a53784005b212a346ac0b899dc7efce2ed0a",
        ),
    )

    fun forLanguage(language: NoteLanguage): SpeechModel = models.first { it.language == language }
}
