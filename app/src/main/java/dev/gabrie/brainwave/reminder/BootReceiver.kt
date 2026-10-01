package dev.gabrie.brainwave.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.gabrie.brainwave.brainwaveApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Alarms do not survive a reboot or an app update, so every pending reminder is
 * re-registered when the device comes back.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.brainwaveApp().container
                val settings = container.settingsRepository.current()
                container.repository.pendingWithDueDate().forEach { brainwave ->
                    container.reminderScheduler.schedule(brainwave, settings)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
