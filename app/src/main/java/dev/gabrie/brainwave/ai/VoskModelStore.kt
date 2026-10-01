package dev.gabrie.brainwave.ai

import dev.gabrie.brainwave.settings.NoteLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model

/** The model for the chosen language has not been downloaded (yet). */
class SpeechModelMissingException(val language: NoteLanguage) : SpeechToTextException(
    "The ${language.displayName} speech model has not been downloaded yet. " +
        "Download it in Settings, under Speech to text."
)

/**
 * Loads an installed Vosk model and keeps it resident.
 *
 * Loading costs a second or two and a chunk of memory, so exactly one model
 * stays loaded; switching language closes the previous one. Fetching and
 * verifying the files is [SpeechModelManager]'s job — this only opens them.
 */
class VoskModelStore(private val manager: SpeechModelManager) {

    private val mutex = Mutex()
    private var loaded: Pair<NoteLanguage, Model>? = null

    init {
        runCatching { LibVosk.setLogLevel(LogLevel.WARNINGS) }
    }

    suspend fun model(language: NoteLanguage): Model = withContext(Dispatchers.IO) {
        mutex.withLock {
            loaded?.let { (cachedLanguage, cachedModel) ->
                if (cachedLanguage == language) return@withLock cachedModel
                runCatching { cachedModel.close() }
                loaded = null
            }

            if (!manager.isInstalled(language)) throw SpeechModelMissingException(language)

            val model = try {
                Model(manager.directory(language).absolutePath)
            } catch (e: Exception) {
                throw SpeechToTextException("Could not load the ${language.displayName} speech model.", e)
            }
            loaded = language to model
            model
        }
    }

    fun release() {
        loaded?.second?.let { runCatching { it.close() } }
        loaded = null
    }
}
