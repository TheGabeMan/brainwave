package dev.gabrie.brainwave.settings

import java.util.Locale

/**
 * The language people speak into the app.
 *
 * This drives three things at once, which is why it is one setting rather than
 * three: which Vosk model is loaded, which phrase tables the offline date
 * parser uses, and which voice the spoken prompts come out in.
 */
enum class NoteLanguage(
    val code: String,
    /**
     * Folder name under `filesDir/vosk/`. These are the names the bundled builds
     * used, so a model already unpacked on a phone is recognised as installed.
     */
    val modelDir: String,
    val displayName: String,
) {
    ENGLISH("en", "en-us", "English"),
    DUTCH("nl", "nl", "Nederlands"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(if (this == ENGLISH) "en-US" else code)

    companion object {
        fun fromCode(code: String?): NoteLanguage =
            entries.firstOrNull { it.code == code } ?: default()

        /** Matches the device language when it is one we support. */
        fun default(): NoteLanguage {
            val device = Locale.getDefault().language
            return entries.firstOrNull { it.code == device } ?: ENGLISH
        }
    }
}

enum class SpeechEngine {
    /** Vosk, running entirely on the phone from a bundled model. */
    ON_DEVICE,

    /** Any OpenAI-compatible /audio/transcriptions endpoint. */
    CLOUD,
}
