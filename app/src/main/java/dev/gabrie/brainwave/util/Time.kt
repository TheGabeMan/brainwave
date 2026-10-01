package dev.gabrie.brainwave.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Time {

    // Rebuilt if the user changes their device language; see LocaleAware.
    private val dateFormat = LocaleAware { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(it) }
    private val dateTimeFormat = LocaleAware {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(it)
    }
    private val timeFormat = LocaleAware { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(it) }
    private val weekdayFormat = LocaleAware { DateTimeFormatter.ofPattern("EEEE", it) }

    fun toLocal(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    fun toMillis(dateTime: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    fun formatAbsolute(millis: Long, withTime: Boolean = true): String {
        val local = toLocal(millis)
        return if (withTime) local.format(dateTimeFormat.get()) else local.format(dateFormat.get())
    }

    /**
     * The label under a brainwave title. Near dates read as words ("Tomorrow,
     * 14:00") because that is how people think about a to-do list; anything
     * beyond a week falls back to an absolute date.
     */
    fun formatDue(dueMillis: Long?, hasTime: Boolean, now: LocalDateTime = LocalDateTime.now()): String {
        if (dueMillis == null) return "No due date"
        val due = toLocal(dueMillis)
        val today = now.toLocalDate()
        val dueDate = due.toLocalDate()
        val time = if (hasTime) ", " + due.format(timeFormat.get()) else ""

        val days = Duration.between(today.atStartOfDay(), dueDate.atStartOfDay()).toDays()
        val day = when {
            days == 0L -> "Today"
            days == 1L -> "Tomorrow"
            days == -1L -> "Yesterday"
            days in 2..6 -> dueDate.format(weekdayFormat.get())
            days in -6..-2 -> "Last " + dueDate.format(weekdayFormat.get())
            else -> dueDate.format(dateFormat.get())
        }
        return day + time
    }

    fun isOverdue(dueMillis: Long?, hasTime: Boolean, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val due = dueMillis?.let { toLocal(it) } ?: return false
        // An all-day brainwave is only late once its day is over, not at 09:00.
        return if (hasTime) due.isBefore(now) else due.toLocalDate().isBefore(now.toLocalDate())
    }

    /**
     * A due date phrased for text-to-speech in [locale].
     *
     * Unlike [formatDue] this avoids the relative words ("Today", "Tomorrow"),
     * which are English-only UI strings — a spoken prompt in Dutch should not
     * splice English into the middle of a sentence.
     */
    fun formatForSpeech(dueMillis: Long, hasTime: Boolean, locale: java.util.Locale): String {
        val due = toLocal(dueMillis)
        val date = due.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
        if (!hasTime) return date
        val time = due.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        return "$date, $time"
    }

    fun endOfDay(date: LocalDate): LocalDateTime = date.atTime(23, 59)
}
