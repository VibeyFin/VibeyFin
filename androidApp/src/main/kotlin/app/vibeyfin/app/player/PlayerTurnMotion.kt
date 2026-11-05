package app.vibeyfin.app.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val TWIST_MS = 350

// Only for a turn that was expected and never came: the system has the last word on orientation
private const val FLIP_WAIT_MS = 1_000L

// A window attribute reaches the system with the next frames, and has to get there before the orientation request does
private const val FRAMES_FOR_ATTRIBUTES = 2

// The system's rotation animates a snapshot it can't shape, so the window flips under a jump cut and the app draws the twist
@Stable
internal class PlayerTurnMotion(rotation: Int, settled: Boolean) {
    private var expecting by mutableStateOf(false)
    private var rotation by mutableIntStateOf(rotation)
    private var fromRotation by mutableStateOf<Int?>(null)
    private var progress by mutableFloatStateOf(1f)

    var pictureRatio by mutableFloatStateOf(DEFAULT_PICTURE_RATIO)

    var settled by mutableStateOf(settled)
        private set

    fun onRotation(now: Int) {
        if (now == rotation) return

        if (expecting) {
            // A back press during the twist carries on from wherever the picture has got to
            progress = 1f - progress
            fromRotation = rotation
            expecting = false
        }

        rotation = now
    }

    suspend fun open(window: PlayerWindow, lifecycle: Lifecycle) {
        if (window.opensWithTurn) {
            // The page underneath must be gone before the window turns, and the entry resumes when its transition ends
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            expecting = true
            window.requestLandscape()
            awaitFlip()
            finishTwist()
            window.useCrossfade()
        }

        settled = true
    }

    suspend fun close(window: PlayerWindow, lifecycle: Lifecycle) {
        settled = false

        // Closing the picture-in-picture window leaves a stopped screen, whose frames never come
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return

        // A turn already asked for lands first, so this one turns it back instead of racing it
        awaitFlip()

        if (window.closesWithTurn()) {
            window.useJumpCut()
            repeat(FRAMES_FOR_ATTRIBUTES) { withFrameNanos { } }

            // Turning the phone over delivers no configuration change, so this may have moved unseen
            rotation = window.displayRotation()
            expecting = true
            window.restoreOrientation()
            awaitFlip()
        }

        finishTwist()
    }

    private suspend fun awaitFlip() {
        withTimeoutOrNull(FLIP_WAIT_MS) { snapshotFlow { expecting }.first { !it } }
        expecting = false
    }

    private suspend fun finishTwist() {
        if (fromRotation != null) {
            animate(progress, 1f, animationSpec = tween(TWIST_MS, easing = FastOutSlowInEasing)) { value, _ -> progress = value }
        }

        fromRotation = null
    }

    fun Modifier.turning() = graphicsLayer {
        val from = fromRotation ?: return@graphicsLayer
        val turn = turnFrom(from, rotation, size.width, size.height, pictureRatio)

        rotationZ = turn.degrees * (1f - progress)
        scaleX = lerp(turn.scale, 1f, progress)
        scaleY = scaleX
    }
}

@Composable
internal fun rememberPlayerTurnMotion(window: PlayerWindow?): PlayerTurnMotion {
    val view = LocalView.current

    // Read for the recomposition it brings: the rotation is read live, and can change without one
    LocalConfiguration.current

    val rotation = view.display.rotation
    val motion = remember { PlayerTurnMotion(rotation, settled = window?.opensWithTurn != true) }

    // Not an effect: the first frame of the new window has to be drawn turned already
    motion.onRotation(rotation)

    return motion
}
