package app.vibeyfin.domain

import app.vibeyfin.domain.model.ProfileRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * What one player does with a download: nothing goes to the server while it plays (there is no play session to report), but where
 * the user is is saved on the device every [interval] and when they pause, seek or stop, so a crash loses little. When the playing
 * stops, the position is handed to [OfflineSync], which sends it at once if the server can be reached and, if not, when it can
 * (the platform's scheduler makes sure of that even if the app is closed). The same events as [PlaybackReporter]: after a stop,
 * the next [playing] starts again.
 */
class LocalPlaybackReporter(
    private val connection: ConnectionManager,
    private val scope: CoroutineScope,
    private val progress: OfflineProgress,
    private val sync: OfflineSync,
    private val scheduler: ProgressSyncScheduler,
    private val changes: WatchChanges,
    private val interval: Duration = PROGRESS_INTERVAL,
) : PlayReporter {
    private sealed interface Event {
        data class Begin(val plan: PlaybackPlan) : Event
        data class Position(val ms: Long) : Event
        data class Playing(val ms: Long) : Event
        data class Paused(val ms: Long) : Event
        data class Seeked(val ms: Long) : Event
        data class Stop(val ms: Long) : Event
        data object Tick : Event
    }

    private val events = Channel<Event>(Channel.UNLIMITED)

    // Only touched by the loop below, one event at a time.
    private var plan: PlaybackPlan? = null
    private var profile: ProfileRef? = null
    private var started = false
    private var isPaused = false
    private var positionMs = 0L
    private var ticker: Job? = null

    init {
        scope.launch {
            try {
                for (event in events) handle(event)
            } finally {
                ticker?.cancel()
            }
        }
    }

    override fun begin(plan: PlaybackPlan) = send(Event.Begin(plan))

    override fun position(positionMs: Long) = send(Event.Position(positionMs))

    override fun playing(positionMs: Long) = send(Event.Playing(positionMs))

    override fun paused(positionMs: Long) = send(Event.Paused(positionMs))

    override fun seeked(positionMs: Long) = send(Event.Seeked(positionMs))

    override fun stop(positionMs: Long, failed: Boolean) = send(Event.Stop(positionMs))

    override fun close() {
        events.close()
    }

    private fun send(event: Event) {
        events.trySend(event)
    }

    private suspend fun handle(event: Event) {
        when (event) {
            is Event.Begin -> plan = event.plan
            is Event.Position -> positionMs = event.ms
            is Event.Playing -> {
                positionMs = event.ms
                if (!started) {
                    started = true
                    profile = (connection.state.value as? ConnectionState.Connected)?.let { ProfileRef(it.server.id, it.account.userId) }
                }
                isPaused = false
                startTicker()
            }
            is Event.Paused -> {
                positionMs = event.ms
                if (started && !isPaused) {
                    isPaused = true
                    stopTicker()
                    save()
                }
            }
            is Event.Seeked -> {
                positionMs = event.ms
                if (started) save()
            }
            is Event.Stop -> {
                positionMs = event.ms
                stopTicker()
                if (started) {
                    started = false
                    isPaused = false
                    save()
                    finish()
                }
            }
            Event.Tick -> if (started && !isPaused) save()
        }
    }

    private suspend fun save() {
        val current = plan ?: return
        val owner = profile ?: return
        try {
            progress.record(owner, current.itemId, positionMs, current.runtimeMs)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Playback goes on; the next save tries again.
        }
    }

    /** The watch state changed on the device: the screens look again, and the server is told when it can be. */
    private fun finish() {
        changes.changed()
        scheduler.schedule()
        scope.launch {
            try {
                sync.syncAll()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The scheduled work tries again.
            }
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                delay(interval)
                send(Event.Tick)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }
}
