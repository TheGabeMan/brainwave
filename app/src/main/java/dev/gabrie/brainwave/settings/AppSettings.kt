package dev.gabrie.brainwave.settings

import dev.gabrie.brainwave.data.SortField
import dev.gabrie.brainwave.data.SortOrder

enum class SmtpSecurity { STARTTLS, SSL, NONE }

/**
 * Everything the user configures. Secrets live in [SecretStore], not here —
 * this object is safe to log and to back up.
 */
data class AppSettings(
    val recipientEmail: String = "",

    val smtpHost: String = "",
    val smtpPort: Int = 587,
    val smtpSecurity: SmtpSecurity = SmtpSecurity.STARTTLS,
    val smtpUsername: String = "",
    val fromAddress: String = "",
    val fromName: String = "Brainwave",

    /** Which language is spoken; picks the Vosk model and the parser phrasings. */
    val noteLanguage: NoteLanguage = NoteLanguage.default(),

    /** On-device by default: free, offline, and needs no key. */
    val speechEngine: SpeechEngine = SpeechEngine.ON_DEVICE,
    val transcriptionEndpoint: String = DEFAULT_TRANSCRIPTION_ENDPOINT,
    val transcriptionModel: String = DEFAULT_TRANSCRIPTION_MODEL,

    val useClaude: Boolean = false,
    val claudeModel: String = DEFAULT_CLAUDE_MODEL,

    /** Write each brainwave that has a due date into the phone's calendar. */
    val calendarEnabled: Boolean = true,
    /**
     * Which calendar entries go to, or [CALENDAR_NOT_CHOSEN]. Never guessed when
     * a phone has several: a work calendar and a family one look alike to code,
     * and a note about the shopping must not land in the wrong one.
     */
    val calendarId: Long = CALENDAR_NOT_CHOSEN,

    val remindersEnabled: Boolean = true,
    /** Minutes before [dueAt] to fire the local notification, 0 = at the due time. */
    val reminderLeadMinutes: Int = 0,
    /** Hour used for an all-day due date's reminder and calendar block. */
    val defaultDueHour: Int = 9,

    val sortField: SortField = SortField.DUE_DATE,
    val sortAscending: Boolean = true,
    val showCompleted: Boolean = false,

    val speakPrompts: Boolean = true,
) {
    val sortOrder: SortOrder get() = SortOrder(sortField, sortAscending)

    val effectiveFrom: String get() = fromAddress.ifBlank { smtpUsername }

    val mailConfigured: Boolean
        get() = smtpHost.isNotBlank() && recipientEmail.isNotBlank() && effectiveFrom.isNotBlank()

    val usesCloudSpeech: Boolean get() = speechEngine == SpeechEngine.CLOUD

    companion object {
        const val CALENDAR_NOT_CHOSEN = -1L
        // Only used when the cloud engine is selected. Free options that speak
        // this protocol include Groq and a self-hosted whisper.cpp server.
        const val DEFAULT_TRANSCRIPTION_ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
        const val DEFAULT_TRANSCRIPTION_MODEL = "whisper-large-v3-turbo"
        const val DEFAULT_CLAUDE_MODEL = "claude-opus-5"
    }
}
