package dev.gabrie.brainwave.ai

import android.util.Log
import dev.gabrie.brainwave.settings.AppSettings
import java.time.LocalDateTime

/**
 * The result of turning note text into a title and a due date.
 *
 * [dueAt] is null when neither path found a date — that is the signal for the
 * recording flow to ask the user out loud.
 */
data class Analysis(
    val title: String,
    val dueAt: LocalDateTime?,
    val hasTime: Boolean,
    val usedClaude: Boolean = false,
)

/**
 * Runs the offline rules first, then upgrades the result with Claude when the
 * user has enabled it.
 *
 * The ordering matters: the rules always produce something, so a missing key,
 * an offline phone or an API error costs quality but never blocks saving a
 * brainwave.
 */
class BrainwaveAnalyzer(private val claude: ClaudeClient = ClaudeClient()) {

    suspend fun analyze(body: String, settings: AppSettings, claudeApiKey: String?): Analysis {
        val parsedDue = DueDateParser.parse(
            text = body,
            defaultHour = settings.defaultDueHour,
            language = settings.noteLanguage,
        )
        val offline = Analysis(
            title = TitleGenerator.generate(body, parsedDue?.matchedSpans.orEmpty(), settings.noteLanguage),
            dueAt = parsedDue?.dateTime,
            hasTime = parsedDue?.hasTime ?: false,
        )

        if (!settings.useClaude || claudeApiKey.isNullOrBlank()) return offline

        return try {
            val remote = claude.analyze(body, claudeApiKey, settings.claudeModel)
            Analysis(
                title = remote.title.ifBlank { offline.title },
                // Claude sees phrasings the regexes miss, but if it found nothing
                // and the rules did, keep the rules' date rather than losing one.
                dueAt = remote.dueAt ?: offline.dueAt,
                hasTime = if (remote.dueAt != null) remote.hasTime else offline.hasTime,
                usedClaude = true,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Claude analysis failed, using offline result", e)
            offline
        }
    }

    /** Parses a spoken answer to "when is this due?" — date only, no title. */
    fun parseSpokenDueDate(answer: String, settings: AppSettings): ParsedDue? =
        DueDateParser.parse(
            text = answer,
            defaultHour = settings.defaultDueHour,
            language = settings.noteLanguage,
        )

    private companion object {
        const val TAG = "BrainwaveAnalyzer"
    }
}
