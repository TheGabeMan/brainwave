package dev.gabrie.brainwave.ai

import dev.gabrie.brainwave.settings.NoteLanguage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * A due date found in free text.
 *
 * [hasTime] is false when the text only named a day ("Friday"), in which case
 * [dateTime] carries the configured default hour and the calendar entry is
 * written as an all-day event.
 *
 * [matchedSpans] are the pieces of the *original* text that expressed the date
 * and time — for "vrijdag middag om twee uur de sleutel ophalen" that is
 * ["vrijdag", "om twee uur", "middag"]. The title generator removes exactly
 * these, which only works because they are slices of what the user said rather
 * than of any normalised form of it.
 */
data class ParsedDue(
    val dateTime: LocalDateTime,
    val hasTime: Boolean,
    val matchedSpans: List<String>,
) {
    val matchedText: String get() = matchedSpans.joinToString(" ")
}

/**
 * Offline natural-language date extraction, in English and Dutch.
 *
 * This is the always-available path: no network, no API key, and it is also the
 * fallback whenever Claude is disabled or fails.
 *
 * **Why numbers are normalised first.** The on-device speech model writes what
 * it hears, and what it hears is words: "half drie", "vijftien oktober",
 * "three thirty", "the fifteenth of october". It never emits digits. So before
 * any matching happens the text is rewritten with spelled-out numbers turned
 * into digits, and every later stage only has to understand digits.
 *
 * The rewrite changes the text, so [Mapped] remembers which slice of the
 * original every character came from. A match against "om 2 uur" can then be
 * reported as "om twee uur", which is what the title generator needs to strip.
 *
 * Everything language-specific lives in [Phrases]; the matching algorithm is
 * shared. Adding a language means adding one more [Phrases] table and a case in
 * [phrasesFor].
 *
 * The result depends only on its arguments, which is what makes it testable.
 */
object DueDateParser {

    fun parse(
        text: String,
        now: LocalDateTime = LocalDateTime.now(),
        defaultHour: Int = 9,
        language: NoteLanguage = NoteLanguage.ENGLISH,
    ): ParsedDue? {
        if (text.isBlank()) return null

        val phrases = phrasesFor(language)
        val mapped = normalise(text, phrases, language)
        val date = findDate(mapped.text, now, phrases, language)

        // A calendar date ("15 oktober", "15.09.2026") contains digits that must
        // not be read a second time as a clock time — "15.09" is not 15:09, and
        // "tegen 15 oktober" is not "at 15:00". Relative words like "vanavond"
        // are left in place because they legitimately carry a time of day too.
        val timeText = if (date != null && date.isCalendarDate) blank(mapped.text, date.range) else mapped.text

        val dayPart = findDayPart(timeText, phrases)
        val time = findTime(timeText, phrases, dayPart, hasDate = date != null)

        if (date != null) {
            if (time != null) {
                return ParsedDue(
                    dateTime = date.date.atTime(time.hour, time.minute),
                    hasTime = true,
                    matchedSpans = spans(mapped, listOf(date.range) + time.ranges),
                )
            }
            val exact = date.exact
            if (exact != null) return ParsedDue(exact, true, spans(mapped, listOf(date.range)))
            return ParsedDue(
                dateTime = date.date.atTime(defaultHour.coerceIn(0, 23), 0),
                hasTime = false,
                matchedSpans = spans(mapped, listOf(date.range)),
            )
        }

        // A bare time means today, unless today's copy of it already passed.
        val bareTime = time ?: return null
        var candidate = now.toLocalDate().atTime(bareTime.hour, bareTime.minute)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return ParsedDue(candidate, true, spans(mapped, bareTime.ranges))
    }

    private fun spans(mapped: Mapped, ranges: List<IntRange>): List<String> =
        ranges.map { mapped.spanOf(it) }.filter { it.isNotBlank() }.distinct()

    private fun blank(text: String, range: IntRange): String {
        val builder = StringBuilder(text)
        for (i in range) builder.setCharAt(i, ' ')
        return builder.toString()
    }

    // ----------------------------------------------------------- mapped text --

