package app.vibeyfin.data.download

import app.vibeyfin.domain.DownloadNetwork
import app.vibeyfin.domain.DownloadRunner
import app.vibeyfin.domain.DownloadScheduler
import app.vibeyfin.domain.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration.Companion.minutes

/**
 * Runs [DownloadRunner] in the app's own process. iOS has no scheduler for this: what keeps a video coming with the app suspended
 * or closed is the background URLSession [IosBackgroundTransfer] hands each video to, and the system wakes the app when one is done
 * (the app then starts, finds the file in place and the runner completes the download). So this only has to run the runner when the
 * app is up, when something was queued, and when a connection comes back. One run at a time; a request that comes in during a run makes
 * it go round again.
 */
class IosDownloadScheduler(
    private val scope: CoroutineScope,
    network: NetworkMonitor,
    private val runner: () -> DownloadRunner,
) : DownloadScheduler {
    override val isSupported = true

    private val running = Mutex()
    private var again = false

    init {
        // A connection coming back is a reason to look again; the first value is the one at start, which the start-up check covers.
        scope.launch {
            network.state.map { it.isConnected }.distinctUntilChanged().drop(1).filter { it }.collect { schedule(DownloadNetwork.None) }
        }
    }

    override fun schedule(network: DownloadNetwork) {
        again = true
        scope.launch {
            // A run is going: it will see `again` when it ends.
            if (!running.tryLock()) return@launch
            try {
                while (again) {
                    again = false
                    runner().run(patience = PATIENCE)
                }
            } finally {
                running.unlock()
            }
        }
    }

    private companion object {
        val PATIENCE = 30.minutes
    }
}
