package dev.gabrie.brainwave.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star

/**
 * The top half of the home screen: one very large target you can hit without
 * looking, which is the point of a capture app.
 *
 * The shape morphs from a soft cookie to a circle on press and turns very
 * slowly at rest — Expressive's shape-and-motion vocabulary rather than a
 * static circle.
 */
@Composable
fun RecordButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Record a brainwave",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val morphProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "recordMorph",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "recordScale",
    )

    val rotation by rememberInfiniteTransition(label = "recordSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
        label = "recordRotation",
    )

    val morph = remember {
        Morph(
            start = RoundedPolygon.star(
                numVerticesPerRadius = 8,
                innerRadius = 0.86f,
                rounding = CornerRounding(0.32f),
            ),
            end = RoundedPolygon.circle(numVertices = 16),
        )
    }

    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .scale(scale)
                .clip(MorphShape(morph, morphProgress, rotation))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClickLabel = label,
                    onClick = onClick,
                )
                .padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(88.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Small circular variant used on the recording screen. */
@Composable
fun PulsingMicIndicator(
    amplitude: Float,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = 1f + (amplitude.coerceIn(0f, 1f) * 0.35f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "micPulse",
    )

    Box(
        modifier = modifier
            .size(160.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(72.dp),
        )
    }
}
