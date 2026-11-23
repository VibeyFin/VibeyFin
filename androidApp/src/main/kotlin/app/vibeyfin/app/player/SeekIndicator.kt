package app.vibeyfin.app.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
 * The feedback for skips in a row, as YouTube draws it: the running total and one thin chevron on the side of the skip
 * ("+30   >", "<   −30"), no background. On each tap a faint second chevron trails the first and slides into it, unless
 * animations are off. It takes no touches.
 */
@Composable
fun BoxScope.SeekIndicator(run: SkipRun?) {
    // The last run stays drawn while the indicator fades out
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
    val trail: Animatable<Float, *> = remember { Animatable(0f) }

    LaunchedEffect(run.taps) {
        if (motion) {
            trail.snapTo(1f)
            trail.animateTo(0f, tween(TRAIL_MS))
        }
    }

    Box(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(SIDE_WIDTH)
                .align(if (run.forward) Alignment.CenterEnd else Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s6, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!run.forward) TrailingChevron(forward = false, trail = trail.value)
            Text(
                text = Strings.seekTotal(run.forward, run.seconds),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                    shadow = Shadow(VibeyFinTokens.Colors.scrim, Offset(0f, 1f), blurRadius = 4f),
                ),
                color = VibeyFinTokens.Colors.onMedia,
            )
            if (run.forward) TrailingChevron(forward = true, trail = trail.value)
        }
    }
}

// [trail] runs from 1, just after a tap, to 0: the faint chevron behind closes in on the main one and fades
@Composable
private fun TrailingChevron(forward: Boolean, trail: Float) {
    val behind: Float = if (forward) -1f else 1f

    Box {
        if (trail > 0f) {
            Chevron(
                forward,
                Modifier.graphicsLayer {
                    translationX = behind * trail * TRAIL_OFFSET.toPx()
                    alpha = TRAIL_ALPHA * trail
                },
            )
        }
        Chevron(forward)
    }
}

@Composable
private fun Chevron(forward: Boolean, modifier: Modifier = Modifier) {
    val ink = VibeyFinTokens.Colors.onMedia
    val shadow = VibeyFinTokens.Colors.scrim

    Canvas(modifier.size(CHEVRON_SIZE)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val tip: Float = if (forward) size.width * 0.65f else size.width * 0.35f
        val tail: Float = if (forward) size.width * 0.35f else size.width * 0.65f
        val path = Path().apply {
            moveTo(tail, size.height * 0.2f)
            lineTo(tip, size.height / 2)
            lineTo(tail, size.height * 0.8f)
        }

        translate(top = 1.dp.toPx()) { drawPath(path, shadow, style = stroke) }
        drawPath(path, ink, style = stroke)
    }
}

private const val SIDE_WIDTH = 0.5f
private const val FADE_MS = 150
private const val TRAIL_MS = 260
private const val TRAIL_ALPHA = 0.5f
private val TRAIL_OFFSET = 7.dp
private val CHEVRON_SIZE = 18.dp
