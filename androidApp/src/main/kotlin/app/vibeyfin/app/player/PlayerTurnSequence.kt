package app.vibeyfin.app.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// Only for a turn that was expected and never came: the system has the last word on orientation
private const val FLIP_WAIT_MS = 1_000L

// The system rotates and cross-fades a snapshot of the old screen into the new one
@Stable
internal class PlayerTurnSequence(private var rotation: Int, turning: Boolean) {
    private var expecting by mutableStateOf(turning)

    // A SurfaceView doesn't fade with the screen; the system's turn takes it along in its snapshot
    var pictureHidden by mutableStateOf(false)
        private set

    fun onRotation(now: Int) {
        if (now == rotation) return

        rotation = now
        expecting = false
    }

    suspend fun open() = awaitFlip()

    suspend fun close(window: PlayerWindow) {
        // A turn already asked for lands first, so this one turns it back instead of racing it
        awaitFlip()

        if (window.closesWithTurn()) {
            // Not waiting for the flip: the system has its snapshot once this returns, and the page has to be ready for the new window
            window.turnBack()
        } else {
            pictureHidden = true
        }
    }

    private suspend fun awaitFlip() {
        withTimeoutOrNull(FLIP_WAIT_MS) { snapshotFlow { expecting }.first { !it } }
        expecting = false
    }
}

@Composable
internal fun rememberPlayerTurnSequence(window: PlayerWindow?): PlayerTurnSequence {
    val view = LocalView.current

    // Read for the recomposition it brings: the rotation is read live, and can change without one
    LocalConfiguration.current

    val rotation = view.display.rotation
    val sequence = remember { PlayerTurnSequence(rotation, turning = window?.opensWithTurn == true) }

    // Not an effect: the new screen has to go up with the window's first frame
    sequence.onRotation(rotation)

    return sequence
}
