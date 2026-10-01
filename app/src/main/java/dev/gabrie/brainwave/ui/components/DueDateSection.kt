package dev.gabrie.brainwave.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.gabrie.brainwave.util.Time
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Editing surface for a due date: quick chips for the common answers, and full
 * date/time pickers behind them.
 *
 * [hasTime] is part of the value rather than derived, because "Friday" and
 * "Friday at 09:00" mean different things to the calendar entry and to how
 * overdue is calculated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDateSection(
    dueAt: LocalDateTime?,
    hasTime: Boolean,
    defaultHour: Int,
    onChange: (LocalDateTime?, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Due",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (dueAt != null) {
                InputChip(
                    selected = true,
                    onClick = { showDatePicker = true },
                    label = { Text(Time.formatDue(Time.toMillis(dueAt), hasTime)) },
                    leadingIcon = { Icon(Icons.Rounded.Event, contentDescription = null) },
                )
                AssistChip(
                    onClick = { showTimePicker = true },
                    label = { Text(if (hasTime) "Change time" else "Add time") },
                    leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                )
                AssistChip(onClick = { onChange(null, false) }, label = { Text("Clear") })
            } else {
                AssistChip(
                    onClick = { onChange(LocalDate.now().atTime(defaultHour, 0), false) },
                    label = { Text("Today") },
                )
                AssistChip(
                    onClick = { onChange(LocalDate.now().plusDays(1).atTime(defaultHour, 0), false) },
                    label = { Text("Tomorrow") },
                )
                AssistChip(
                    onClick = { onChange(LocalDate.now().plusWeeks(1).atTime(defaultHour, 0), false) },
                    label = { Text("Next week") },
                )
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text("Pick a date") },
                    leadingIcon = { Icon(Icons.Rounded.Event, contentDescription = null) },
                )
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = (dueAt?.toLocalDate() ?: LocalDate.now())
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            // The picker reports UTC midnight; read the calendar
                            // date back in UTC so it is not shifted by a day.
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            val time = if (hasTime && dueAt != null) dueAt.toLocalTime() else null
                            onChange(
                                time?.let { date.atTime(it) } ?: date.atTime(defaultHour, 0),
                                hasTime,
                            )
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val base = dueAt ?: LocalDate.now().atTime(defaultHour, 0)
        val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
        val timePickerState = rememberTimePickerState(
            initialHour = base.hour,
            initialMinute = base.minute,
            is24Hour = is24Hour,
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(
                            base.toLocalDate().atTime(timePickerState.hour, timePickerState.minute),
                            true,
                        )
                        showTimePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            title = { Text("Time") },
            text = { TimePicker(state = timePickerState) },
        )
    }
}
