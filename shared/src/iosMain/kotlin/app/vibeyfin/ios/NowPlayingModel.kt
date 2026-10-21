package app.vibeyfin.ios

import app.vibeyfin.domain.NowPlaying
import app.vibeyfin.domain.NowPlayingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * What the player is showing, as SwiftUI needs it for the mini player: the state while something is
 * playing (null otherwise), and a way to ask for the full player back. [NowPlaying] is a single that
 * outlives any one screen, so this only watches it.
 */
class NowPlayingModel {
    private val nowPlaying = KoinPlatform.getKoin().get<NowPlaying>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observing: Job? = null

    /** Calls [onChange] on the main thread with the current state now and with every change after. */
    fun observe(onChange: (NowPlayingState?) -> Unit) {
        observing?.cancel()
        observing = scope.launch { nowPlaying.state.collect { onChange(it) } }
    }

    /** Asks for the full player to come back for the item now playing. */
    fun requestRestore() = nowPlaying.requestRestore()

    fun close() {
        scope.cancel()
    }
}
