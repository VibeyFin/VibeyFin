package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.api.ApiResult
import com.liamgleeson.vibeyfin.data.api.JellyfinApi
import com.liamgleeson.vibeyfin.data.api.PlaybackProgressDto
import com.liamgleeson.vibeyfin.data.api.PlaybackStartDto
import com.liamgleeson.vibeyfin.data.api.reportPlaybackProgress
import com.liamgleeson.vibeyfin.data.api.reportPlaybackStart
import com.liamgleeson.vibeyfin.domain.model.ProfileRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How often progress is sent while playing. Pause, resume and seek are sent straight away. */
val PROGRESS_INTERVAL: Duration = 10.seconds

/**
 * Tells the server what one player is doing: it started ([playing]), where it is (every
 * [interval] while playing, and at once on [paused], resume and [seeked]), and that it [stop]ped.
 *
 * The player pushes events in; nothing here reads the player. Events are handled one at a time in
 * the order they came in, so `Start` always reaches the server before `Progress`, and `Stopped`
 * last. That happens on [scope], which must outlive the screen: a `Stopped` sent as the screen goes
 * away would be cancelled with the screen's own scope. Reports are best effort: a failed one is
 * dropped, except `Stopped`, which is tried once more.
 *
 * `Stopped` is the one report that must not get lost, so a session is also saved in [pending] when it
 * starts and as its position moves, and is only forgotten once the server has answered. If the
 * process dies first, [PlaybackRecovery] sends it at the next start. It is sent as the profile that
 * started it, even if the user has switched profile or the session has ended since.
 *
 * A stop is not final. After one, the next [playing] starts the session again, which is what
 * happens when the app goes to the background and comes back. [begin] gives the item to report on,
 * and a new one for each stream, since a transcode fallback is a new play session.
 *
 * Calls may come from any thread.
 */
