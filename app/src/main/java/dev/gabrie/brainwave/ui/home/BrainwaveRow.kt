package dev.gabrie.brainwave.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gabrie.brainwave.data.Brainwave
import dev.gabrie.brainwave.ui.theme.CompleteGreen
import dev.gabrie.brainwave.ui.theme.CompleteGreenContainer
import dev.gabrie.brainwave.util.Time

/**
 * One row of the list.
 *
 * Swipe left completes, swipe right deletes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainwaveRow(
    brainwave: Brainwave,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    // React to the settled value and reset, rather than vetoing the change from
    // confirmValueChange (deprecated). Resetting means the row snaps back and
    // the data change drives it off the list, so a row can never be left
    // stranded in a dismissed state — which is what would happen to a completed
    // brainwave while the "Done" filter is on.
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> {
                onComplete()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.StartToEnd -> {
                onDelete()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fillMaxWidth(),
        backgroundContent = { SwipeBackground(dismissState.dismissDirection, brainwave.completed) },
    ) {
        val overdue = !brainwave.completed && Time.isOverdue(brainwave.dueAt, brainwave.dueHasTime)

        ListItem(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ),
            headlineContent = {
                Text(
                    text = brainwave.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (brainwave.completed) TextDecoration.LineThrough else null,
                    color = if (brainwave.completed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            },
            supportingContent = {
                Text(
                    text = Time.formatDue(brainwave.dueAt, brainwave.dueHasTime),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        overdue -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            trailingContent = if (brainwave.completed) {
                {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Completed",
                        tint = CompleteGreen,
                    )
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, completed: Boolean) {
    val deleting = direction == SwipeToDismissBoxValue.StartToEnd

    val background = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.errorContainer
        SwipeToDismissBoxValue.EndToStart -> CompleteGreenContainer
        SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .padding(horizontal = 24.dp),
        contentAlignment = if (deleting) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Icon(
                imageVector = if (deleting) Icons.Rounded.DeleteOutline else Icons.Rounded.CheckCircle,
                contentDescription = when {
                    deleting -> "Delete"
                    completed -> "Reopen"
                    else -> "Complete"
                },
                tint = if (deleting) MaterialTheme.colorScheme.onErrorContainer else CompleteGreen,
            )
        }
    }
}
