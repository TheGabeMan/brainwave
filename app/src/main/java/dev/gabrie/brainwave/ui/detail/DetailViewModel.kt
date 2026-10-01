package dev.gabrie.brainwave.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.gabrie.brainwave.AppContainer
import dev.gabrie.brainwave.audio.AudioPlayer
import dev.gabrie.brainwave.data.Brainwave
import dev.gabrie.brainwave.mail.MailHandoff
import dev.gabrie.brainwave.ui.container
import dev.gabrie.brainwave.util.Time
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val original: Brainwave? = null,
    val title: String = "",
    val body: String = "",
    val dueAt: LocalDateTime? = null,
    val hasTime: Boolean = false,
    val completed: Boolean = false,
    val playing: Boolean = false,
    val defaultDueHour: Int = 9,
    val calendarEnabled: Boolean = true,
    val closed: Boolean = false,
) {
    val hasAudio: Boolean get() = original?.audioPath?.let { File(it).exists() } == true

    /** Drives whether the save button is enabled. */
    val dirty: Boolean
        get() {
            val source = original ?: return false
            return title.trim() != source.title ||
                body.trim() != source.body ||
                dueAt?.let { Time.toMillis(it) } != source.dueAt ||
                hasTime != source.dueHasTime
        }
}

class DetailViewModel(
    private val container: AppContainer,
    private val brainwaveId: Long,
) : ViewModel() {

    private val player = AudioPlayer()

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val messages = Channel<String>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    /** "Save & email" through the mail app: only a screen can open another app. */
    private val handoffs = Channel<MailHandoff.Request>(Channel.BUFFERED)
    val handoffFlow = handoffs.receiveAsFlow()

    init {
        viewModelScope.launch {
            val settings = container.settingsRepository.current()
            val brainwave = container.repository.find(brainwaveId)
            if (brainwave == null) {
                _uiState.update { it.copy(loading = false, missing = true) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    original = brainwave,
                    title = brainwave.title,
                    body = brainwave.body,
                    dueAt = brainwave.dueAt?.let { Time.toLocal(it) },
                    hasTime = brainwave.dueHasTime,
                    completed = brainwave.completed,
                    defaultDueHour = settings.defaultDueHour,
                    calendarEnabled = settings.calendarEnabled,
                )
            }
        }
    }

    fun setTitle(value: String) = _uiState.update { it.copy(title = value) }

    fun setBody(value: String) = _uiState.update { it.copy(body = value) }

    fun setDue(dateTime: LocalDateTime?, hasTime: Boolean) =
        _uiState.update { it.copy(dueAt = dateTime, hasTime = hasTime) }

    /** Re-runs the parser over the edited text — handy after typing a new date in. */
    fun rereadDueDateFromText() {
        viewModelScope.launch {
            val settings = container.settingsRepository.current()
            val parsed = container.analyzer.parseSpokenDueDate(_uiState.value.body, settings)
            if (parsed == null) {
                messages.send("No date found in the text.")
                return@launch
            }
            _uiState.update { it.copy(dueAt = parsed.dateTime, hasTime = parsed.hasTime) }
        }
    }

    fun save(resendEmail: Boolean = false) {
        viewModelScope.launch {
            val state = _uiState.value
            val original = state.original ?: return@launch

            container.coordinator.update(
                id = original.id,
                title = state.title.trim().ifBlank { original.title },
                body = state.body.trim(),
                dueAt = state.dueAt?.let { Time.toMillis(it) },
                dueHasTime = state.hasTime,
                resendNote = resendEmail,
            )
            // Re-read rather than patching our copy: the coordinator has just
            // stored things (the calendar entry's id) that the copy cannot know.
            container.repository.find(original.id)?.let { fresh ->
                _uiState.update { it.copy(original = fresh) }
            }
            val settings = container.settingsRepository.current()
            when {
                resendEmail && settings.sendsAutomatically -> messages.send("Saved — re-sending the email.")
                resendEmail && settings.handsOffToMailApp ->
                    container.coordinator.noteHandoff(original.id)?.let { handoffs.send(it) }
                resendEmail -> messages.send("Saved. Set up email in Settings to send it.")
                else -> messages.send("Saved.")
            }
        }
    }

    fun toggleCompleted() {
        viewModelScope.launch {
            val original = _uiState.value.original ?: return@launch
            val next = !original.completed
            container.coordinator.setCompleted(original.id, next)
            container.repository.find(original.id)?.let { fresh ->
                _uiState.update { it.copy(original = fresh, completed = next) }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            val original = _uiState.value.original ?: return@launch
            player.stop()
            container.coordinator.delete(original)
            _uiState.update { it.copy(closed = true) }
        }
    }

    fun togglePlayback() {
        val path = _uiState.value.original?.audioPath ?: return
        if (player.isPlaying) {
            player.stop()
            _uiState.update { it.copy(playing = false) }
            return
        }
        player.play(File(path)) {
            _uiState.update { it.copy(playing = false) }
        }
        _uiState.update { it.copy(playing = true) }
    }

    override fun onCleared() {
        super.onCleared()
        player.stop()
    }

    companion object {
        fun factory(brainwaveId: Long) = viewModelFactory {
            initializer { DetailViewModel(this.container, brainwaveId) }
        }
    }
}
