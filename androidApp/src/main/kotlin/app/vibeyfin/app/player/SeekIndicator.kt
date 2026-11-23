package app.vibeyfin.app.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
 * The feedback for skips in a row, as YouTube draws it: the running total and one chevron on the side of the skip
 * ("+30    >", "<    −30"), no background. It fades in with the chevron gliding out from the number; on each further tap a
 * second chevron flies in from behind and knocks the first, which springs back. Without animations it only shows. It takes
 * no touches.
 */
@Composable
fun BoxScope.SeekIndicator(run: SkipRun?) {
    // The last run stays drawn while the indicator goes
    val shown: Array<SkipRun?> = remember { arrayOfNulls(1) }

    if (run != null) shown[0] = run

    AnimatedVisibility(
        visible = run != null,
        enter = fadeIn(tween(APPEAR_MS)),
        exit = fadeOut(tween(EXIT_MS)),
        modifier = Modifier.matchParentSize(),
    ) {
        shown[0]?.let { SeekIndicatorContent(it) }
    }
}

@Composable
private fun SeekIndicatorContent(run: SkipRun) {
    val motion: Boolean = animationsEnabled()
    val glide: Animatable<Float, *> = remember { Animatable(0f) }
    val flight: Animatable<Float, *> = remember { Animatable(1f) }
    val knock: Animatable<Float, *> = remember { Animatable(0f) }

    LaunchedEffect(run.forward) {
        glide.snapTo(if (motion) 1f else 0f)
        glide.animateTo(0f, tween(GLIDE_MS, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(run.taps) {
        if (motion && run.taps > 1) {
            flight.snapTo(0f)
            flight.animateTo(1f, tween(FLIGHT_MS, easing = LinearOutSlowInEasing))
            knock.snapTo(1f)
            knock.animateTo(0f, spring(dampingRatio = KNOCK_DAMPING, stiffness = Spring.StiffnessMediumLow))
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
            if (!run.forward) Chevrons(forward = false, glide = glide.value, flight = flight.value, knock = knock.value)
            Text(
                text = Strings.seekTotal(run.forward, run.seconds),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                    shadow = Shadow(VibeyFinTokens.Colors.scrim, Offset(0f, 1f), blurRadius = 4f),
                ),
                color = VibeyFinTokens.Colors.onMedia,
            )
            if (run.forward) Chevrons(forward = true, glide = glide.value, flight = flight.value, knock = knock.value)
        }
    }
}

// [glide] 1 to 0 as the indicator appears, [flight] 0 to 1 as the second chevron closes in, [knock] 1 to 0 as the first springs back
@Composable
private fun Chevrons(forward: Boolean, glide: Float, flight: Float, knock: Float) {
    val ahead: Float = if (forward) 1f else -1f

    Box {
        if (flight < 1f) {
            Chevron(
                forward,
                Modifier.graphicsLayer {
                    translationX = -ahead * (1f - flight) * FLIGHT_DISTANCE.toPx()
                    alpha = FLIGHT_ALPHA * flight
                },
            )
        }
        Chevron(
            forward,
            Modifier.graphicsLayer {
                translationX = ahead * (knock * KNOCK_DISTANCE.toPx() - glide * GLIDE_DISTANCE.toPx())
                alpha = 1f - glide * GLIDE_FADE
            },
        )
    }
}

@Composable
private fun Chevron(forward: Boolean, modifier: Modifier = Modifier) {
    val ink = VibeyFinTokens.Colors.onMedia
    val shadow = VibeyFinTokens.Colors.scrim

    Canvas(modifier.size(CHEVRON_SIZE)) {
        val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val tip: Float = if (forward) size.width * 0.68f else size.width * 0.32f
        val tail: Float = if (forward) size.width * 0.32f else size.width * 0.68f
        val path = Path().apply {
            moveTo(tail, size.height * 0.18f)
            lineTo(tip, size.height / 2)
            lineTo(tail, size.height * 0.82f)
        }

        translate(top = 1.dp.toPx()) { drawPath(path, shadow, style = stroke) }
        drawPath(path, ink, style = stroke)
    }
}

private const val SIDE_WIDTH = 0.44f
private const val APPEAR_MS = 80
private const val EXIT_MS = 40
private const val GLIDE_MS = 160
private const val GLIDE_FADE = 0.6f
private const val FLIGHT_MS = 130
private const val FLIGHT_ALPHA = 0.9f
private const val KNOCK_DAMPING = 0.4f
private val GLIDE_DISTANCE = 10.dp
private val FLIGHT_DISTANCE = 14.dp
private val KNOCK_DISTANCE = 3.dp
private val CHEVRON_SIZE = 22.dp
