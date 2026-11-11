package app.vibeyfin.app.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.theme.FixedColors
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.animationsEnabled

private const val VIEWBOX_SIZE: Float = 100f
private const val MARK_PATH: String = "M27 17C50 19 69 31 81 48L27 69C34 54 34 32 27 17Z"
private const val MARK_STROKE: Float = 8f
private const val BAR_X: Float = 23f
private const val BAR_Y: Float = 81f
private const val BAR_WIDTH: Float = 62f
private const val BAR_HEIGHT: Float = 8f
private const val STATIC_PROGRESS: Float = 0.62f

private val DefaultLogoSize: Dp = 96.dp

/** The Fin Play mark over its progress bar. [progress] from 0 to 1 fills the bar; null shows it static. */
@Composable
fun VibeyFinLogo(modifier: Modifier = Modifier, progress: Float? = null, size: Dp = DefaultLogoSize) {
    val animated: Boolean = animationsEnabled()
    val target: Float = (progress ?: STATIC_PROGRESS).coerceIn(0f, 1f)
    val shown: Float by animateFloatAsState(target, animationSpec = tween(if (animated) VibeyFinTokens.Motion.HERO_MS else 0), label = "logo-progress")

    Canvas(modifier.size(size)) { drawLogo(shown) }
}

private fun DrawScope.drawLogo(fill: Float) {
    val unit: Float = this.size.minDimension / VIEWBOX_SIZE
    val mark: Path = PathParser().parsePathString(MARK_PATH).toPath()
    val barCorner: CornerRadius = CornerRadius(BAR_HEIGHT / 2 * unit)
    val barOrigin: Offset = Offset(BAR_X * unit, BAR_Y * unit)

    scale(unit, pivot = Offset.Zero) {
        drawPath(mark, FixedColors.logoMark, style = Fill)
        drawPath(mark, FixedColors.logoMark, style = Stroke(width = MARK_STROKE, join = StrokeJoin.Round))
    }

    drawRoundRect(FixedColors.logoTrack, barOrigin, Size(BAR_WIDTH * unit, BAR_HEIGHT * unit), barCorner)
    drawRoundRect(FixedColors.logoFill, barOrigin, Size(BAR_WIDTH * unit * fill, BAR_HEIGHT * unit), barCorner)
}
