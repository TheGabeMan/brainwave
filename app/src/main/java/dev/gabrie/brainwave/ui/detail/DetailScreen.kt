package dev.gabrie.brainwave.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrie.brainwave.ui.components.DueDateSection
import dev.gabrie.brainwave.ui.components.rememberCalendarActions
import dev.gabrie.brainwave.util.Time

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    brainwaveId: Long,
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel(
        key = "brainwave-$brainwaveId",
        factory = DetailViewModel.factory(brainwaveId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    val calendar = rememberCalendarActions()

    LaunchedEffect(Unit) {
        viewModel.messageFlow.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(state.closed, state.missing) {
        if (state.closed || state.missing) onBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Brainwave") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleCompleted) {
                        Icon(
                            imageVector = if (state.completed) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.RadioButtonUnchecked
                            },
                            contentDescription = if (state.completed) "Mark as open" else "Mark complete",
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        calendar.Dialogs()
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.body,
                onValueChange = viewModel::setBody,
                label = { Text("Brainwave") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            DueDateSection(
                dueAt = state.dueAt,
                hasTime = state.hasTime,
                defaultHour = state.defaultDueHour,
                onChange = viewModel::setDue,
            )

            TextButton(onClick = viewModel::rereadDueDateFromText) {
                Text("Find a date in the text")
            }

            if (state.hasAudio) {
                OutlinedButton(onClick = viewModel::togglePlayback) {
                    Icon(
                        imageVector = if (state.playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                    )
                    Text(
                        text = if (state.playing) "Stop" else "Play recording",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        calendar.ensure(state.dueAt != null && state.calendarEnabled) { viewModel.save(resendEmail = false) }
                    },
                    enabled = state.dirty,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
                OutlinedButton(
                    onClick = {
                        calendar.ensure(state.dueAt != null && state.calendarEnabled) { viewModel.save(resendEmail = true) }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save & email")
                }
            }

            state.original?.let { original ->
                Text(
                    text = buildString {
                        append("Captured ")
                        append(Time.formatAbsolute(original.createdAt))
                        if (original.noteMailSent) append(" · emailed")
                        if (original.calendarEventId != null) append(" · in your calendar")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this brainwave?") },
            text = { Text("The note and its recording are removed from this phone. Emails already sent stay in your inbox.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
