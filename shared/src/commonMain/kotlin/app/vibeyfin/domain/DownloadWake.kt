package app.vibeyfin.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Tells a [DownloadRunner] that is waiting (for a network, for Wi-Fi, for a retry) that something changed which it should look
 * at again: a download was added, resumed or paused, or the profile now allows mobile data. One per app, shared by whoever
 * changes the queue and the runner.
 */
class DownloadWake {
    private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val signal: SharedFlow<Unit> get() = events

    fun wake() {
        events.tryEmit(Unit)
    }
}