class PlaybackReporter(
    private val connection: ConnectionManager,
    private val scope: CoroutineScope,
    private val pending: PendingReports,
    private val recovery: PlaybackRecovery,
    private val changes: WatchChanges,
    private val interval: Duration = PROGRESS_INTERVAL,
) {
    private sealed interface Event {
        data class Begin(val plan: PlaybackPlan) : Event
        data class Position(val ms: Long) : Event
        data class Playing(val ms: Long) : Event
        data class Paused(val ms: Long) : Event
        data class Seeked(val ms: Long) : Event
        data class Stop(val ms: Long, val failed: Boolean) : Event
        data object Tick : Event
    }

    private val events = Channel<Event>(Channel.UNLIMITED)

    // Everything below is only touched by the loop in `handle`, one event at a time.
    private var plan: PlaybackPlan? = null
    private var started = false
    private var isPaused = false
    private var positionMs = 0L
    private var ticker: Job? = null

    /** The session that was started, as `Stopped` has to name it; null if nobody was signed in to start it. */
    private var open: PendingStop? = null

    init {
        scope.launch {
            try {
                for (event in events) handle(event)
            } finally {
                ticker?.cancel()
            }
        }
    }

    /**
     * The stream to report on. Call it before the player starts. Called again with the same play
     * session and other tracks, it reports the change as it happens.
     */
    fun begin(plan: PlaybackPlan) = send(Event.Begin(plan))

    /** The latest position, as often as it is known. Nothing is sent for it. */
    fun position(positionMs: Long) = send(Event.Position(positionMs))

    /** The player is playing: it started, or resumed after a pause or a stop. */
    fun playing(positionMs: Long) = send(Event.Playing(positionMs))

    fun paused(positionMs: Long) = send(Event.Paused(positionMs))

    fun seeked(positionMs: Long) = send(Event.Seeked(positionMs))

    /** Playback ended, was closed or went to the background. Does nothing if nothing was started. */
    fun stop(positionMs: Long, failed: Boolean = false) = send(Event.Stop(positionMs, failed))

    /** No more events. What is already queued, `Stopped` included, is still sent. */
    fun close() {
        events.close()
    }

    private fun send(event: Event) {
        events.trySend(event)
    }

    private suspend fun handle(event: Event) {
        when (event) {
            is Event.Begin -> {
                val previous = plan
                plan = event.plan
                // The same play session with other tracks: the player switched audio or subtitles
                // without a new stream, which the server should hear about now, as with a seek.
                val switchedTracks = previous != null && previous.playSessionId == event.plan.playSessionId &&
                    (previous.audioStreamIndex != event.plan.audioStreamIndex || previous.subtitleStreamIndex != event.plan.subtitleStreamIndex)
                if (started && switchedTracks) sendProgress()
            }
            is Event.Position -> positionMs = event.ms
            is Event.Playing -> {
                positionMs = event.ms
                if (!started) {
                    started = true
                    isPaused = false
                    open = plan?.let { current -> currentProfile()?.let { openSession(it, current) } }
                    keep()
                    sendStart()
                } else if (isPaused) {
                    isPaused = false
                    sendProgress()
                }
                startTicker()
            }
            is Event.Paused -> {
                positionMs = event.ms
                if (started && !isPaused) {
                    isPaused = true
                    stopTicker()
                    sendProgress()
                }
            }
            is Event.Seeked -> {
                positionMs = event.ms
                if (started) sendProgress()
            }
            is Event.Stop -> {
                positionMs = event.ms
                stopTicker()
                if (started) {
                    started = false
                    isPaused = false
                    keep(event.failed)
                    sendStopped()
                }
            }
            Event.Tick -> if (started && !isPaused) sendProgress()
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

    private suspend fun sendStart() {
        val plan = plan ?: return
        call { api ->
            api.reportPlaybackStart(
                PlaybackStartDto(
                    itemId = plan.itemId,
                    mediaSourceId = plan.mediaSourceId,
                    playSessionId = plan.playSessionId,
                    playMethod = plan.method.wireName,
                    positionTicks = positionMs * TICKS_PER_MILLISECOND,
                    isPaused = false,
                    canSeek = true,
                    audioStreamIndex = plan.audioStreamIndex,
                    subtitleStreamIndex = plan.subtitleStreamIndex,
                ),
            )
        }
    }

    private suspend fun sendProgress() {
        val plan = plan ?: return
        keep()
        call { api ->
            api.reportPlaybackProgress(
                PlaybackProgressDto(
                    itemId = plan.itemId,
                    mediaSourceId = plan.mediaSourceId,
                    playSessionId = plan.playSessionId,
                    playMethod = plan.method.wireName,
                    positionTicks = positionMs * TICKS_PER_MILLISECOND,
                    isPaused = isPaused,
                    canSeek = true,
                    audioStreamIndex = plan.audioStreamIndex,
                    subtitleStreamIndex = plan.subtitleStreamIndex,
                ),
            )
        }
    }

    /** Saves the session with where it is now, so a crash from here on leaves a `Stopped` to send. */
    private suspend fun keep(failed: Boolean = false) {
        val stop = open?.copy(positionTicks = positionMs * TICKS_PER_MILLISECOND, failed = failed) ?: return
        open = stop
        try {
            pending.save(stop)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The report below still goes out; only the safety net is missing.
        }
    }

    private suspend fun sendStopped() {
        val stop = open ?: return
        open = null
        // The one report worth a second try: without it the server keeps showing the item as playing.
        // If both fail the session stays saved and goes out at the next start.
        if (recovery.send(stop) == StopOutcome.Retry) recovery.send(stop)
        changes.changed()
    }

    /** Sends one report with the current session. A report that can't be sent, or fails, is dropped. */
    private suspend fun call(block: suspend (JellyfinApi) -> ApiResult<Unit>): ApiResult<Unit>? {
        val connected = connection.state.value as? ConnectionState.Connected ?: return null
        return try {
            block(connected.api)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun currentProfile(): ProfileRef? =
        (connection.state.value as? ConnectionState.Connected)?.let { ProfileRef(it.server.id, it.account.userId) }

    private fun openSession(profile: ProfileRef, plan: PlaybackPlan) = PendingStop(
        profile = profile,
        itemId = plan.itemId,
        mediaSourceId = plan.mediaSourceId,
        playSessionId = plan.playSessionId,
        positionTicks = positionMs * TICKS_PER_MILLISECOND,
        failed = false,
    )
}