    /**
     * Lower-cased, whitespace-collapsed text in which every character remembers
     * the slice of the original it came from. When a word is replaced — "drie"
     * becomes "3" — the replacement inherits the whole word's slice.
     */
    private class Mapped private constructor(
        val text: String,
        private val starts: IntArray,
        private val ends: IntArray,
        private val original: String,
    ) {
        fun spanOf(range: IntRange): String {
            if (text.isEmpty() || range.isEmpty()) return ""
            val s = starts[range.first.coerceIn(0, text.length - 1)]
            val e = ends[range.last.coerceIn(0, text.length - 1)]
            return if (e <= s) "" else original.substring(s, e).trim()
        }

        fun replace(regex: Regex, transform: (MatchResult, String) -> String?): Mapped {
            val out = StringBuilder()
            val newStarts = ArrayList<Int>(text.length)
            val newEnds = ArrayList<Int>(text.length)
            var cursor = 0

            fun copy(from: Int, to: Int) {
                for (i in from until to) {
                    out.append(text[i])
                    newStarts.add(starts[i])
                    newEnds.add(ends[i])
                }
            }

            for (match in regex.findAll(text)) {
                val replacement = transform(match, text) ?: continue
                copy(cursor, match.range.first)
                val s = starts[match.range.first]
                val e = ends[match.range.last]
                for (ch in replacement) {
                    out.append(ch)
                    newStarts.add(s)
                    newEnds.add(e)
                }
                cursor = match.range.last + 1
            }
            copy(cursor, text.length)
            return Mapped(out.toString(), newStarts.toIntArray(), newEnds.toIntArray(), original)
        }

        companion object {
            fun of(original: String): Mapped {
                val out = StringBuilder(" ")
                val starts = arrayListOf(0)
                val ends = arrayListOf(0)
                var lastWasSpace = true

                for ((i, raw) in original.withIndex()) {
                    // Per-character, so the length never changes.
                    val ch = if (raw == '’' || raw == '‘') '\'' else raw.lowercaseChar()
                    if (ch.isWhitespace()) {
                        if (!lastWasSpace) {
                            out.append(' ')
                            starts.add(i)
                            ends.add(i + 1)
                            lastWasSpace = true
                        }
                    } else {
                        out.append(ch)
                        starts.add(i)
                        ends.add(i + 1)
                        lastWasSpace = false
                    }
                }
                if (!lastWasSpace) {
                    out.append(' ')
                    starts.add(original.length)
                    ends.add(original.length)
                }
                return Mapped(out.toString(), starts.toIntArray(), ends.toIntArray(), original)
            }
        }
    }

    // ------------------------------------------------------------ the tables --

    private enum class PeriodUnit { MINUTE, HOUR, DAY, WEEK, MONTH, YEAR }
    private enum class DayPart { AM, PM }

    /**
     * A clock phrase reduced to what was *said*: the hour as spoken, the
     * minutes, and a shift for idioms like "half drie", where the spoken hour
     * (3) is one past the actual time (2:30).
     *
     * AM/PM is decided on the spoken hour, because that is what a speaker
     * chooses it by: "tien voor acht" is 7:50, and it is the eight that says
     * morning.
     */
    private class Spoken(
        val hour: Int,
        val minute: Int,
        val hourShift: Int = 0,
        val meridiem: String = "",
    )

    private class Idiom(val regex: Regex, val build: (MatchResult) -> Spoken?)

    private class Numbers(
        val cardinals: Map<String, Int>,
        val ordinals: Map<String, Int>,
        /** "twee en twintig", "twenty-three": rewritten before single words. */
        val compounds: List<Pair<Regex, (MatchResult) -> String>>,
    ) {
        val cardinalRegex: Regex = wordAlternation(cardinals.keys)
        val ordinalRegex: Regex = wordAlternation(ordinals.keys)
    }

    private class Phrases(
        val numbers: Numbers,
        val idioms: List<Idiom>,
        val namedTimes: List<Pair<Regex, Pair<Int, Int>>>,
        /** Used only when a date was found, so "morning routine" alone is not a deadline. */
        val dayPartTimes: List<Pair<Regex, Pair<Int, Int>>>,
        val amMarkers: Regex,
        val pmMarkers: Regex,
        /** Ordered most specific first: "overmorgen" must beat "morgen". */
        val relativeDays: List<Pair<Regex, Long>>,
        val inDuration: Regex,
        val units: Map<String, PeriodUnit>,
        val fuzzyQuantities: Map<String, Long>,
        val weekdayPhrase: Regex,
        /** "volgende week vrijdag": the weekday of next week, not Monday. */
        val nextWeekWeekday: Regex,
        /** Qualifiers that push past the coming occurrence into the next week. */
        val nextWords: Set<String>,
        val nextPeriod: Regex,
        val periodUnits: Map<String, PeriodUnit>,
        val endOf: Regex,
        val ordinalSuffix: String,
        val dayMonthJoiner: String,
        /** Words between an ordinal and a month name: "the third *of* march". */
        val ordinalConnectors: Set<String>,
    )

    private fun wordAlternation(words: Collection<String>): Regex =
        Regex(
            words.sortedByDescending { it.length }
                .joinToString("|", prefix = "(?<![\\p{L}\\p{N}])(?:", postfix = ")(?![\\p{L}\\p{N}])") { Regex.escape(it) }
        )

    private fun hour(raw: String): Int? = raw.toIntOrNull()?.takeIf { it in 0..23 }
    private fun minute(raw: String): Int? = raw.toIntOrNull()?.takeIf { it in 0..59 }

    private const val MERIDIEM = "(am|pm|a\\.m\\.|p\\.m\\.)"

    // ------------------------------------------------------------- English --

