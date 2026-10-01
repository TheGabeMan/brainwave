package dev.gabrie.brainwave.ai

import dev.gabrie.brainwave.settings.AppSettings
import java.io.File
import java.io.IOException

/** Anything that can turn a recording into text. */
interface SpeechToText {

    /** @param apiKey only meaningful for engines that talk to a server. */
    suspend fun transcribe(audio: File, settings: AppSettings, apiKey: String?): String
}

open class SpeechToTextException(message: String, cause: Throwable? = null) : IOException(message, cause)
