package dev.gabrie.brainwave.ai

import dev.gabrie.brainwave.settings.AppSettings
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * Speech-to-text against any OpenAI-compatible `/v1/audio/transcriptions`
 * endpoint — the optional alternative to running Vosk on the phone.
 *
 * The endpoint is a setting rather than a constant on purpose: the same code
 * talks to Groq's free tier, to a whisper.cpp / faster-whisper server on the
 * home network, or to a paid provider, so nobody is locked to one vendor and
 * the app itself stays free of proprietary dependencies.
 */
class CloudTranscriber(private val http: OkHttpClient = defaultClient()) : SpeechToText {

    override suspend fun transcribe(audio: File, settings: AppSettings, apiKey: String?): String =
        withContext(Dispatchers.IO) {
            if (settings.transcriptionEndpoint.isBlank()) {
                throw SpeechToTextException("No transcription endpoint configured. Set one in Settings.")
            }

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audio.name, audio.asRequestBody(AUDIO_MEDIA_TYPE))
                .addFormDataPart("model", settings.transcriptionModel)
                .addFormDataPart("response_format", "json")
                .addFormDataPart("language", settings.noteLanguage.code)
                .build()

            val request = Request.Builder()
                .url(settings.transcriptionEndpoint)
                .post(body)
                .apply { if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey") }
                .build()

            http.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw SpeechToTextException("Transcription failed (HTTP ${response.code}): ${payload.take(300)}")
                }
                val text = runCatching {
                    Json.parseToJsonElement(payload).jsonObject["text"]?.jsonPrimitive?.content
                }.getOrNull()

                text?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw SpeechToTextException("Transcription returned no text.")
            }
        }

    private companion object {
        val AUDIO_MEDIA_TYPE = "audio/m4a".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // Whisper-class models take a while on longer clips.
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
