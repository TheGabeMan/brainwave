package dev.gabrie.brainwave.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.gabrie.brainwave.data.SortField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            recipientEmail = p[RECIPIENT] ?: "",
            smtpHost = p[SMTP_HOST] ?: "",
            smtpPort = p[SMTP_PORT] ?: 587,
            smtpSecurity = p[SMTP_SECURITY]?.let { runCatching { SmtpSecurity.valueOf(it) }.getOrNull() }
                ?: SmtpSecurity.STARTTLS,
            smtpUsername = p[SMTP_USERNAME] ?: "",
            fromAddress = p[FROM_ADDRESS] ?: "",
            fromName = p[FROM_NAME] ?: "Brainwave",
            transcriptionEndpoint = p[STT_ENDPOINT] ?: AppSettings.DEFAULT_TRANSCRIPTION_ENDPOINT,
            transcriptionModel = p[STT_MODEL] ?: AppSettings.DEFAULT_TRANSCRIPTION_MODEL,
            noteLanguage = NoteLanguage.fromCode(p[NOTE_LANGUAGE]),
            speechEngine = p[SPEECH_ENGINE]?.let { runCatching { SpeechEngine.valueOf(it) }.getOrNull() }
                ?: SpeechEngine.ON_DEVICE,
            useClaude = p[USE_CLAUDE] ?: false,
            claudeModel = p[CLAUDE_MODEL] ?: AppSettings.DEFAULT_CLAUDE_MODEL,
            calendarEnabled = p[CALENDAR_ENABLED] ?: true,
            calendarId = p[CALENDAR_ID] ?: AppSettings.CALENDAR_NOT_CHOSEN,
            remindersEnabled = p[REMINDERS_ENABLED] ?: true,
            reminderLeadMinutes = p[REMINDER_LEAD] ?: 0,
            defaultDueHour = p[DEFAULT_DUE_HOUR] ?: 9,
            sortField = p[SORT_FIELD]?.let { runCatching { SortField.valueOf(it) }.getOrNull() }
                ?: SortField.DUE_DATE,
            sortAscending = p[SORT_ASC] ?: true,
            showCompleted = p[SHOW_COMPLETED] ?: false,
            speakPrompts = p[SPEAK_PROMPTS] ?: true,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(current())
        context.settingsDataStore.edit { p ->
            p[RECIPIENT] = next.recipientEmail
            p[SMTP_HOST] = next.smtpHost
            p[SMTP_PORT] = next.smtpPort
            p[SMTP_SECURITY] = next.smtpSecurity.name
            p[SMTP_USERNAME] = next.smtpUsername
            p[FROM_ADDRESS] = next.fromAddress
            p[FROM_NAME] = next.fromName
            p[STT_ENDPOINT] = next.transcriptionEndpoint
            p[STT_MODEL] = next.transcriptionModel
            p[NOTE_LANGUAGE] = next.noteLanguage.code
            p[SPEECH_ENGINE] = next.speechEngine.name
            p[USE_CLAUDE] = next.useClaude
            p[CLAUDE_MODEL] = next.claudeModel
            p[CALENDAR_ENABLED] = next.calendarEnabled
            p[CALENDAR_ID] = next.calendarId
            p[REMINDERS_ENABLED] = next.remindersEnabled
            p[REMINDER_LEAD] = next.reminderLeadMinutes
            p[DEFAULT_DUE_HOUR] = next.defaultDueHour
            p[SORT_FIELD] = next.sortField.name
            p[SORT_ASC] = next.sortAscending
            p[SHOW_COMPLETED] = next.showCompleted
            p[SPEAK_PROMPTS] = next.speakPrompts
        }
    }

    private companion object {
        val RECIPIENT = stringPreferencesKey("recipient_email")
        val SMTP_HOST = stringPreferencesKey("smtp_host")
        val SMTP_PORT = intPreferencesKey("smtp_port")
        val SMTP_SECURITY = stringPreferencesKey("smtp_security")
        val SMTP_USERNAME = stringPreferencesKey("smtp_username")
        val FROM_ADDRESS = stringPreferencesKey("from_address")
        val FROM_NAME = stringPreferencesKey("from_name")
        val STT_ENDPOINT = stringPreferencesKey("stt_endpoint")
        val STT_MODEL = stringPreferencesKey("stt_model")
        val NOTE_LANGUAGE = stringPreferencesKey("note_language")
        val SPEECH_ENGINE = stringPreferencesKey("speech_engine")
        val USE_CLAUDE = booleanPreferencesKey("use_claude")
        val CLAUDE_MODEL = stringPreferencesKey("claude_model")
        val CALENDAR_ENABLED = booleanPreferencesKey("calendar_enabled")
        val CALENDAR_ID = longPreferencesKey("calendar_id")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_LEAD = intPreferencesKey("reminder_lead_minutes")
        val DEFAULT_DUE_HOUR = intPreferencesKey("default_due_hour")
        val SORT_FIELD = stringPreferencesKey("sort_field")
        val SORT_ASC = booleanPreferencesKey("sort_ascending")
        val SHOW_COMPLETED = booleanPreferencesKey("show_completed")
        val SPEAK_PROMPTS = booleanPreferencesKey("speak_prompts")
    }
}
