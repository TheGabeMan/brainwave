package dev.gabrie.brainwave.ui.record

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.gabrie.brainwave.AppContainer
import dev.gabrie.brainwave.audio.AudioRecorder
import dev.gabrie.brainwave.audio.HeadsetAudioRouter
import dev.gabrie.brainwave.audio.Speaker
import dev.gabrie.brainwave.data.Brainwave
import dev.gabrie.brainwave.ai.ModelStatus
import dev.gabrie.brainwave.settings.NoteLanguage
import dev.gabrie.brainwave.settings.SecretStore
import dev.gabrie.brainwave.settings.SpeechEngine
import dev.gabrie.brainwave.ui.container
import dev.gabrie.brainwave.util.Time
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RecordStage {
    /** Waiting for the microphone permission decision. */
    PERMISSION,
    RECORDING,
    /** Uploading, transcribing and analysing. */
    PROCESSING,
    /** Speaking "when is this due?" out loud. */
    ASKING_DUE,
    /** Recording the spoken answer to that question. */
    LISTENING_DUE,
    /** Editable result, ready to save. */
    REVIEW,
}

data class RecordUiState(
    val stage: RecordStage = RecordStage.PERMISSION,
    val elapsedMillis: Long = 0L,
    val amplitude: Float = 0f,
    val title: String = "",
    val body: String = "",
    val dueAt: LocalDateTime? = null,
    val hasTime: Boolean = false,
    val audioPath: String? = null,
    val statusText: String = "",
    val error: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val headsetConnected: Boolean = false,
    val dueQuestionAsked: Boolean = false,
    val defaultDueHour: Int = 9,
    val language: NoteLanguage = NoteLanguage.ENGLISH,
    val calendarEnabled: Boolean = true,
    /** Offer to download the speech model: on-device engine chosen, model missing. */
    val modelPrompt: Boolean = false,
    val modelSizeMegabytes: Int = 0,
    /** 0..1 while the model for [language] is downloading, else null. */
    val modelProgress: Float? = null,
) {
    val dueLabel: String
        get() = dueAt?.let { Time.formatDue(Time.toMillis(it), hasTime) } ?: "No due date"
}

/**
 * Drives capture end to end: record → transcribe → title + due date → ask for a
 * missing due date out loud → review → save.
 *
 * Every step degrades rather than dead-ends. If transcription fails the user
 * still lands on the review screen with the audio attached and an empty body
 * they can type into, so a captured thought is never lost to a network error.
 */
