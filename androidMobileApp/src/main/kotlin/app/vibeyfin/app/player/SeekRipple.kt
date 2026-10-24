package app.vibeyfin.app.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.vibeyfin.design.VibeyFinTokens
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.playback.SkipRun

/**
 * The feedback for skips in a row, the way Android's own players show a double-tap seek: a half
 * circle on that side of the screen, a ripple for each tap, three chevrons running in the direction
 * of the skip, and the seconds skipped so far below them. It takes no touches, so taps reach the video.
 */
@Composable
fun BoxScope.SeekRipple(run: SkipRun?) {
    // The last run stays drawn while the ripple fades out
    val shown = remember { arrayOfNulls<SkipRun>(1) }

    if (run != null) shown[0] = run

    AnimatedVisibility(
        visible = run != null,
        enter = fadeIn(tween(RIPPLE_FADE_MS)),
        exit = fadeOut(tween(RIPPLE_FADE_MS)),
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(RIPPLE_WIDTH)
            .align(if (shown[0]?.forward == true) Alignment.CenterEnd else Alignment.CenterStart),
    ) {
        shown[0]?.let { SeekRippleContent(it) }
    }
}

@Composable
private fun SeekRippleContent(run: SkipRun) {
    val wash = VibeyFinTokens.Colors.ink.copy(alpha = 0.12f)
    val tap = remember { Animatable(1f) }

    LaunchedEffect(run.taps) {
        tap.snapTo(0f)
        tap.animateTo(1f, tween(TAP_RIPPLE_MS))
    }

    Box(Modifier.fillMaxSize().clipToBounds().clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // A circle much taller than the screen, centered off its edge, so only a gentle curve shows
            val radius = size.height
            val center = Offset(if (run.forward) radius else size.width - radius, size.height / 2)

            drawCircle(wash, radius, center)

            drawCircle(
                VibeyFinTokens.Colors.ink.copy(alpha = 0.16f * (1f - tap.value)),
                radius = size.width * (0.2f + 0.8f * tap.value),
                center = Offset(size.width / 2, size.height / 2),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
            Chevrons(run.forward)
            Text(
                text = Strings.seekFlash(run.seconds),
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                color = VibeyFinTokens.Colors.ink,
            )
        }
    }
}

@Composable
private fun Chevrons(forward: Boolean) {
    val phase by rememberInfiniteTransition(label = "chevrons").animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(CHEVRON_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )

    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { slot ->
            // Back runs right to left, so its first lit triangle is the one on the right
            val order = if (forward) slot else 2 - slot
            val lit = (1f - kotlin.math.abs(phase - order - 0.5f)).coerceIn(0.25f, 1f)

            Canvas(Modifier.size(12.dp)) {
                val path = Path().apply {
                    if (forward) {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2)
                        lineTo(0f, size.height)
                    } else {
                        moveTo(size.width, 0f)
                        lineTo(0f, size.height / 2)
                        lineTo(size.width, size.height)
                    }

                    close()
                }

                drawPath(path, VibeyFinTokens.Colors.ink.copy(alpha = lit))
            }
        }
    }
}

private const val RIPPLE_WIDTH = 0.38f
private const val RIPPLE_FADE_MS = 150
private const val TAP_RIPPLE_MS = 450
private const val CHEVRON_CYCLE_MS = 900
