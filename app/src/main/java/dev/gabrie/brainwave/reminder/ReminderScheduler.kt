package dev.gabrie.brainwave.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import android.util.Log
import dev.gabrie.brainwave.R
import dev.gabrie.brainwave.data.Brainwave
import dev.gabrie.brainwave.settings.AppSettings

/**
 * Local notifications at (or shortly before) a brainwave's due moment.
 *
 * The calendar entry covers the user's calendar; this covers the phone in
 * their pocket, which is the thing they actually look at.
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun schedule(brainwave: Brainwave, settings: AppSettings) {
        cancel(brainwave.id)
        if (!settings.remindersEnabled || brainwave.completed) return

        val dueAt = brainwave.dueAt ?: return
        val triggerAt = dueAt - settings.reminderLeadMinutes * 60_000L
        // A reminder for a moment that already passed would fire instantly and
        // be pure noise, so past-due brainwaves simply get no alarm.
        if (triggerAt <= System.currentTimeMillis()) return

        val pending = createPendingIntent(brainwave.id, brainwave.title)

        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                // Without the exact-alarm permission the reminder still arrives,
                // just batched by the system within its maintenance window.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(brainwaveId: Long) {
        existingPendingIntent(brainwaveId)?.let { existing ->
            alarmManager.cancel(existing)
            existing.cancel()
        }
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Creating always yields an instance; only FLAG_NO_CREATE lookups can be null. */
    private fun createPendingIntent(id: Long, title: String?): PendingIntent =
        PendingIntent.getBroadcast(context, id.toInt(), intentFor(id, title), FLAGS_CREATE)

    private fun existingPendingIntent(id: Long): PendingIntent? =
        PendingIntent.getBroadcast(context, id.toInt(), intentFor(id, null), FLAGS_LOOKUP)

    private fun intentFor(id: Long, title: String?): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            // The data URI keeps each brainwave's PendingIntent distinct; extras
            // alone are not part of intent equality.
            data = "brainwave://reminder/$id".toUri()
            putExtra(EXTRA_BRAINWAVE_ID, id)
            title?.let { putExtra(EXTRA_TITLE, it) }
        }

    companion object {
        const val CHANNEL_ID = "brainwave_reminders"
        const val ACTION_REMIND = "dev.gabrie.brainwave.REMIND"
        const val EXTRA_BRAINWAVE_ID = "brainwave_id"
        const val EXTRA_TITLE = "brainwave_title"

        private const val TAG = "ReminderScheduler"
        private const val FLAGS_CREATE = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        private const val FLAGS_LOOKUP = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    }
}