    private val EN_UNITS = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
    private val EN_TENS = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50)
    private val EN_TEENS = mapOf(
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val EN_ORDINAL_UNITS = listOf(
        "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth",
    )

    private val EN_NUMBERS: Numbers = run {
        val cardinals = buildMap {
            EN_UNITS.forEachIndexed { i, w -> put(w, i + 1) }
            putAll(EN_TEENS)
            putAll(EN_TENS)
        }
        val ordinals = buildMap {
            EN_ORDINAL_UNITS.forEachIndexed { i, w -> put(w, i + 1) }
            put("tenth", 10); put("eleventh", 11); put("twelfth", 12); put("thirteenth", 13)
            put("fourteenth", 14); put("fifteenth", 15); put("sixteenth", 16); put("seventeenth", 17)
            put("eighteenth", 18); put("nineteenth", 19); put("twentieth", 20); put("thirtieth", 30)
        }
        Numbers(
            cardinals = cardinals,
            ordinals = ordinals,
            compounds = listOf(
                Regex("(?<![\\p{L}])(twenty|thirty|forty|fifty)[ -](${EN_UNITS.joinToString("|")})(?![\\p{L}])") to
                    { m: MatchResult -> (EN_TENS.getValue(m.groupValues[1]) + EN_UNITS.indexOf(m.groupValues[2]) + 1).toString() },
                Regex("(?<![\\p{L}])(twenty|thirty)[ -](${EN_ORDINAL_UNITS.joinToString("|")})(?![\\p{L}])") to
                    { m: MatchResult -> (EN_TENS.getValue(m.groupValues[1]) + EN_ORDINAL_UNITS.indexOf(m.groupValues[2]) + 1).toString() },
            ),
        )
    }

    private val ENGLISH = Phrases(
        numbers = EN_NUMBERS,
        idioms = listOf(
            Idiom(Regex("\\bquarter past (\\d{1,2})\\b")) { m -> hour(m.groupValues[1])?.let { Spoken(it, 15) } },
            Idiom(Regex("\\bquarter to (\\d{1,2})\\b")) { m -> hour(m.groupValues[1])?.let { Spoken(it, 45, -1) } },
            Idiom(Regex("\\bhalf past (\\d{1,2})\\b")) { m -> hour(m.groupValues[1])?.let { Spoken(it, 30) } },
            Idiom(Regex("\\b(?:at|around) (\\d{1,2}) (past|to) (\\d{1,2})\\b")) { m ->
                val minutes = minute(m.groupValues[1])
                val hour = hour(m.groupValues[3])
                if (minutes == null || hour == null) null
                else if (m.groupValues[2] == "past") Spoken(hour, minutes) else Spoken(hour, 60 - minutes, -1)
            },
            // "at three thirty" arrives as "at 3 30".
            Idiom(Regex("\\b(?:at|around) (\\d{1,2}) (\\d{2})\\b")) { m ->
                val hour = hour(m.groupValues[1])
                val minutes = minute(m.groupValues[2])
                if (hour == null || minutes == null) null else Spoken(hour, minutes)
            },
            Idiom(Regex("\\b(?:at |around |@ ?)?(\\d{1,2})[:.](\\d{2})\\s*$MERIDIEM?")) { m ->
                val hour = hour(m.groupValues[1])
                val minutes = minute(m.groupValues[2])
                if (hour == null || minutes == null) null else Spoken(hour, minutes, meridiem = m.groupValues[3])
            },
            Idiom(Regex("\\b(?:at|around) (\\d{1,2})\\s*$MERIDIEM?(?:\\s*(?:o'clock|oclock))?\\b")) { m ->
                hour(m.groupValues[1])?.let { Spoken(it, 0, meridiem = m.groupValues[2]) }
            },
            Idiom(Regex("\\b(\\d{1,2})\\s*$MERIDIEM\\b")) { m ->
                m.groupValues[1].toIntOrNull()?.takeIf { it in 1..12 }?.let { Spoken(it, 0, meridiem = m.groupValues[2]) }
            },
        ),
        namedTimes = listOf(
            Regex("\\bat noon\\b|\\bmidday\\b|\\bat midday\\b") to (12 to 0),
            Regex("\\bat midnight\\b|\\bmidnight\\b") to (0 to 0),
            Regex("\\bthis morning\\b|\\bin the morning\\b|\\btomorrow morning\\b") to (9 to 0),
            Regex("\\bthis afternoon\\b|\\bin the afternoon\\b|\\btomorrow afternoon\\b") to (14 to 0),
            Regex("\\bthis evening\\b|\\bin the evening\\b|\\btomorrow evening\\b|\\btonight\\b") to (19 to 0),
        ),
        dayPartTimes = listOf(
            Regex("\\bmorning\\b") to (9 to 0),
            Regex("\\bafternoon\\b") to (14 to 0),
            Regex("\\bevening\\b") to (19 to 0),
        ),
        amMarkers = Regex("\\bmorning\\b"),
        pmMarkers = Regex("\\bafternoon\\b|\\bevening\\b|\\btonight\\b|(?<![\\p{L}])night\\b"),
        relativeDays = listOf(
            Regex("\\bday after tomorrow\\b") to 2L,
            Regex("\\btomorrow\\b|\\btmrw\\b") to 1L,
            Regex("\\btoday\\b|\\btonight\\b|\\bthis evening\\b|\\bthis afternoon\\b|\\bthis morning\\b") to 0L,
        ),
        inDuration = Regex(
            "\\bin (\\d{1,3}|a couple of|a few|a|an) " +
                "(minute|minutes|hour|hours|day|days|week|weeks|month|months|year|years)\\b"
        ),
        units = mapOf(
            "minute" to PeriodUnit.MINUTE, "minutes" to PeriodUnit.MINUTE,
            "hour" to PeriodUnit.HOUR, "hours" to PeriodUnit.HOUR,
            "day" to PeriodUnit.DAY, "days" to PeriodUnit.DAY,
            "week" to PeriodUnit.WEEK, "weeks" to PeriodUnit.WEEK,
            "month" to PeriodUnit.MONTH, "months" to PeriodUnit.MONTH,
            "year" to PeriodUnit.YEAR, "years" to PeriodUnit.YEAR,
        ),
        fuzzyQuantities = mapOf("a" to 1L, "an" to 1L, "a couple of" to 2L, "a few" to 3L),
        weekdayPhrase = Regex("\\b(next|this|coming|on|by|due|before) ([a-zà-ÿ]{3,12})\\b"),
        nextWeekWeekday = Regex("\\bnext week (?:on )?([a-zà-ÿ]{3,12})\\b"),
        nextWords = setOf("next", "following"),
        nextPeriod = Regex("\\b(next|following) (week|month|year)\\b"),
        periodUnits = mapOf("week" to PeriodUnit.WEEK, "month" to PeriodUnit.MONTH, "year" to PeriodUnit.YEAR),
        endOf = Regex("\\b(?:by |at )?(?:the )?end of (?:the |this )?(week|month|year)\\b"),
        ordinalSuffix = "(?:st|nd|rd|th)?",
        dayMonthJoiner = "(?:of )?",
        ordinalConnectors = setOf("of"),
    )

    // --------------------------------------------------------------- Dutch --

    private val NL_UNIT_WORDS = listOf("een", "twee", "drie", "vier", "vijf", "zes", "zeven", "acht", "negen")
    private val NL_TEENS = mapOf(
        "tien" to 10, "elf" to 11, "twaalf" to 12, "dertien" to 13, "veertien" to 14,
        "vijftien" to 15, "zestien" to 16, "zeventien" to 17, "achttien" to 18, "negentien" to 19,
    )
    private val NL_TENS = mapOf("twintig" to 20, "dertig" to 30, "veertig" to 40, "vijftig" to 50)

    /** "twee" + "ën" + "twintig", but also the unaccented spelling. */
    private val NL_CONNECTORS = listOf("en", "ën")

    private val NL_NUMBERS: Numbers = run {
        val cardinals = buildMap {
            // Bare "een" is the article far more often than the number, so only
            // the accented "één" counts. "een november" is handled in normalise().
            put("één", 1)
            NL_UNIT_WORDS.drop(1).forEachIndexed { i, w -> put(w, i + 2) }
            putAll(NL_TEENS)
            putAll(NL_TENS)
            NL_TENS.forEach { (tens, tensValue) ->
                NL_UNIT_WORDS.forEachIndexed { i, unit ->
                    NL_CONNECTORS.forEach { connector -> put(unit + connector + tens, tensValue + i + 1) }
                }
            }
        }

        val ordinals = buildMap {
            put("eerste", 1); put("tweede", 2); put("derde", 3); put("vierde", 4); put("vijfde", 5)
            put("zesde", 6); put("zevende", 7); put("achtste", 8); put("negende", 9)
            NL_TEENS.forEach { (word, value) -> put(word + "de", value) }
            put("twintigste", 20); put("dertigste", 30)
            listOf("twintig" to 20, "dertig" to 30).forEach { (tens, tensValue) ->
                NL_UNIT_WORDS.forEachIndexed { i, unit ->
                    if (tensValue + i + 1 <= 31) {
                        NL_CONNECTORS.forEach { connector -> put(unit + connector + tens + "ste", tensValue + i + 1) }
                    }
                }
            }
        }

        Numbers(
            cardinals = cardinals,
            ordinals = ordinals,
            compounds = listOf(
                Regex("(?<![\\p{L}])(een|één|twee|drie|vier|vijf|zes|zeven|acht|negen) en (twintig|dertig|veertig|vijftig)(?![\\p{L}])") to
                    { m: MatchResult -> (NL_TENS.getValue(m.groupValues[2]) + NL_UNIT_WORDS.indexOf(m.groupValues[1].replace("één", "een")) + 1).toString() },
            ),
        )
    }

    private val DUTCH = Phrases(
        numbers = NL_NUMBERS,
        idioms = listOf(
            // "tien voor acht", "tien over half drie", "tien voor half vier"
            Idiom(Regex("\\b(?:om|rond|tegen) (\\d{1,2}) (over|voor) (half )?(\\d{1,2})\\b")) { m ->
                val minutes = minute(m.groupValues[1])
                val hour = hour(m.groupValues[4])
                val half = m.groupValues[3].isNotBlank()
                when {
                    minutes == null || hour == null -> null
                    m.groupValues[2] == "over" -> Spoken(hour, minutes + (if (half) 30 else 0), if (half) -1 else 0)
                    half -> Spoken(hour, 30 - minutes, -1)
                    else -> Spoken(hour, 60 - minutes, -1)
                }
            },
            Idiom(Regex("\\b(?:(?:om|rond|tegen) )?kwart (over|voor) (\\d{1,2})\\b")) { m ->
                hour(m.groupValues[2])?.let { if (m.groupValues[1] == "over") Spoken(it, 15) else Spoken(it, 45, -1) }
            },
            // "half drie" is 2:30: half *towards* the three.
            Idiom(Regex("\\b(?:(?:om|rond|tegen) )?half (\\d{1,2})\\b")) { m ->
                hour(m.groupValues[1])?.let { Spoken(it, 30, -1) }
            },
            Idiom(Regex("\\b(?:(?:om|rond|tegen) )?(\\d{1,2}) uur (\\d{1,2})\\b")) { m ->
                val hour = hour(m.groupValues[1])
                val minutes = minute(m.groupValues[2])
                if (hour == null || minutes == null) null else Spoken(hour, minutes)
            },
            Idiom(Regex("\\b(?:(?:om|rond|tegen|@) ?)?(\\d{1,2})[:.](\\d{2})\\b")) { m ->
                val hour = hour(m.groupValues[1])
                val minutes = minute(m.groupValues[2])
                if (hour == null || minutes == null) null else Spoken(hour, minutes)
            },
            Idiom(Regex("\\bom (\\d{1,2})(?: uur)?\\b")) { m -> hour(m.groupValues[1])?.let { Spoken(it, 0) } },
            // "tegen 15 oktober" is a deadline, not 15:00 — these need the word "uur".
            Idiom(Regex("\\b(?:rond|tegen) (\\d{1,2}) uur\\b")) { m -> hour(m.groupValues[1])?.let { Spoken(it, 0) } },
        ),
        namedTimes = listOf(
            Regex("\\bmiddernacht\\b") to (0 to 0),
            Regex("\\btussen de middag\\b") to (12 to 0),
            Regex("\\bmorgenvroeg\\b|\\bmorgen vroeg\\b") to (7 to 0),
            Regex("\\bvanochtend\\b|\\bvanmorgen\\b|\\bmorgenochtend\\b|\\bs ochtends\\b|\\bs morgens\\b") to (9 to 0),
            Regex("\\bvanmiddag\\b|\\bmorgenmiddag\\b|\\bs middags\\b") to (14 to 0),
            Regex("\\bvanavond\\b|\\bmorgenavond\\b|\\bvannacht\\b|\\bs avonds\\b") to (19 to 0),
        ),
        dayPartTimes = listOf(
            Regex("\\bochtend\\b") to (9 to 0),
            Regex("\\bmiddag\\b") to (14 to 0),
            Regex("\\bavond\\b") to (19 to 0),
        ),
        amMarkers = Regex(
            "\\bs? ?ochtends?\\b|\\bs morgens\\b|\\bmorgens\\b|\\bvanochtend\\b|\\bvanmorgen\\b|" +
                "\\bmorgenochtend\\b|\\bmorgenvroeg\\b|\\bmorgen vroeg\\b"
        ),
        pmMarkers = Regex(
            "\\bs middags\\b|\\bs avonds\\b|\\bmiddag\\b|\\bnamiddag\\b|\\bavond\\b|\\bvanmiddag\\b|" +
                "\\bvanavond\\b|\\bmorgenmiddag\\b|\\bmorgenavond\\b"
        ),
        relativeDays = listOf(
            // "overmorgen" and "vanmorgen" are single words, so \b keeps the
            // bare "morgen" alternative from stealing either of them.
            Regex("\\bovermorgen\\b") to 2L,
            Regex("\\bmorgenvroeg\\b|\\bmorgen vroeg\\b|\\bmorgenochtend\\b|\\bmorgenmiddag\\b|\\bmorgenavond\\b|\\bmorgen\\b") to 1L,
            Regex("\\bvandaag\\b|\\bvanavond\\b|\\bvanmiddag\\b|\\bvanochtend\\b|\\bvanmorgen\\b|\\bvannacht\\b") to 0L,
        ),
        inDuration = Regex(
            "\\bover (\\d{1,3}|een paar|enkele|een) " +
                "(minuut|minuten|uur|uren|dag|dagen|week|weken|maand|maanden|jaar|jaren)\\b"
        ),
        units = mapOf(
            "minuut" to PeriodUnit.MINUTE, "minuten" to PeriodUnit.MINUTE,
            "uur" to PeriodUnit.HOUR, "uren" to PeriodUnit.HOUR,
            "dag" to PeriodUnit.DAY, "dagen" to PeriodUnit.DAY,
            "week" to PeriodUnit.WEEK, "weken" to PeriodUnit.WEEK,
            "maand" to PeriodUnit.MONTH, "maanden" to PeriodUnit.MONTH,
            "jaar" to PeriodUnit.YEAR, "jaren" to PeriodUnit.YEAR,
        ),
        fuzzyQuantities = mapOf("een" to 1L, "een paar" to 2L, "enkele" to 3L),
        weekdayPhrase = Regex("\\b(volgende|aanstaande|komende|deze|op|voor|vóór|uiterlijk|tegen) ([a-zà-ÿ]{3,12})\\b"),
        nextWeekWeekday = Regex("\\b(?:volgende|komende) week (?:op )?([a-zà-ÿ]{3,12})\\b"),
        nextWords = setOf("volgende"),
        nextPeriod = Regex("\\b(volgende|komende) (week|maand|jaar)\\b"),
        periodUnits = mapOf("week" to PeriodUnit.WEEK, "maand" to PeriodUnit.MONTH, "jaar" to PeriodUnit.YEAR),
        endOf = Regex("\\b(?:aan |tegen )?(?:het |de )?(?:eind|einde) van (?:de |het |deze |dit )?(week|maand|jaar)\\b"),
        // Dutch ordinals: 15e, 1ste, 2de.
        ordinalSuffix = "(?:e|de|ste|den)?",
        dayMonthJoiner = "(?:van )?",
        ordinalConnectors = setOf("van"),
    )

    private fun phrasesFor(language: NoteLanguage): Phrases = when (language) {
        NoteLanguage.ENGLISH -> ENGLISH
        NoteLanguage.DUTCH -> DUTCH
    }

    // ------------------------------------------------------- normalisation --

    private fun normalise(text: String, phrases: Phrases, language: NoteLanguage): Mapped {
        val numbers = phrases.numbers
        val monthNames = months(language).keys
        var mapped = Mapped.of(text)

        // 1. Multi-word numbers, before their parts are rewritten on their own.
        for ((regex, combine) in numbers.compounds) {
            mapped = mapped.replace(regex) { match, _ -> combine(match) }
        }

        // 2. Ordinals. Only next to a month name: "the third of march" is a date,
        //    but "the second thing I need" is not.
        mapped = mapped.replace(numbers.ordinalRegex) { match, whole ->
            val value = numbers.ordinals[match.value] ?: return@replace null
            if (isBesideMonth(whole, match.range, monthNames, phrases.ordinalConnectors)) value.toString() else null
        }

        // 3. Cardinals.
        mapped = mapped.replace(numbers.cardinalRegex) { match, _ -> numbers.cardinals[match.value]?.toString() }

        // 4. Dutch "een" is an article unless it is a day number: "een november".
        if (language == NoteLanguage.DUTCH) {
            mapped = mapped.replace(Regex("(?<![\\p{L}])een(?![\\p{L}])")) { match, whole ->
                if (isBesideMonth(whole, match.range, monthNames, phrases.ordinalConnectors, onlyAfter = true)) "1" else null
            }
        }
        return mapped
    }

    private fun isBesideMonth(
        whole: String,
        range: IntRange,
        monthNames: Set<String>,
        connectors: Set<String>,
        onlyAfter: Boolean = false,
    ): Boolean {
        val after = whole.substring(range.last + 1).trimStart().split(' ').filter { it.isNotEmpty() }
        val nextWord = after.getOrNull(0)?.trim('.', ',')
        val wordAfterConnector = if (nextWord in connectors) after.getOrNull(1)?.trim('.', ',') else null
        if (nextWord in monthNames || wordAfterConnector in monthNames) return true
        if (onlyAfter) return false

        val before = whole.substring(0, range.first).trimEnd().split(' ').filter { it.isNotEmpty() }
        return before.lastOrNull()?.trim('.', ',') in monthNames
    }

    // ------------------------------------------------------------ day parts --

    private class DayPartMatch(val part: DayPart, val range: IntRange)

    /** "'s middags", "morgenvroeg" — the words that say whether a bare "five" is 5 or 17. */
    private fun findDayPart(text: String, phrases: Phrases): DayPartMatch? {
        val pm = phrases.pmMarkers.find(text)
        val am = phrases.amMarkers.find(text)
        return when {
            pm == null && am == null -> null
            am == null -> DayPartMatch(DayPart.PM, pm!!.range)
            pm == null -> DayPartMatch(DayPart.AM, am.range)
            else -> if (pm.range.first <= am.range.first) DayPartMatch(DayPart.PM, pm.range) else DayPartMatch(DayPart.AM, am.range)
        }
    }

    // ---------------------------------------------------------------- time --

    private class TimeMatch(val hour: Int, val minute: Int, val ranges: List<IntRange>)

    private fun findTime(text: String, phrases: Phrases, dayPart: DayPartMatch?, hasDate: Boolean): TimeMatch? {
        for (idiom in phrases.idioms) {
            for (match in idiom.regex.findAll(text)) {
                val spoken = idiom.build(match) ?: continue
                val resolved = resolveHour(spoken.hour, spoken.meridiem, dayPart?.part)
                val total = ((resolved + spoken.hourShift) * 60 + spoken.minute).mod(24 * 60)
                val ranges = buildList {
                    add(match.range)
                    // The day-part word is part of how the time was said, so it
                    // goes with it — "vrijdag *middag* om twee uur".
                    if (dayPart != null && spoken.meridiem.isBlank()) add(dayPart.range)
                }
                return TimeMatch(total / 60, total % 60, ranges)
            }
        }
        for ((regex, hourMinute) in phrases.namedTimes) {
            regex.find(text)?.let { return TimeMatch(hourMinute.first, hourMinute.second, listOf(it.range)) }
        }
        if (hasDate) {
            for ((regex, hourMinute) in phrases.dayPartTimes) {
                regex.find(text)?.let { return TimeMatch(hourMinute.first, hourMinute.second, listOf(it.range)) }
            }
        }
        return null
    }

    private fun resolveHour(hour: Int, meridiem: String, dayPart: DayPart?): Int {
        if (meridiem.isNotBlank()) {
            val normalised = meridiem.replace(".", "")
            return when {
                normalised == "pm" && hour < 12 -> hour + 12
                normalised == "am" && hour == 12 -> 0
                else -> hour
            }
        }
        if (hour == 0 || hour >= 13) return hour
        return when (dayPart) {
            DayPart.PM -> if (hour < 12) hour + 12 else hour
            DayPart.AM -> hour
            // Nothing said which half of the day: 1–7 is far more likely to be an
            // afternoon or evening than the small hours.
            null -> if (hour in 1..7) hour + 12 else hour
        }
    }

    // ---------------------------------------------------------------- date --

    private class DateMatch(
        val date: LocalDate,
        val range: IntRange,
        /** Set for sub-day durations ("in two hours"), which carry their own time. */
        val exact: LocalDateTime? = null,
        /** True for "15 oktober" / "15.09.2026", whose digits must not be re-read as a time. */
        val isCalendarDate: Boolean = false,
    )

    private val weekdayTables = ConcurrentHashMap<NoteLanguage, Pair<Map<String, DayOfWeek>, Map<String, DayOfWeek>>>()
    private val monthTables = ConcurrentHashMap<NoteLanguage, Map<String, Month>>()

    /**
     * Full names, and full names plus abbreviations. Abbreviations are only
     * trusted after a qualifier ("on fri"): scanned bare they turn "the sun is
     * out" into a Sunday deadline and "he sat down" into a Saturday one.
     * Two-letter forms ("zo", "ma", "do") are left out entirely — "zo" is "so".
     */
    private fun weekdays(language: NoteLanguage): Pair<Map<String, DayOfWeek>, Map<String, DayOfWeek>> =
        weekdayTables.getOrPut(language) {
            val full = HashMap<String, DayOfWeek>()
            val all = HashMap<String, DayOfWeek>()
            listOf(Locale.ENGLISH, language.locale).forEach { locale ->
                DayOfWeek.values().forEach { day ->
                    val fullName = day.getDisplayName(TextStyle.FULL, locale).lowercase(locale)
                    val shortName = day.getDisplayName(TextStyle.SHORT, locale).lowercase(locale).trimEnd('.')
                    full[fullName] = day
                    all[fullName] = day
                    if (shortName.length >= 3) all[shortName] = day
                }
            }
            full to all
        }

    /** English is always included so a mixed-language note still resolves. */
    private fun months(language: NoteLanguage): Map<String, Month> =
        monthTables.getOrPut(language) {
            buildMap {
                listOf(Locale.ENGLISH, language.locale).forEach { locale ->
                    Month.values().forEach { month ->
                        put(month.getDisplayName(TextStyle.FULL, locale).lowercase(locale), month)
                        put(month.getDisplayName(TextStyle.SHORT, locale).lowercase(locale).trimEnd('.'), month)
                    }
                }
            }
        }

    private val NUMERIC_DATE = Regex("\\b(\\d{1,2})[/\\-.](\\d{1,2})(?:[/\\-.](\\d{2,4}))?\\b")
    private val BARE_WORD = Regex("\\b([a-zà-ÿ]{3,12})\\b")

    private fun findDate(
        text: String,
        now: LocalDateTime,
        phrases: Phrases,
        language: NoteLanguage,
    ): DateMatch? {
        val today = now.toLocalDate()
        val monthNames = months(language)
        val (fullWeekdays, anyWeekday) = weekdays(language)

        val monthFirst = Regex("\\b([a-zà-ÿ]{3,12})\\.? (\\d{1,2})${phrases.ordinalSuffix}(?:,? (\\d{4}))?\\b")
        val dayFirst = Regex(
            "\\b(?:the |de )?(\\d{1,2})${phrases.ordinalSuffix} ${phrases.dayMonthJoiner}" +
                "([a-zà-ÿ]{3,12})\\.?(?:,? (\\d{4}))?\\b"
        )

        // Most specific first: an explicit calendar date beats a relative phrase.
        // Every candidate match is tried, not just the first — "call 5 people on
        // 15/04" must not give up because "call 5" matched the pattern shape.
        NUMERIC_DATE.findAll(text).firstNotNullOfOrNull { m ->
            resolveNumericDate(m.groupValues, today, language)?.let { DateMatch(it, m.range, isCalendarDate = true) }
        }?.let { return it }

        monthFirst.findAll(text).firstNotNullOfOrNull { m ->
            val month = monthNames[m.groupValues[1].trimEnd('.')] ?: return@firstNotNullOfOrNull null
            val day = m.groupValues[2].toIntOrNull() ?: return@firstNotNullOfOrNull null
            resolveMonthDay(month, day, m.groupValues[3], today)?.let { DateMatch(it, m.range, isCalendarDate = true) }
        }?.let { return it }

        dayFirst.findAll(text).firstNotNullOfOrNull { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@firstNotNullOfOrNull null
            val month = monthNames[m.groupValues[2].trimEnd('.')] ?: return@firstNotNullOfOrNull null
            resolveMonthDay(month, day, m.groupValues[3], today)?.let { DateMatch(it, m.range, isCalendarDate = true) }
        }?.let { return it }

        for ((regex, days) in phrases.relativeDays) {
            regex.find(text)?.let { return DateMatch(today.plusDays(days), it.range) }
        }

        phrases.inDuration.find(text)?.let { m ->
            val amount = m.groupValues[1].toLongOrNull() ?: phrases.fuzzyQuantities[m.groupValues[1]]
            val unit = phrases.units[m.groupValues[2]]
            if (amount != null && unit != null) {
                // "in two hours" names a moment, not a day, so it carries its
                // own time rather than falling back to the default hour.
                val exact = when (unit) {
                    PeriodUnit.MINUTE -> now.plusMinutes(amount)
                    PeriodUnit.HOUR -> now.plusHours(amount)
                    else -> null
                }
                val date = exact?.toLocalDate() ?: when (unit) {
                    PeriodUnit.DAY -> today.plusDays(amount)
                    PeriodUnit.WEEK -> today.plusWeeks(amount)
                    PeriodUnit.MONTH -> today.plusMonths(amount)
                    else -> today.plusYears(amount)
                }
                return DateMatch(date, m.range, exact)
            }
        }

        // "volgende week vrijdag": Friday of next week — not the Monday that
        // "volgende week" alone resolves to.
        phrases.nextWeekWeekday.findAll(text).firstNotNullOfOrNull { m ->
            val day = anyWeekday[m.groupValues[1]] ?: return@firstNotNullOfOrNull null
            val monday = today.plusWeeks(1).with(DayOfWeek.MONDAY)
            DateMatch(monday.plusDays((day.value - 1).toLong()), m.range)
        }?.let { return it }

        phrases.weekdayPhrase.findAll(text).firstNotNullOfOrNull { m ->
            val day = anyWeekday[m.groupValues[2]] ?: return@firstNotNullOfOrNull null
            DateMatch(nextWeekday(today, day, m.groupValues[1] in phrases.nextWords), m.range)
        }?.let { return it }

        phrases.nextPeriod.find(text)?.let { m ->
            val date = when (phrases.periodUnits[m.groupValues[2]]) {
                PeriodUnit.WEEK -> today.plusWeeks(1).with(DayOfWeek.MONDAY)
                PeriodUnit.MONTH -> today.plusMonths(1).withDayOfMonth(1)
                else -> today.plusYears(1).withDayOfYear(1)
            }
            return DateMatch(date, m.range)
        }

        phrases.endOf.find(text)?.let { m ->
            val date = when (phrases.periodUnits[m.groupValues[1]]) {
                PeriodUnit.WEEK -> today.with(DayOfWeek.FRIDAY).let { if (it.isBefore(today)) it.plusWeeks(1) else it }
                PeriodUnit.MONTH -> today.withDayOfMonth(today.lengthOfMonth())
                else -> today.withDayOfYear(today.lengthOfYear())
            }
            return DateMatch(date, m.range)
        }

        // Last resort: a weekday name standing on its own ("call the dentist friday").
        BARE_WORD.findAll(text).firstNotNullOfOrNull { m ->
            fullWeekdays[m.groupValues[1]]?.let { DateMatch(nextWeekday(today, it, false), m.range) }
        }?.let { return it }

        return null
    }

    /**
     * "Next Friday" said on a Monday means the Friday of the *following* week,
     * not four days out — otherwise "this Friday" and "next Friday" would name
     * the same day. Plain "on Friday" always means the soonest future Friday.
     */
    private fun nextWeekday(today: LocalDate, target: DayOfWeek, forceNextWeek: Boolean): LocalDate {
        var daysAhead = (target.value - today.dayOfWeek.value + 7) % 7
        if (daysAhead == 0) daysAhead = 7
        if (forceNextWeek) {
            val toMonday = (DayOfWeek.MONDAY.value - today.dayOfWeek.value + 7) % 7
            val daysUntilNextMonday = if (toMonday == 0) 7 else toMonday
            if (daysAhead < daysUntilNextMonday) daysAhead += 7
        }
        return today.plusDays(daysAhead.toLong())
    }

    private fun resolveMonthDay(month: Month, day: Int, yearGroup: String, today: LocalDate): LocalDate? {
        val year = yearGroup.toIntOrNull()
        return runCatching {
            if (year != null) {
                LocalDate.of(year, month, day)
            } else {
                // No year spoken: pick the next occurrence, so "March 3" said in
                // December means next March rather than nine months ago.
                val thisYear = LocalDate.of(today.year, month, day)
                if (thisYear.isBefore(today)) thisYear.plusYears(1) else thisYear
            }
        }.getOrNull()
    }

    private fun resolveNumericDate(
        groups: List<String>,
        today: LocalDate,
        language: NoteLanguage,
    ): LocalDate? {
        val first = groups[1].toInt()
        val second = groups[2].toInt()
        val yearRaw = groups[3].toIntOrNull()

        val (day, month) = when {
            first > 12 && second <= 12 -> first to second
            second > 12 && first <= 12 -> second to first
            dayBeforeMonth(language) -> first to second
            else -> second to first
        }
        if (month !in 1..12 || day !in 1..31) return null

        val year = when {
            yearRaw == null -> today.year
            yearRaw < 100 -> 2000 + yearRaw
            else -> yearRaw
        }
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        return if (yearRaw == null && date.isBefore(today)) date.plusYears(1) else date
    }

    /**
     * True for languages that write 15/04 rather than 04/15. Keyed on the note's
     * language, not the phone's, so a Dutch note reads Dutch-style even on an
     * English device.
     */
    private fun dayBeforeMonth(language: NoteLanguage): Boolean {
        val pattern = runCatching {
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.SHORT, null, java.time.chrono.IsoChronology.INSTANCE, language.locale
            )
        }.getOrNull() ?: return true
        val dayIndex = pattern.indexOf('d')
        val monthIndex = pattern.indexOf('M')
        return dayIndex >= 0 && (monthIndex < 0 || dayIndex < monthIndex)
    }
}
