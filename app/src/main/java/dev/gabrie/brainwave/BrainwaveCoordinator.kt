package dev.gabrie.brainwave

import dev.gabrie.brainwave.calendar.CalendarWriter
import dev.gabrie.brainwave.data.Brainwave
import dev.gabrie.brainwave.data.BrainwaveRepository
import dev.gabrie.brainwave.reminder.ReminderScheduler
import dev.gabrie.brainwave.settings.AppSettings
import dev.gabrie.brainwave.settings.SettingsRepository
import dev.gabrie.brainwave.work.MailWorkScheduler
import java.time.LocalDate
import java.time.ZoneId

/**
 * The single place that knows what has to happen around a brainwave changing:
 * the reminder rescheduled, the calendar entry created / moved / removed, mail
 * queued.
 *
 * Both the recording flow and the edit screen route through here, so the two
 * can never drift apart on, say, whether editing a due date moves the calendar
 * entry.
 *
 * **Every write here is a targeted update, and every decision reads the row
 * fresh from the database.** Callers hold a copy of a brainwave from when their
 * screen opened; writing that copy back whole would overwrite bookkeeping that
 * changed since — most importantly [Brainwave.calendarEventId], whose loss means
 * the next edit creates a duplicate calendar entry instead of updating one.
 */
class BrainwaveCoordinator(
    private val repository: BrainwaveRepository,
    private val settingsRepository: SettingsRepository,
    private val reminders: ReminderScheduler,
    private val mail: MailWorkScheduler,
    private val calendar: CalendarWriter,
) {

    suspend fun create(brainwave: Brainwave): Long {
        val id = repository.insert(brainwave)
        val settings = settingsRepository.current()
        val saved = repository.find(id) ?: return id

        reminders.schedule(saved, settings)
        syncCalendar(saved, settings)
        if (settings.mailConfigured) mail.sendNote(id)
        return id
    }

    /** Persists an edit and brings the reminder and calendar entry in line with it. */
    suspend fun update(
        id: Long,
        title: String,
        body: String,
        dueAt: Long?,
        dueHasTime: Boolean,
        resendNote: Boolean = false,
    ) {
        repository.updateContent(id, title, body, dueAt, dueHasTime)
        val current = repository.find(id) ?: return
        val settings = settingsRepository.current()

        reminders.schedule(current, settings)
        syncCalendar(current, settings)
        if (resendNote && settings.mailConfigured) mail.sendNote(id)
    }

    suspend fun setCompleted(id: Long, completed: Boolean) {
        repository.setCompleted(id, completed)
        val current = repository.find(id) ?: return
        val settings = settingsRepository.current()

        if (completed) reminders.cancel(id) else reminders.schedule(current, settings)
        // A finished task is no longer something to be reminded of, in the
        // calendar as much as on the phone; reopening it puts it back.
        syncCalendar(current, settings)
    }

    suspend fun delete(brainwave: Brainwave) {
        // The row, not the copy we were handed: the copy may predate the entry.
        val eventId = repository.find(brainwave.id)?.calendarEventId ?: brainwave.calendarEventId
        reminders.cancel(brainwave.id)
        eventId?.let { calendar.remove(it) }
        repository.delete(brainwave)
    }

    suspend fun restore(brainwave: Brainwave) {
        val id = repository.restore(brainwave)
        val settings = settingsRepository.current()
        val restored = repository.find(id) ?: return
        reminders.schedule(restored, settings)
        syncCalendar(restored, settings)
    }

    /**
     * Creates calendar entries for open brainwaves that are still to come and
     * have none. Safe to run as often as you like.
     *
     * It exists because an entry can be missing for reasons that have since
     * gone away: the brainwave predates calendar support, or permission was
     * granted after it was saved. Past-due brainwaves are left alone — filling
     * the calendar with deadlines that already went by is noise, not help.
     */
    suspend fun catchUpCalendar() {
        val settings = settingsRepository.current()
        if (!settings.calendarEnabled || !calendar.hasPermission()) return

        val startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        repository.allOnce()
            .filter { !it.completed && it.calendarEventId == null && (it.dueAt ?: 0L) >= startOfToday }
            .forEach { syncCalendar(it, settings) }
    }

    /**
     * Called when the user picks a (different) calendar: removes every entry
     * this app wrote and writes the open, upcoming ones again into the new one.
     *
     * Without this, switching calendars strands the old entries where they
     * were, and their stored ids make [catchUpCalendar] believe they still
     * exist — so the new calendar would never receive them.
     */
    suspend fun rehomeCalendarEntries() {
        repository.allOnce().filter { it.calendarEventId != null }.forEach { brainwave ->
            calendar.remove(brainwave.calendarEventId!!)
            repository.setCalendarEventId(brainwave.id, null)
        }
        catchUpCalendar()
    }

    fun sendWholeList() = mail.sendList()

    /**
     * Makes the calendar match [brainwave]: an entry when it has a due date and
     * is still open, none otherwise.
     */
    private suspend fun syncCalendar(brainwave: Brainwave, settings: AppSettings) {
        val existing = brainwave.calendarEventId
        val wanted = settings.calendarEnabled && brainwave.dueAt != null && !brainwave.completed

        if (!wanted) {
            if (existing != null) {
                calendar.remove(existing)
                repository.setCalendarEventId(brainwave.id, null)
            }
            return
        }

        val eventId = calendar.upsert(brainwave, settings)
        // Null means "could not write right now" (no permission, say). Keep what
        // we had rather than forgetting an entry that may well still exist.
        if (eventId != null && eventId != existing) repository.setCalendarEventId(brainwave.id, eventId)
    }
}
