package app.vibeyfin.app.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.animationsEnabled
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.playback.SkipRun

/**
 * The feedback for skips in a row: the video dims a little and the running total ("+30 >", "< −30") sits on the side of
 * the skip. The chevron nudges the way of the skip on each tap, unless animations are off. It takes no touches.
 */
@Composable
fun BoxScope.SeekIndicator(run: SkipRun?) {
    val shown: Array<SkipRun?> = remember { arrayOfNulls(1) }

    if (run != null) shown[0] = run

    AnimatedVisibility(
        visible = run != null,
        enter = fadeIn(tween(FADE_MS)),
        exit = fadeOut(tween(FADE_MS)),
        modifier = Modifier.matchParentSize(),
    ) {
        shown[0]?.let { SeekIndicatorContent(it) }
    }
}

@Composable
private fun SeekIndicatorContent(run: SkipRun) {
    val motion: Boolean = animationsEnabled()
    val nudge: Animatable<Float, *> = remember { Animatable(0f) }
    val direction: Float = if (run.forward) 1f else -1f

    LaunchedEffect(run.taps) {
        if (motion) {
            nudge.snapTo(1f)
            nudge.animateTo(0f, tween(NUDGE_MS))
        }
    }

    Box(Modifier.fillMaxSize().background(VibeyFinTokens.Colors.scrim.copy(alpha = DIM_ALPHA)).clearAndSetSemantics { }) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(SIDE_WIDTH)
                .align(if (run.forward) Alignment.CenterEnd else Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!run.forward) Chevron(forward = false, Modifier.graphicsLayer { translationX = direction * nudge.value * NUDGE_DP.toPx() })
            Text(
                text = Strings.seekTotal(run.forward, run.seconds),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                    shadow = Shadow(VibeyFinTokens.Colors.scrim, Offset(0f, 2f), blurRadius = 6f),
                ),
                color = VibeyFinTokens.Colors.onMedia,
            )
            if (run.forward) Chevron(forward = true, Modifier.graphicsLayer { translationX = direction * nudge.value * NUDGE_DP.toPx() })
        }
    }
}

@Composable
private fun Chevron(forward: Boolean, modifier: Modifier = Modifier) {
    val ink = VibeyFinTokens.Colors.onMedia
    val shadow = VibeyFinTokens.Colors.scrim

    Canvas(modifier.size(CHEVRON_SIZE)) {
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val tip: Float = if (forward) size.width * 0.7f else size.width * 0.3f
        val tail: Float = if (forward) size.width * 0.3f else size.width * 0.7f
        val path = Path().apply {
            moveTo(tail, size.height * 0.15f)
            lineTo(tip, size.height / 2)
            lineTo(tail, size.height * 0.85f)
        }

        translate(top = 1.dp.toPx()) { drawPath(path, shadow, style = stroke) }
        drawPath(path, ink, style = stroke)
    }
}

private const val SIDE_WIDTH = 0.38f
private const val DIM_ALPHA = 0.4f
private const val FADE_MS = 150
private const val NUDGE_MS = 220
private val NUDGE_DP = 6.dp
private val CHEVRON_SIZE = 22.dp
