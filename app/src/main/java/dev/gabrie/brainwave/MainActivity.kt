package dev.gabrie.brainwave

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dev.gabrie.brainwave.ui.nav.BrainwaveNavHost
import dev.gabrie.brainwave.ui.theme.BrainwaveTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        setContent {
            var pendingBrainwaveId by remember(intent) {
                mutableStateOf(intent?.getLongExtra(EXTRA_OPEN_BRAINWAVE_ID, -1L)?.takeIf { it > 0L })
            }

            BrainwaveTheme {
                BrainwaveNavHost(
                    openBrainwaveId = pendingBrainwaveId,
                    onOpenHandled = { pendingBrainwaveId = null },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Cheap and idempotent; also picks up calendar permission granted from
        // the system settings while the app was in the background.
        lifecycleScope.launch { brainwaveApp().container.coordinator.catchUpCalendar() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop: a reminder tapped while the app is already open arrives here.
        setIntent(intent)
    }

    /**
     * Reminders are the point of the app, so the permission is asked for on
     * first launch rather than at the moment the first alarm would fire.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_OPEN_BRAINWAVE_ID = "open_brainwave_id"
    }
}
