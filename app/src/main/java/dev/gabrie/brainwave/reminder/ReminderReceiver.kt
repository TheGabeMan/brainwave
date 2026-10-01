package dev.gabrie.brainwave.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.gabrie.brainwave.MainActivity
import dev.gabrie.brainwave.R
import dev.gabrie.brainwave.brainwaveApp
import dev.gabrie.brainwave.util.Time
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_BRAINWAVE_ID, -1L)
        if (id <= 0L) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.brainwaveApp().container
                val brainwave = container.repository.find(id)
                // Completed or deleted between scheduling and firing: stay quiet.
                if (brainwave == null || brainwave.completed) return@launch

                notify(
                    context = context,
                    id = id,
                    title = brainwave.title,
                    body = Time.formatDue(brainwave.dueAt, brainwave.dueHasTime),
                )
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, id: Long, title: String, body: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val open = PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_BRAINWAVE_ID, id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(id.toInt(), notification)
    }
}
