package dev.gabrie.brainwave.ai

import dev.gabrie.brainwave.settings.NoteLanguage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Offline title generation: the first clause of the note, with the verbal
 * scaffolding people put in front of a spoken thought stripped off.
 *
 * "Um, note to self, book the ferry tickets before next Friday"
 *   -> "Book the ferry tickets"
 * "Eh, denk eraan om overmorgen de ferry te boeken"
 *   -> "De ferry te boeken"
 *
 * Note that on-device recognition returns lower-case text with no punctuation,
 * so the sentence split rarely fires there and the length cap does most of the
 * work. That is fine — the title is editable on the review screen.
 */
object TitleGenerator {

    private const val MAX_LENGTH = 64

    /** Filler that precedes the actual thought; stripped repeatedly from the front. */
    private val ENGLISH_LEAD_INS = listOf(
        "um", "uh", "er", "so", "okay", "ok", "right", "well", "hey",
        "note to self", "quick note", "reminder", "a reminder",
        "remind me to", "remind me that", "remind me",
        "i need to", "i have to", "i must", "i should", "i want to",
        "don't forget to", "dont forget to", "don't forget", "dont forget",
        "make sure to", "make sure i", "make sure",
        "todo", "to do", "brainwave", "idea",
    )

    private val DUTCH_LEAD_INS = listOf(
        "eh", "uh", "ehm", "nou", "dus", "oké", "oke", "ok", "goed", "even", "hé", "he",
        "notitie aan mezelf", "notitie voor mezelf", "kort briefje", "memo",
        "herinner me eraan om", "herinner me eraan dat", "herinner me eraan", "herinner me",
        "denk eraan om", "denk eraan dat", "denk eraan",
        "vergeet niet om", "vergeet niet dat", "vergeet niet",
        "zorg dat ik", "zorg ervoor dat", "zorg dat",
        "ik moet", "ik wil", "ik zou moeten", "ik ga",
        "todo", "idee", "brainwave",
    )

    private val ENGLISH_PREPOSITIONS = listOf("by", "before", "on", "due", "at", "until")
    private val DUTCH_PREPOSITIONS = listOf("voor", "vóór", "op", "om", "tegen", "uiterlijk", "tot")

    private val SENTENCE_END = Regex("(?<=[.!?;])\\s+")

    /**
     * @param dueSpans the pieces of [body] that expressed the date and time, as
     *   reported by the date parser. They are removed one by one because they are
     *   rarely contiguous — in "vrijdag middag om twee uur" the word "middag"
     *   sits between the date and the time.
     */
    fun generate(
        body: String,
        dueSpans: List<String> = emptyList(),
        language: NoteLanguage = NoteLanguage.ENGLISH,
    ): String {
        var text = body.trim()
        if (text.isEmpty()) return fallback(language)

        // Drop the phrases that produced the due date — the date belongs in the
        // due field, and repeating it in the title only makes it longer.
        if (dueSpans.any { it.isNotBlank() }) {
            dueSpans.filter { it.isNotBlank() }.sortedByDescending { it.length }.forEach { span ->
                // Lookarounds rather than \b, which does not hold next to an apostrophe ("'s middags").
                text = text.replace(
                    Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(span) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE),
                    " ",
                )
            }
            prepositionsFor(language).forEach { preposition ->
                text = text.replace(
                    Regex("\\b$preposition\\s+(?=\\s|$)", RegexOption.IGNORE_CASE), " "
                )
            }
        }

        var candidate = SENTENCE_END.split(text.trim()).firstOrNull { it.isNotBlank() }?.trim() ?: text
        candidate = stripLeadIns(candidate, language)
        candidate = candidate.trim().trim(',', '.', ';', ':', '-', ' ')

        if (candidate.isBlank()) return fallback(language)
        if (candidate.length > MAX_LENGTH) candidate = truncateAtWord(candidate)

        return candidate.replaceFirstChar { it.titlecase(language.locale) }
    }

    /** Single-phrase form, kept for callers that only have one string. */
    fun generate(
        body: String,
        dueMatchedText: String?,
        language: NoteLanguage = NoteLanguage.ENGLISH,
    ): String = generate(body, listOfNotNull(dueMatchedText), language)

    private fun leadInsFor(language: NoteLanguage) = when (language) {
        NoteLanguage.ENGLISH -> ENGLISH_LEAD_INS
        NoteLanguage.DUTCH -> DUTCH_LEAD_INS
    }

    private fun prepositionsFor(language: NoteLanguage) = when (language) {
        NoteLanguage.ENGLISH -> ENGLISH_PREPOSITIONS
        NoteLanguage.DUTCH -> DUTCH_PREPOSITIONS
    }

    private fun stripLeadIns(input: String, language: NoteLanguage): String {
        val leadIns = leadInsFor(language)
        var text = input
        var changed = true
        while (changed) {
            changed = false
            val lower = text.lowercase(language.locale)
            for (leadIn in leadIns) {
                // Longest-first would be tidier, but the loop repeats until the
                // string stops shrinking, which reaches the same fixed point.
                if (lower.startsWith("$leadIn ") || lower.startsWith("$leadIn,")) {
                    text = text.substring(leadIn.length).trimStart(',', ' ', ':', '-')
                    changed = true
                    break
                }
            }
        }
        return text
    }

    private fun truncateAtWord(input: String): String {
        val cut = input.take(MAX_LENGTH)
        val lastSpace = cut.lastIndexOf(' ')
        val body = if (lastSpace > MAX_LENGTH / 2) cut.substring(0, lastSpace) else cut
        return body.trimEnd(',', '.', ' ') + "…"
    }

    fun fallback(
        language: NoteLanguage = NoteLanguage.ENGLISH,
        date: LocalDate = LocalDate.now(),
    ): String {
        val formatted = date.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(language.locale)
        )
        return when (language) {
            NoteLanguage.ENGLISH -> "Brainwave $formatted"
            NoteLanguage.DUTCH -> "Brainwave $formatted"
        }
    }
}
