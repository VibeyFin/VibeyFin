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
import kotlinx.coroutines.launch

/**
 * The feedback for skips in a row, as YouTube draws it: the running total and one chevron on the side of the skip
 * ("+30      >", "<      −30"), no background. On every tap the number pops a little bigger and settles, the chevron
 * recoils towards it and eases back out, and a faint second chevron closes in from behind. Without animations it only
 * shows. It takes no touches.
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
    val pop: Animatable<Float, *> = remember { Animatable(1f) }
    val recoil: Animatable<Float, *> = remember { Animatable(0f) }
    val flight: Animatable<Float, *> = remember { Animatable(1f) }

    LaunchedEffect(run.taps, run.forward) {
        if (!motion) return@LaunchedEffect
        launch {
            pop.animateTo(POP_SCALE, tween(POP_UP_MS, easing = FastOutSlowInEasing))
            pop.animateTo(1f, spring(dampingRatio = POP_DAMPING, stiffness = Spring.StiffnessMediumLow))
        }
        launch {
            recoil.snapTo(1f)
            recoil.animateTo(0f, tween(RECOIL_MS, easing = FastOutSlowInEasing))
        }
        if (run.taps > 1) {
            flight.snapTo(0f)
            flight.animateTo(1f, tween(FLIGHT_MS, easing = LinearOutSlowInEasing))
        }
    }

    Box(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(SIDE_WIDTH)
                .align(if (run.forward) Alignment.CenterEnd else Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s7, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!run.forward) Chevrons(forward = false, recoil = recoil.value, flight = flight.value)
            Text(
                text = Strings.seekTotal(run.forward, run.seconds),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                    shadow = Shadow(VibeyFinTokens.Colors.scrim, Offset(0f, 1f), blurRadius = 4f),
                ),
                color = VibeyFinTokens.Colors.onMedia,
                modifier = Modifier.graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
            )
            if (run.forward) Chevrons(forward = true, recoil = recoil.value, flight = flight.value)
        }
    }
}

// [recoil] 1 to 0 as the chevron eases back out from the number, [flight] 0 to 1 as the faint one behind closes in
@Composable
private fun Chevrons(forward: Boolean, recoil: Float, flight: Float) {
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
        Chevron(forward, Modifier.graphicsLayer { translationX = -ahead * recoil * RECOIL_DISTANCE.toPx() })
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
private const val EXIT_MS = 60
private const val POP_SCALE = 1.15f
private const val POP_UP_MS = 80
private const val POP_DAMPING = 0.45f
private const val RECOIL_MS = 120
private const val FLIGHT_MS = 200
private const val FLIGHT_ALPHA = 0.6f
private val RECOIL_DISTANCE = 4.dp
private val FLIGHT_DISTANCE = 7.dp
private val CHEVRON_SIZE = 22.dp
