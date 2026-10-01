package dev.gabrie.brainwave.ai

import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** What Claude extracted from a transcript. */
data class ClaudeAnalysis(
    val title: String,
    val dueAt: LocalDateTime?,
    val hasTime: Boolean,
)

/**
 * Optional enrichment pass over a transcript: a better title, and due dates the
 * rule-based parser cannot reach ("the Thursday after the school holidays").
 *
 * This talks to the Messages API over plain HTTP rather than through the
 * Anthropic Java SDK. The SDK is a JVM-server library — on Android it drags in
 * a large dependency graph for what is one small request, and the app already
 * has OkHttp for transcription. Everything user-facing degrades to
 * [DueDateParser] / [TitleGenerator] if this path is off, unconfigured, or
 * failing, so the network call is never load-bearing.
 */
class ClaudeClient(private val http: OkHttpClient = defaultClient()) {

    class ClaudeException(message: String) : IOException(message)

    suspend fun analyze(
        transcript: String,
        apiKey: String,
        model: String,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ClaudeAnalysis = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
            put("system", SYSTEM_PROMPT)
            putJsonObject("output_config") {
                // A title and a date do not need deep reasoning; low effort keeps
                // the round trip short and cheap.
                put("effort", "low")
                putJsonObject("format") {
                    put("type", "json_schema")
                    put("schema", SCHEMA)
                }
            }
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put(
                            "content",
                            buildString {
                                append("Current local date and time: ")
                                append(now.format(DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd'T'HH:mm")))
                                append(" (")
                                append(zone.id)
                                append(")\n\nVoice note transcript:\n")
                                append(transcript)
                            },
                        )
                    }
                )
            }
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ClaudeException("Claude request failed (HTTP ${response.code}): ${raw.take(300)}")
            }

            val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
                ?: throw ClaudeException("Claude returned a malformed response.")

            // A safety decline arrives as HTTP 200 with stop_reason "refusal" and
            // no usable content. Treat it as "no answer" and let the caller fall
            // back to the offline parser.
            if (root["stop_reason"]?.jsonPrimitive?.content == "refusal") {
                throw ClaudeException("Claude declined to analyse this note.")
            }

            val text = root["content"]?.jsonArray
                ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                ?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?: throw ClaudeException("Claude returned no text block.")

            parseAnalysis(text)
        }
    }

    private fun parseAnalysis(text: String): ClaudeAnalysis {
        val obj = runCatching { Json.parseToJsonElement(text.trim()).jsonObject }.getOrNull()
            ?: throw ClaudeException("Claude returned non-JSON content.")

        val title = obj["title"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (title.isEmpty()) throw ClaudeException("Claude returned an empty title.")

        val rawDue = obj["due_date"]?.jsonPrimitive?.content?.trim().orEmpty()
        val dueAt = rawDue.takeIf { it.isNotEmpty() }?.let { value ->
            runCatching { LocalDateTime.parse(value) }.getOrNull()
                ?: runCatching { java.time.LocalDate.parse(value).atStartOfDay() }.getOrNull()
        }

        val hasTime = obj["has_time"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        return ClaudeAnalysis(title, dueAt, hasTime && dueAt != null)
    }

    private companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        val SYSTEM_PROMPT = """
            You turn a spoken personal note into a short title and, when one is
            mentioned, a due date.

            Rules:
            - title: an imperative summary of the note, at most 64 characters, no
              trailing punctuation, no quotation marks, in the language of the
              transcript. Do not repeat the date in the title.
            - due_date: the moment the note is due, resolved against the current
              local date and time given by the user, formatted YYYY-MM-DDTHH:MM.
              Return an empty string if the note mentions no deadline at all. Do
              not invent one.
            - has_time: true only if the note named a time of day. If it named
              only a day, set has_time false and use 09:00 in due_date.
            Respond only with the JSON object.
        """.trimIndent()

        // due_date is a plain string with "" for absent rather than a nullable
        // type, because union types are the least portable corner of JSON Schema.
        val SCHEMA: JsonObject = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") {
                    put("type", "string")
                    put("description", "Short imperative title, max 64 characters.")
                }
                putJsonObject("due_date") {
                    put("type", "string")
                    put("description", "Local date-time as YYYY-MM-DDTHH:MM, or \"\" if none.")
                }
                putJsonObject("has_time") {
                    put("type", "boolean")
                    put("description", "True when the note stated a time of day.")
                }
            }
            put("required", buildJsonArray { add("title"); add("due_date"); add("has_time") })
            put("additionalProperties", false)
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
