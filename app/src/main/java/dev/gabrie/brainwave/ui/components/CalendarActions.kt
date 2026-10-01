package dev.gabrie.brainwave.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.gabrie.brainwave.AppContainer
import dev.gabrie.brainwave.brainwaveApp
import dev.gabrie.brainwave.calendar.CalendarWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Everything the UI needs to get a brainwave into the calendar: the permission
 * prompt, and — when the phone has several calendars — the question of which.
 *
 * Both are asked at the moment they matter (saving a brainwave that has a due
 * date), not on first launch, where "allow access to your calendar" means
 * nothing. And neither can stop a save: whichever way the user answers, or if
 * they dismiss the dialog, the block passed to [ensure] still runs. The worst
 * case is a brainwave without a calendar entry, which the next app start
 * quietly catches up on once the user has chosen.
 *
 * Call [Dialogs] once in the screen so the chooser has somewhere to appear.
 */
class CalendarActions internal constructor(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val launchPermission: () -> Unit,
) {
    private var pendingPermission: (suspend () -> Unit)? = null
    private var afterChoice: (() -> Unit)? = null
    private var options by mutableStateOf<List<CalendarWriter.Target>?>(null)

    /** Runs [proceed] once permission and a target calendar are sorted out. */
    fun ensure(needed: Boolean, proceed: () -> Unit) {
        if (!needed) {
            proceed()
        } else if (!container.calendarWriter.hasPermission()) {
            pendingPermission = { chooseIfNeeded(proceed) }
            launchPermission()
        } else {
            scope.launch { chooseIfNeeded(proceed) }
        }
    }

    /** Opens the chooser regardless of what is already chosen — for Settings. */
    fun choose(onDone: () -> Unit = {}) {
        if (!container.calendarWriter.hasPermission()) {
            pendingPermission = { openChooser(onDone) }
            launchPermission()
        } else {
            scope.launch { openChooser(onDone) }
        }
    }

    internal fun onPermissionResult() {
        val next = pendingPermission ?: return
        pendingPermission = null
        scope.launch { next() }
    }

    private suspend fun chooseIfNeeded(proceed: () -> Unit) {
        val writer = container.calendarWriter
        if (!writer.hasPermission()) {
            proceed()
            return
        }

        val settings = container.settingsRepository.current()
        val calendars = writer.writableCalendars()
        val stillExists = calendars.any { it.id == settings.calendarId }

        when {
            stillExists || calendars.isEmpty() -> proceed()
            calendars.size == 1 -> {
                container.settingsRepository.update { it.copy(calendarId = calendars.single().id) }
                proceed()
            }
            else -> {
                afterChoice = proceed
                options = calendars
            }
        }
    }

    private suspend fun openChooser(onDone: () -> Unit) {
        val calendars = container.calendarWriter.writableCalendars()
        if (calendars.isEmpty()) {
            onDone()
        } else {
            afterChoice = onDone
            options = calendars
        }
    }

    private fun finish() {
        val next = afterChoice
        afterChoice = null
        next?.invoke()
    }

    private fun select(calendar: CalendarWriter.Target) {
        scope.launch {
            container.settingsRepository.update { it.copy(calendarId = calendar.id) }
            options = null
            container.coordinator.rehomeCalendarEntries()
            finish()
        }
    }

    @Composable
    fun Dialogs() {
        val calendars = options ?: return
        AlertDialog(
            onDismissRequest = {
                options = null
                finish()
            },
            title = { Text("Which calendar?") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Brainwaves with a due date are added to one calendar. " +
                            "You can change this later in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    calendars.forEach { calendar ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { select(calendar) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(calendar.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = calendar.account,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        options = null
                        finish()
                    }
                ) { Text("Not now") }
            },
        )
    }
}

@Composable
fun rememberCalendarActions(): CalendarActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val holder = remember { arrayOfNulls<CalendarActions>(1) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        holder[0]?.onPermissionResult()
    }

    return remember {
        CalendarActions(context.brainwaveApp().container, scope) {
            launcher.launch(CalendarWriter.PERMISSIONS)
        }.also { holder[0] = it }
    }
}
