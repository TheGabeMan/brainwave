package dev.gabrie.brainwave.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrie.brainwave.ui.components.DueDateSection
import dev.gabrie.brainwave.ui.components.rememberCalendarActions
import dev.gabrie.brainwave.ui.home.PulsingMicIndicator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: RecordViewModel = viewModel(factory = RecordViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val calendar = rememberCalendarActions()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Only the microphone is required; a declined Bluetooth prompt just means
        // recording falls back to the phone's own mic.
        viewModel.onPermissionResult(results[Manifest.permission.RECORD_AUDIO] != false)
    }

    LaunchedEffect(Unit) {
        // Recording starts the instant the screen opens: the whole point of the
        // big button is to catch a thought before it evaporates.
        val missing = viewModel.requiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) viewModel.startRecording() else permissionLauncher.launch(missing.toTypedArray())
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.stage == RecordStage.REVIEW) "Review" else "Recording") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.discard()
                            onCancel()
                        }
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Discard")
                    }
                },
            )
        },
    ) { padding ->
        calendar.Dialogs()
        if (state.modelPrompt) {
            AlertDialog(
                onDismissRequest = viewModel::dismissModelPrompt,
                title = { Text("Download the speech model?") },
                text = {
                    Text(
                        "To turn your voice into text on this phone, Brainwave needs the " +
                            "${state.language.displayName} speech model: about ${state.modelSizeMegabytes} MB, " +
                            "downloaded once. Wi-Fi is recommended. Your recording is kept either way."
                    )
                },
                confirmButton = { TextButton(onClick = viewModel::downloadModel) { Text("Download") } },
                dismissButton = { TextButton(onClick = viewModel::dismissModelPrompt) { Text("Not now") } },
            )
        }
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state.stage) {
                RecordStage.PERMISSION -> Centered { Text("Waiting for microphone permission…") }

                RecordStage.RECORDING -> RecordingBody(
                    amplitude = state.amplitude,
                    elapsedMillis = state.elapsedMillis,
                    headsetConnected = state.headsetConnected,
                    modelProgress = state.modelProgress,
                    onStop = viewModel::stopRecording,
                    onType = viewModel::switchToTyping,
                )

                RecordStage.PROCESSING -> Centered {
                    val progress = state.modelProgress
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        CircularProgressIndicator()
                    }
                    Text(
                        text = state.statusText.ifBlank { "Working…" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                RecordStage.ASKING_DUE -> Centered {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(SpokenPrompts.dueQuestion(state.language), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = viewModel::skipDueQuestion) { Text("Skip") }
                }

                RecordStage.LISTENING_DUE -> Centered {
                    PulsingMicIndicator(amplitude = state.amplitude)
                    Text("Listening for a date…", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = viewModel::stopListeningForDueDate) { Text("Done") }
                    TextButton(onClick = viewModel::skipDueQuestion) { Text("Skip") }
                }

                RecordStage.REVIEW -> ReviewBody(
                    state = state,
                    defaultDueHour = state.defaultDueHour,
                    onTitleChange = viewModel::setTitle,
                    onBodyChange = viewModel::setBody,
                    onDueChange = viewModel::setDue,
                    onSave = {
                        calendar.ensure(state.dueAt != null && state.calendarEnabled) { viewModel.save() }
                    },
                    onDismissError = viewModel::dismissError,
                )
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        content = content,
    )
}

@Composable
private fun RecordingBody(
    amplitude: Float,
    elapsedMillis: Long,
    headsetConnected: Boolean,
    modelProgress: Float?,
    onStop: () -> Unit,
    onType: () -> Unit,
) {
    Centered {
        PulsingMicIndicator(amplitude = amplitude)

        Text(
            text = formatElapsed(elapsedMillis),
            style = MaterialTheme.typography.displaySmall,
        )

        if (modelProgress != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(progress = { modelProgress }, modifier = Modifier.fillMaxWidth(0.6f))
                Text(
                    text = "Downloading the speech model… ${(modelProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (headsetConnected) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("Using your headset", style = MaterialTheme.typography.labelLarge)
            }
        }

        Button(onClick = onStop) {
            Icon(Icons.Rounded.Stop, contentDescription = null)
            Text("Stop", modifier = Modifier.padding(start = 8.dp))
        }

        TextButton(onClick = onType) {
            Icon(Icons.Rounded.Keyboard, contentDescription = null)
            Text("Type instead", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ReviewBody(
    state: RecordUiState,
    defaultDueHour: Int,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onDueChange: (java.time.LocalDateTime?, Boolean) -> Unit,
    onSave: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(onClick = onDismissError, modifier = Modifier.align(Alignment.End)) {
                        Text("Dismiss")
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.body,
            onValueChange = onBodyChange,
            label = { Text("Brainwave") },
            minLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )

        DueDateSection(
            dueAt = state.dueAt,
            hasTime = state.hasTime,
            defaultHour = defaultDueHour,
            onChange = onDueChange,
        )

        if (state.audioPath != null) {
            Text(
                text = "The recording will be attached to the email.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FilledTonalButton(
            onClick = onSave,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.saving) "Saving…" else "Save brainwave")
        }

        Text(
            text = "Saving emails the recording and adds the due date to your calendar.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formatElapsed(millis: Long): String {
    val totalSeconds = millis / 1000
    return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
