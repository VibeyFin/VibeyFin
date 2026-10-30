package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.PlaybackStopDto
import app.vibeyfin.data.api.reportPlaybackStopped
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.hours

/**
 * A saved session older than this is dropped unsent. `Stopped` carries a position, which the server
 * saves, so one sent a week late would overwrite what was watched on another device since.
 */
val PENDING_STOP_MAX_AGE = 24.hours

/** What happened to one `Stopped`. */
enum class StopOutcome {
    /** The server took it. */
    Sent,

    /** It can never be sent (the profile is locked, or the server refused it), so it was forgotten. */
    Dropped,

    /** It couldn't be sent now (no connection, or a server error); it is kept for a later try. */
    Retry,
}

/**
 * Tells the server a play session stopped, as the profile that started it, which need not be the one
 * signed in now. Used when playback ends, and at the start of the next run for sessions that a crash
 * or a killed process left behind.
 */
class PlaybackRecovery(
    private val pending: PendingReports,
    private val connection: ConnectionManager,
) {
    /** Sends [stop] and forgets it unless it should be tried again. */
    suspend fun send(stop: PendingStop): StopOutcome {
        val info = PlaybackStopDto(
            itemId = stop.itemId,
            mediaSourceId = stop.mediaSourceId,
            playSessionId = stop.playSessionId,
            positionTicks = stop.positionTicks,
            failed = stop.failed,
        )
        val outcome = try {
            when (val call = connection.asProfile(stop.profile.serverId, stop.profile.userId) { it.reportPlaybackStopped(info) }) {
                ProfileCall.NoSession -> StopOutcome.Dropped

                ProfileCall.Unreachable -> StopOutcome.Retry

                is ProfileCall.Answered -> when (val result = call.result) {
                    is ApiResult.Success -> StopOutcome.Sent
                    is ApiResult.Failure -> if (result.error.mayPass()) StopOutcome.Retry else StopOutcome.Dropped
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            StopOutcome.Retry
        }
        if (outcome != StopOutcome.Retry) forget(stop)
        return outcome
    }

    /** Sends every session a previous run left behind. Call it once, when the process starts. */
    suspend fun recoverOnStart() {
        try {
            pending.discardOlderThan(PENDING_STOP_MAX_AGE)
            for (stop in pending.stops()) send(stop)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Nothing to tell the user: the records stay and the next start tries again.
        }
    }

    private suspend fun forget(stop: PendingStop) {
        try {
            pending.remove(stop)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A record that stays is sent again at the next start, which the server shrugs off.
        }
    }

    private fun ApiError.mayPass() = this == ApiError.Network || this is ApiError.Server
}
