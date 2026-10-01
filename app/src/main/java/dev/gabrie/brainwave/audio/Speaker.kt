package dev.gabrie.brainwave.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Text-to-speech for the "when is this due?" follow-up question.
 *
 * Uses the platform TTS service (whatever engine the user has installed), so
 * there is no bundled speech dependency. Audio attributes are set to
 * ASSISTANCE_ACCESSIBILITY/SPEECH so a connected headset gets the prompt
 * instead of the loudspeaker.
 */
class Speaker(context: Context) {

    private val counter = AtomicInteger(0)
    private var ready = false

    /** Applied once the engine reports ready; set before the first say(). */
    private var pendingLocale: Locale = Locale.getDefault()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            runCatching { tts.language = pendingLocale }
        }
    }

    /** Speaks [text] and suspends until playback finishes (or fails). */
    suspend fun say(text: String): Unit = suspendCancellableCoroutine { cont ->
        if (!ready || text.isBlank()) {
            cont.resume(Unit)
            return@suspendCancellableCoroutine
        }
        val id = "brainwave-${counter.incrementAndGet()}"

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId == id && cont.isActive) cont.resume(Unit)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id && cont.isActive) cont.resume(Unit)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == id && cont.isActive) cont.resume(Unit)
            }
        })

        cont.invokeOnCancellation { runCatching { tts.stop() } }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    /**
     * Switches voice language. Falls back silently when the installed engine has
     * no voice for [locale] — a wrong-accent prompt beats no prompt at all.
     */
    fun setLanguage(locale: Locale) {
        pendingLocale = locale
        if (ready) runCatching { tts.language = locale }
    }

    fun stop() {
        runCatching { tts.stop() }
    }

    fun release() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }
}
