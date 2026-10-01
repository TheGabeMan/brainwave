package dev.gabrie.brainwave.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import android.util.Log
import androidx.core.content.ContextCompat
import dev.gabrie.brainwave.data.Brainwave
import dev.gabrie.brainwave.settings.AppSettings
import dev.gabrie.brainwave.util.Time
import java.time.ZoneOffset
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes a brainwave's due date straight into the phone's calendar.
 *
 * This replaced an emailed `.ics` invite. That design handed the event to the
 * mail provider and hoped it would turn a self-sent message into a calendar
 * entry; in practice the SMTP server accepted every invite and none of them
 * ever appeared in the calendar, and nothing in the app could see why. Writing
 * through [CalendarContract] has no such middleman, and it is a platform API —
 * no Google libraries — so it stays F-Droid-clean.
 *
 * Entries land in the calendar the user chose, so they sync to Google
 * Calendar, Nextcloud/DAVx⁵ or wherever that account lives.
 */
class CalendarWriter(private val context: Context) {

    /** The calendar entries are written to, for showing in Settings. */
    data class Target(val id: Long, val name: String, val account: String)

    fun hasPermission(): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Every calendar the user can add events to. */
    suspend fun writableCalendars(): List<Target> = withContext(Dispatchers.IO) {
        if (!hasPermission()) emptyList() else runCatching { queryWritable() }.getOrDefault(emptyList())
    }

    /**
     * The calendar entries go to now, or null if the user still has to choose.
     * Exposed for Settings, which has to say where things are going.
     */
    suspend fun target(chosenId: Long): Target? = withContext(Dispatchers.IO) {
        if (!hasPermission()) null else runCatching { resolveTarget(chosenId) }.getOrNull()
    }

    /**
     * Creates the entry, or updates it if [Brainwave.calendarEventId] still
     * points at one.
     *
     * @return the event id, or null if nothing could be written — no permission,
     *   no writable calendar, or the provider refused. Never throws: a calendar
     *   problem must not stop a brainwave being saved.
     */
    suspend fun upsert(brainwave: Brainwave, settings: AppSettings): Long? = withContext(Dispatchers.IO) {
        try {
            if (!hasPermission()) return@withContext null
            val dueAt = brainwave.dueAt ?: return@withContext null
            val calendar = resolveTarget(settings.calendarId) ?: return@withContext null

            val values = eventValues(brainwave, dueAt, settings)
            val resolver = context.contentResolver

            val existing = brainwave.calendarEventId
            if (existing != null) {
                val uri = ContentUris.withAppendedId(Events.CONTENT_URI, existing)
                // Zero rows means the user deleted the entry from their calendar
                // app. Treat that as "create a new one", not as an error.
                if (resolver.update(uri, values, null, null) > 0) {
                    writeReminder(existing, brainwave, settings)
                    return@withContext existing
                }
            }

            values.put(Events.CALENDAR_ID, calendar.id)
            val created = resolver.insert(Events.CONTENT_URI, values) ?: return@withContext null
            val eventId = ContentUris.parseId(created)
            writeReminder(eventId, brainwave, settings)
            eventId
        } catch (e: Exception) {
            Log.w(TAG, "Could not write calendar entry", e)
            null
        }
    }

    suspend fun remove(eventId: Long) {
        withContext(Dispatchers.IO) {
            try {
                if (hasPermission()) {
                    context.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not remove calendar entry $eventId", e)
            }
        }
    }

    // ------------------------------------------------------------- internals --

    private fun eventValues(brainwave: Brainwave, dueAt: Long, settings: AppSettings): ContentValues =
        ContentValues().apply {
            put(Events.TITLE, brainwave.title)
            put(Events.DESCRIPTION, brainwave.body)
            // A reminder is a marker, not a meeting: do not make the user look busy.
            put(Events.AVAILABILITY, Events.AVAILABILITY_FREE)

            if (brainwave.dueHasTime) {
                put(Events.ALL_DAY, 0)
                put(Events.DTSTART, dueAt)
                put(Events.DTEND, dueAt + EVENT_LENGTH_MILLIS)
                put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            } else {
                // Only the day is known. All-day events are stored as UTC
                // midnight to UTC midnight regardless of the phone's timezone —
                // anything else makes them drift a day for people who travel.
                val day = Time.toLocal(dueAt).toLocalDate()
                val start = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                put(Events.ALL_DAY, 1)
                put(Events.DTSTART, start)
                put(Events.DTEND, start + DAY_MILLIS)
                put(Events.EVENT_TIMEZONE, "UTC")
            }
            // Reminders only make sense when there is a moment to be before.
            put(Events.HAS_ALARM, if (wantsAlarm(brainwave, settings)) 1 else 0)
        }

    private fun wantsAlarm(brainwave: Brainwave, settings: AppSettings) =
        brainwave.dueHasTime && settings.remindersEnabled

    private fun writeReminder(eventId: Long, brainwave: Brainwave, settings: AppSettings) {
        val resolver = context.contentResolver
        resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
        if (!wantsAlarm(brainwave, settings)) return

        resolver.insert(
            Reminders.CONTENT_URI,
            ContentValues().apply {
                put(Reminders.EVENT_ID, eventId)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
                put(Reminders.MINUTES, settings.reminderLeadMinutes.coerceAtLeast(0))
            },
        )
    }

    /**
     * The calendar to write to: the one the user chose if it still exists,
     * otherwise the phone's only writable calendar, otherwise nothing.
     *
     * Deliberately *not* "the primary calendar". On a phone with a work
     * calendar, a family calendar and a personal one, "primary" is whichever the
     * account happened to be set up with — often the work one — and putting a
     * shopping reminder there is worse than putting it nowhere.
     */
    private fun resolveTarget(chosenId: Long): Target? {
        val calendars = queryWritable()
        calendars.firstOrNull { it.id == chosenId }?.let { return it }
        return calendars.singleOrNull()
    }

    private fun queryWritable(): List<Target> {
        val projection = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME)
        val selection = "${Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${Calendars.VISIBLE} = 1"
        val args = arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        val found = mutableListOf<Target>()
        context.contentResolver.query(Calendars.CONTENT_URI, projection, selection, args, "${Calendars._ID} ASC")?.use { c ->
            while (c.moveToNext()) found += Target(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty())
        }
        return found
    }

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

        private const val TAG = "CalendarWriter"
        private const val EVENT_LENGTH_MILLIS = 30 * 60_000L
        private const val DAY_MILLIS = 24 * 60 * 60_000L
    }
}
