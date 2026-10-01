package dev.gabrie.brainwave.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.gabrie.brainwave.AppContainer
import dev.gabrie.brainwave.mail.MailHandoff
import dev.gabrie.brainwave.mail.OutgoingMail
import dev.gabrie.brainwave.ai.ModelStatus
import dev.gabrie.brainwave.settings.AppSettings
import dev.gabrie.brainwave.settings.MailMethod
import dev.gabrie.brainwave.settings.NoteLanguage
import dev.gabrie.brainwave.settings.SecretStore
import dev.gabrie.brainwave.settings.SmtpSecurity
import dev.gabrie.brainwave.ui.container
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Editable copy of the settings.
 *
 * The form is the source of truth while the screen is open and every change is
 * written through to storage. Reading the persisted flow back into the fields
 * instead would fight the text cursor on each keystroke.
 */
data class SettingsForm(
    val settings: AppSettings = AppSettings(),
    val smtpPassword: String = "",
    val transcriptionKey: String = "",
    val claudeKey: String = "",
    val loaded: Boolean = false,
    val testing: Boolean = false,
    /** One line saying where calendar entries go, or why they cannot yet. */
    val calendarStatus: String = "",
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _form = MutableStateFlow(SettingsForm())
    val form: StateFlow<SettingsForm> = _form.asStateFlow()

    /** Per-language state of the downloadable speech models. */
    val modelStatuses: StateFlow<Map<NoteLanguage, ModelStatus>> = container.speechModels.status

    fun downloadModel(language: NoteLanguage) = container.speechModels.download(language)

    fun cancelModelDownload(language: NoteLanguage) = container.speechModels.cancel(language)

    /** The test message for the mail-app method, which only a screen can open. */
    private val handoffs = Channel<MailHandoff.Request>(Channel.BUFFERED)
    val handoffFlow = handoffs.receiveAsFlow()

    private val messages = Channel<String>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            _form.value = SettingsForm(
                settings = container.settingsRepository.current(),
                smtpPassword = container.secretStore.get(SecretStore.SMTP_PASSWORD).orEmpty(),
                transcriptionKey = container.secretStore.get(SecretStore.TRANSCRIPTION_API_KEY).orEmpty(),
                claudeKey = container.secretStore.get(SecretStore.CLAUDE_API_KEY).orEmpty(),
                loaded = true,
            )
            refreshCalendarStatus()
        }
    }

    suspend fun refreshCalendarStatus() {
        val writer = container.calendarWriter
        val settings = container.settingsRepository.current()
        val status = when {
            !writer.hasPermission() -> "Calendar access has not been allowed yet."
            else -> {
                val target = writer.target(settings.calendarId)
                when {
                    target != null -> "Entries go to \u201c${target.name}\u201d (${target.account})."
                    writer.writableCalendars().isEmpty() -> "No calendar on this phone accepts new entries."
                    else -> "Choose which calendar to use."
                }
            }
        }
        _form.update { it.copy(calendarStatus = status) }
    }

    fun refreshCalendar() {
        viewModelScope.launch { refreshCalendarStatus() }
    }

    /** One coroutine, so the catch-up sees the new value rather than racing the write. */
    fun setCalendarEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(calendarEnabled = enabled) }
            _form.update { it.copy(settings = it.settings.copy(calendarEnabled = enabled)) }
            if (enabled) container.coordinator.catchUpCalendar()
            refreshCalendarStatus()
        }
    }

    fun edit(transform: (AppSettings) -> AppSettings) {
        val next = transform(_form.value.settings)
        _form.update { it.copy(settings = next) }
        viewModelScope.launch { container.settingsRepository.update { next } }
    }

    fun setSmtpPassword(value: String) = setSecret(SecretStore.SMTP_PASSWORD, value) {
        _form.update { form -> form.copy(smtpPassword = value) }
    }

    fun setTranscriptionKey(value: String) = setSecret(SecretStore.TRANSCRIPTION_API_KEY, value) {
        _form.update { form -> form.copy(transcriptionKey = value) }
    }

    fun setClaudeKey(value: String) = setSecret(SecretStore.CLAUDE_API_KEY, value) {
        _form.update { form -> form.copy(claudeKey = value) }
    }

    private fun setSecret(name: String, value: String, updateForm: () -> Unit) {
        updateForm()
        viewModelScope.launch { container.secretStore.put(name, value) }
    }

    fun setSmtpSecurity(security: SmtpSecurity) = edit { settings ->
        // Port and security travel together; changing one without the other is
        // the single most common way to get an unexplained connection timeout.
        val port = when (security) {
            SmtpSecurity.STARTTLS -> 587
            SmtpSecurity.SSL -> 465
            SmtpSecurity.NONE -> 25
        }
        settings.copy(smtpSecurity = security, smtpPort = port)
    }

    fun sendTestEmail() {
        viewModelScope.launch {
            val state = _form.value

            if (state.settings.mailMethod == MailMethod.MAIL_APP) {
                if (state.settings.recipientEmail.isBlank()) {
                    messages.send("Fill in the recipient email first.")
                } else {
                    handoffs.send(
                        MailHandoff.Request(
                            OutgoingMail(
                                subject = "[brainwave] Test email",
                                text = "If you are reading this, Brainwave can hand mail to your mail app.",
                            ),
                            state.settings.recipientEmail,
                        )
                    )
                }
                return@launch
            }

            if (!state.settings.mailConfigured) {
                messages.send("Fill in the SMTP host, the sender and the recipient first.")
                return@launch
            }

            _form.update { it.copy(testing = true) }
            val result = runCatching {
                container.mailer.send(
                    mail = OutgoingMail(
                        subject = "[brainwave] Test email",
                        text = "If you are reading this, Brainwave can send mail from this phone.",
                    ),
                    settings = state.settings,
                    password = state.smtpPassword,
                )
            }
            _form.update { it.copy(testing = false) }

            messages.send(
                result.fold(
                    onSuccess = { "Test email sent to ${state.settings.recipientEmail}" },
                    onFailure = { "Failed: ${it.message}" },
                )
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(this.container) }
        }
    }
}
