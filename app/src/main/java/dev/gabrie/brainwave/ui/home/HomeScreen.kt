package dev.gabrie.brainwave.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.automirrored.rounded.ForwardToInbox
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrie.brainwave.data.SortField
import dev.gabrie.brainwave.mail.MailHandoff

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRecord: () -> Unit,
    onOpenBrainwave: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.handoffFlow.collect { request ->
            if (!MailHandoff.launch(context, request)) snackbarHostState.showSnackbar("No mail app found on this phone.")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messageFlow.collect { message ->
            val isUndoable = message.startsWith(HomeViewModel.DELETED_PREFIX)
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (isUndoable) "Undo" else null,
                withDismissAction = !isUndoable,
            )
            if (isUndoable && result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Brainwave") },
                actions = {
                    IconButton(onClick = viewModel::emailWholeList) {
                        Icon(Icons.AutoMirrored.Rounded.ForwardToInbox, contentDescription = "Email the whole list")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // The two weight(1f) halves are the 50/50 split: capture on top,
            // everything already captured underneath.
            RecordButton(
                onClick = onRecord,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                SortBar(
                    field = state.sortOrder.field,
                    ascending = state.sortOrder.ascending,
                    showCompleted = state.showCompleted,
                    onFieldChange = viewModel::setSortField,
                    onDirectionToggle = viewModel::toggleDirection,
                    onShowCompletedChange = viewModel::setShowCompleted,
                )

                if (state.loaded && state.brainwaves.isEmpty()) {
                    EmptyList(showCompleted = state.showCompleted)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.brainwaves, key = { it.id }) { brainwave ->
                            BrainwaveRow(
                                brainwave = brainwave,
                                onOpen = { onOpenBrainwave(brainwave.id) },
                                onComplete = { viewModel.complete(brainwave) },
                                onDelete = { viewModel.delete(brainwave) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortBar(
    field: SortField,
    ascending: Boolean,
    showCompleted: Boolean,
    onFieldChange: (SortField) -> Unit,
    onDirectionToggle: () -> Unit,
    onShowCompletedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            SegmentedButton(
                selected = field == SortField.DUE_DATE,
                onClick = { onFieldChange(SortField.DUE_DATE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text("Due date") },
            )
            SegmentedButton(
                selected = field == SortField.TITLE,
                onClick = { onFieldChange(SortField.TITLE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                label = { Text("Title") },
            )
        }

        IconButton(onClick = onDirectionToggle) {
            Icon(
                imageVector = if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                contentDescription = if (ascending) "Sorted ascending" else "Sorted descending",
            )
        }

        FilterChip(
            selected = showCompleted,
            onClick = { onShowCompletedChange(!showCompleted) },
            label = { Text("Done") },
        )
    }
}

@Composable
private fun EmptyList(showCompleted: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (showCompleted) {
                "Nothing here yet. Tap the big button to capture your first brainwave."
            } else {
                "No open brainwaves. Tap the big button to capture one."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
