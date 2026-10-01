package dev.gabrie.brainwave.ai

import dev.gabrie.brainwave.audio.PcmDecoder
import dev.gabrie.brainwave.settings.AppSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.Recognizer

/**
 * On-device speech-to-text.
 *
 * Free, works in aeroplane mode, needs no account, and the audio never leaves
 * the phone. The trade-off is that Vosk emits lower-case words with no
 * punctuation, so titles read a little flatter than a cloud model's would.
 */
class VoskTranscriber(private val models: VoskModelStore) : SpeechToText {

    override suspend fun transcribe(audio: File, settings: AppSettings, apiKey: String?): String =
        withContext(Dispatchers.Default) {
            if (!audio.exists()) throw SpeechToTextException("The recording is missing.")

            val model = models.model(settings.noteLanguage)
            val recognizer = try {
                Recognizer(model, SAMPLE_RATE.toFloat())
            } catch (e: Exception) {
                throw SpeechToTextException("Could not start the speech recogniser.", e)
            }

            val transcript = StringBuilder()
            try {
                PcmDecoder.decode(audio, SAMPLE_RATE) { pcm, length ->
                    // acceptWaveForm returns true at each end-of-utterance, which
                    // is the point a complete phrase can be collected.
                    if (recognizer.acceptWaveForm(pcm, length)) {
                        transcript.appendSegment(recognizer.result)
                    }
                }
                transcript.appendSegment(recognizer.finalResult)
            } catch (e: PcmDecoder.DecodeException) {
                throw SpeechToTextException(e.message ?: "Could not read the recording.", e)
            } finally {
                runCatching { recognizer.close() }
            }

            transcript.toString().trim().ifEmpty {
                throw SpeechToTextException("Nothing recognisable was said in that recording.")
            }
        }

    /** Vosk returns `{"text": "..."}`; empty segments are common and skippable. */
    private fun StringBuilder.appendSegment(json: String?) {
        if (json.isNullOrBlank()) return
        val text = runCatching {
            Json.parseToJsonElement(json).jsonObject["text"]?.jsonPrimitive?.content
        }.getOrNull()?.trim().orEmpty()

        if (text.isEmpty()) return
        if (isNotEmpty()) append(' ')
        append(text)
    }

    companion object {
        /** Every small Vosk model is trained at 16 kHz. */
        const val SAMPLE_RATE = 16_000
    }
}
