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

/**
 * The system's rotation animates a snapshot of the screen, so the window flips under a jump cut and the app draws the
 * twist: from where the old window left the picture, to where the new window puts it.
 */
@Stable
internal class PlayerTurnMotion(rotation: Int, settled: Boolean) {
    private var expecting = false
    private var rotation by mutableIntStateOf(rotation)
    private var fromRotation by mutableStateOf<Int?>(null)
    private var progress by mutableFloatStateOf(1f)

    var pictureRatio by mutableFloatStateOf(DEFAULT_PICTURE_RATIO)

    /** False until the screen has finished turning: the controls come in after it. */
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
            twist()
            window.useOwnRotationAnimation()
        }

        settled = true
    }

    suspend fun close(window: PlayerWindow) {
        settled = false

        if (!window.closesWithTurn()) return

        window.useJumpCut()
        repeat(FRAMES_FOR_ATTRIBUTES) { withFrameNanos { } }
        expecting = true
        window.restoreOrientation()
        twist()
    }

    private suspend fun twist() {
        withTimeoutOrNull(FLIP_WAIT_MS) { snapshotFlow { fromRotation }.first { it != null } }
        expecting = false

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
    val configuration = LocalConfiguration.current
    val rotation = remember(configuration) { view.display.rotation }
    val motion = remember { PlayerTurnMotion(rotation, settled = window?.opensWithTurn != true) }

    // In composition, not an effect: the first frame of the new window has to be drawn turned already
    motion.onRotation(rotation)

    return motion
}
