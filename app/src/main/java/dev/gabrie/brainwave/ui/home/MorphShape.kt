package dev.gabrie.brainwave.ui.home

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath

/**
 * A Compose [Shape] that renders a point along a [Morph] between two rounded
 * polygons — the shape-morphing button of Material 3 Expressive.
 *
 * `graphics-shapes` produces geometry normalised to radius 1 around the origin,
 * so it is scaled to the composable's bounds and re-centred here.
 */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotationDegrees: Float = 0f,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = morph.toPath(progress.coerceIn(0f, 1f))
        val matrix = android.graphics.Matrix()
        matrix.setScale(size.width / 2f, size.height / 2f)
        matrix.postTranslate(size.width / 2f, size.height / 2f)
        if (rotationDegrees != 0f) {
            matrix.postRotate(rotationDegrees, size.width / 2f, size.height / 2f)
        }
        path.transform(matrix)
        return Outline.Generic(path.asComposePath())
    }
}
