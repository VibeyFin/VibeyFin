package app.vibeyfin.app.player

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// What the player reports, read by the controls and the scrubber
@Stable
internal class PlaybackClock {
    var playing by mutableStateOf(true)
    var buffering by mutableStateOf(true)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var bufferedMs by mutableLongStateOf(0L)

    // When the last seek was asked for, to time how long the player takes to be ready again; 0 when none waits
    var seekAt by mutableLongStateOf(0L)

    // The scrubber shows the new position until the next tick, so it doesn't fall back to the old one
    fun startSeek(toMs: Long) {
        seekAt = SystemClock.elapsedRealtime()
        positionMs = toMs
    }
}

// What the screen is showing on top of the picture, and whether it is on its way out
@Stable
internal class PlayerChrome {
    // The controls go at once, the player pauses and the screen is popped with the app's own transition, which carries the paused video along
    var leaving by mutableStateOf(false)
    var controlsVisible by mutableStateOf(true)

    // Counts every touch, so the controls' hide timer starts over
    var interactions by mutableIntStateOf(0)
    var scrubbing by mutableStateOf(false)
    var showTracks by mutableStateOf(false)
    var showSpeeds by mutableStateOf(false)

    fun touched() {
        interactions++
    }

    fun closeSheets() {
        showTracks = false
        showSpeeds = false
    }
}
