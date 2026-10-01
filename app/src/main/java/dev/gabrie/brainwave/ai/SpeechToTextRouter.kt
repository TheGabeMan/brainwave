package dev.gabrie.brainwave.ai

import dev.gabrie.brainwave.settings.AppSettings
import dev.gabrie.brainwave.settings.SpeechEngine
import java.io.File

/**
 * Picks the engine the user selected.
 *
 * On-device is the default and needs nothing configured; the cloud engine is
 * there for anyone who would rather point at Groq's free tier or their own
 * whisper server.
 */
class SpeechToTextRouter(
    private val onDevice: SpeechToText,
    private val cloud: SpeechToText,
) : SpeechToText {

    override suspend fun transcribe(audio: File, settings: AppSettings, apiKey: String?): String =
        when (settings.speechEngine) {
            SpeechEngine.ON_DEVICE -> onDevice.transcribe(audio, settings, null)
            SpeechEngine.CLOUD -> cloud.transcribe(audio, settings, apiKey)
        }
}