class RecordViewModel(
    private val container: AppContainer,
    application: Application,
) : ViewModel() {

    private val recorder = AudioRecorder(application)
    private val speaker = Speaker(application)
    private val router = HeadsetAudioRouter(application)

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private var meterJob: Job? = null
    private var listenTimeoutJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = container.settingsRepository.current()
            speaker.setLanguage(settings.noteLanguage.locale)
            _uiState.update {
                it.copy(
                    defaultDueHour = settings.defaultDueHour,
                    language = settings.noteLanguage,
                    calendarEnabled = settings.calendarEnabled,
                )
            }

            val models = container.speechModels
            val missing = settings.speechEngine == SpeechEngine.ON_DEVICE && !models.isInstalled(settings.noteLanguage)
            if (missing && models.status.value[settings.noteLanguage] !is ModelStatus.Downloading) {
                // Recording has already started — the thought is being caught
                // while this asks, never instead of it.
                _uiState.update {
                    it.copy(modelPrompt = true, modelSizeMegabytes = models.model(settings.noteLanguage).sizeMegabytes)
                }
            }
        }

        viewModelScope.launch {
            container.speechModels.status.collect { all ->
                _uiState.update { state ->
                    state.copy(modelProgress = (all[state.language] as? ModelStatus.Downloading)?.fraction)
                }
            }
        }
    }

    fun downloadModel() {
        container.speechModels.download(_uiState.value.language)
        _uiState.update { it.copy(modelPrompt = false) }
    }

    fun dismissModelPrompt() = _uiState.update { it.copy(modelPrompt = false) }

    // ------------------------------------------------------------ recording --

    /**
     * BLUETOOTH_CONNECT is a runtime permission on Android 12+, and without it
     * the headset's own microphone (which lives on the SCO link) can never be
     * selected — silently, with recording falling back to the phone's mic. It is
     * only worth asking for when a Bluetooth headset is actually connected.
     */
    fun requiredPermissions(): List<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && router.bluetoothHeadsetConnected()) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            startRecording()
        } else {
            _uiState.update {
                it.copy(
                    stage = RecordStage.REVIEW,
                    error = "Microphone access denied — you can still type this brainwave.",
                )
            }
        }
    }

    fun startRecording() {
        if (recorder.isRecording) return
        try {
            router.engage()
            val file = recorder.start()
            _uiState.update {
                it.copy(
                    stage = RecordStage.RECORDING,
                    audioPath = file.absolutePath,
                    error = null,
                    elapsedMillis = 0L,
                    headsetConnected = router.headsetConnected(),
                )
            }
            startMeter()
        } catch (e: Exception) {
            router.release()
            _uiState.update {
                it.copy(
                    stage = RecordStage.REVIEW,
                    error = "Could not start recording: ${e.message}",
                )
            }
        }
    }

    fun stopRecording() {
        if (!recorder.isRecording) return
        stopMeter()
        val file = recorder.stop()
        router.release()

        if (file == null) {
            _uiState.update {
                it.copy(
                    stage = RecordStage.REVIEW,
                    audioPath = null,
                    error = "That recording was too short to keep.",
                )
            }
            return
        }
        process(file)
    }

    /** Skips voice entirely; the review screen is a plain text editor. */
    fun switchToTyping() {
        if (recorder.isRecording) {
            stopMeter()
            recorder.cancel()
            router.release()
        }
        _uiState.update {
            it.copy(stage = RecordStage.REVIEW, audioPath = null, error = null)
        }
    }

    fun discard() {
        stopMeter()
        listenTimeoutJob?.cancel()
        if (recorder.isRecording) recorder.cancel()
        router.release()
        speaker.stop()
        _uiState.value.audioPath?.let { path -> runCatching { File(path).delete() } }
        _uiState.update { it.copy(audioPath = null) }
    }

    private fun startMeter() {
        meterJob?.cancel()
        meterJob = viewModelScope.launch {
            while (recorder.isRecording) {
                _uiState.update {
                    it.copy(amplitude = recorder.amplitude(), elapsedMillis = recorder.elapsedMillis())
                }
                delay(80)
            }
        }
    }

    private fun stopMeter() {
        meterJob?.cancel()
        meterJob = null
        _uiState.update { it.copy(amplitude = 0f) }
    }

    // ----------------------------------------------------------- processing --

    private fun process(audio: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(stage = RecordStage.PROCESSING, statusText = "Transcribing…") }

            val settings = container.settingsRepository.current()
            val sttKey = container.secretStore.get(SecretStore.TRANSCRIPTION_API_KEY)

            // A model still downloading is worth waiting for: the recording is
            // safe either way, and this beats failing a transcription that was
            // seconds from working.
            if (settings.speechEngine == SpeechEngine.ON_DEVICE &&
                container.speechModels.status.value[settings.noteLanguage] is ModelStatus.Downloading
            ) {
                _uiState.update { it.copy(statusText = "Downloading the speech model…") }
                container.speechModels.awaitReady(settings.noteLanguage)
                _uiState.update { it.copy(statusText = "Transcribing…") }
            }

            val transcript = try {
                container.transcription.transcribe(audio, settings, sttKey)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        stage = RecordStage.REVIEW,
                        error = "${e.message}\n\nThe recording is safe — add a title and text yourself, or try again.",
                        statusText = "",
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(statusText = "Summarising…") }
            val claudeKey = container.secretStore.get(SecretStore.CLAUDE_API_KEY)
            val analysis = container.analyzer.analyze(transcript, settings, claudeKey)

            _uiState.update {
                it.copy(
                    title = analysis.title,
                    body = transcript,
                    dueAt = analysis.dueAt,
                    hasTime = analysis.hasTime,
                    statusText = "",
                )
            }

            if (analysis.dueAt == null && !_uiState.value.dueQuestionAsked) {
                askForDueDate()
            } else {
                _uiState.update { it.copy(stage = RecordStage.REVIEW) }
            }
        }
    }

    // ------------------------------------------------------- due-date prompt --

    private fun askForDueDate() {
        viewModelScope.launch {
            val settings = container.settingsRepository.current()
            _uiState.update { it.copy(stage = RecordStage.ASKING_DUE, dueQuestionAsked = true) }

            if (settings.speakPrompts) {
                router.engage()
                speaker.setLanguage(settings.noteLanguage.locale)
                speaker.say(SpokenPrompts.dueQuestion(settings.noteLanguage))
            }

            // Straight from the question into listening, so the user can answer the
            // moment they hear it without touching the phone.
            startListeningForDueDate()
        }
    }

    fun startListeningForDueDate() {
        if (recorder.isRecording) return
        try {
            router.engage()
            recorder.start()
            _uiState.update { it.copy(stage = RecordStage.LISTENING_DUE, elapsedMillis = 0L) }
            startMeter()

            listenTimeoutJob?.cancel()
            listenTimeoutJob = viewModelScope.launch {
                delay(ANSWER_TIMEOUT_MILLIS)
                if (_uiState.value.stage == RecordStage.LISTENING_DUE) stopListeningForDueDate()
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(stage = RecordStage.REVIEW) }
        }
    }

    fun stopListeningForDueDate() {
        listenTimeoutJob?.cancel()
        if (!recorder.isRecording) {
            _uiState.update { it.copy(stage = RecordStage.REVIEW) }
            return
        }
        stopMeter()
        val answerFile = recorder.stop()
        router.release()

        if (answerFile == null) {
            _uiState.update { it.copy(stage = RecordStage.REVIEW) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(stage = RecordStage.PROCESSING, statusText = "Reading the date…") }
            val settings = container.settingsRepository.current()
            val key = container.secretStore.get(SecretStore.TRANSCRIPTION_API_KEY)

            val answer = runCatching { container.transcription.transcribe(answerFile, settings, key) }
                .getOrNull()
            // The answer clip is a throwaway; only the original note is attached.
            answerFile.delete()

            val parsed = answer?.let { container.analyzer.parseSpokenDueDate(it, settings) }

            if (parsed != null) {
                if (settings.speakPrompts) {
                    val spoken = Time.formatForSpeech(
                        Time.toMillis(parsed.dateTime), parsed.hasTime, settings.noteLanguage.locale
                    )
                    speaker.say(SpokenPrompts.confirmed(settings.noteLanguage, spoken))
                }
                _uiState.update {
                    it.copy(
                        stage = RecordStage.REVIEW,
                        dueAt = parsed.dateTime,
                        hasTime = parsed.hasTime,
                        statusText = "",
                    )
                }
            } else {
                if (settings.speakPrompts) speaker.say(SpokenPrompts.noDateHeard(settings.noteLanguage))
                _uiState.update { it.copy(stage = RecordStage.REVIEW, statusText = "") }
            }
        }
    }

    fun skipDueQuestion() {
        listenTimeoutJob?.cancel()
        speaker.stop()
        if (recorder.isRecording) {
            recorder.cancel()
            stopMeter()
        }
        router.release()
        _uiState.update { it.copy(stage = RecordStage.REVIEW, statusText = "") }
    }

    // --------------------------------------------------------------- review --

    fun setTitle(value: String) = _uiState.update { it.copy(title = value) }

    fun setBody(value: String) = _uiState.update { it.copy(body = value) }

    fun setDue(dateTime: LocalDateTime?, hasTime: Boolean) =
        _uiState.update { it.copy(dueAt = dateTime, hasTime = hasTime) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.saving) return@launch

            val body = state.body.trim()
            val title = state.title.trim().ifBlank {
                container.analyzer.analyze(body, container.settingsRepository.current(), null).title
            }
            if (body.isBlank() && state.audioPath == null) {
                _uiState.update { it.copy(error = "Nothing to save yet.") }
                return@launch
            }

            _uiState.update { it.copy(saving = true) }
            container.coordinator.create(
                Brainwave(
                    title = title,
                    body = body,
                    audioPath = state.audioPath,
                    dueAt = state.dueAt?.let { Time.toMillis(it) },
                    dueHasTime = state.hasTime,
                )
            )
            _uiState.update { it.copy(saving = false, saved = true) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMeter()
        listenTimeoutJob?.cancel()
        if (recorder.isRecording) recorder.cancel()
        router.release()
        speaker.release()
    }

    companion object {
        private const val ANSWER_TIMEOUT_MILLIS = 8_000L

        val Factory = viewModelFactory {
            initializer {
                RecordViewModel(
                    container = this.container,
                    application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application,
                )
            }
        }
    }
}
