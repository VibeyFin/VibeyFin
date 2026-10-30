package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.BaseItemDto
import app.vibeyfin.data.api.UpdateUserItemDataDto
import app.vibeyfin.data.api.itemsById
import app.vibeyfin.data.api.itemsByIds
import app.vibeyfin.data.api.setPlayed
import app.vibeyfin.data.api.updateUserData
import app.vibeyfin.data.db.DOWNLOAD_DONE
import app.vibeyfin.data.db.DownloadDao
import app.vibeyfin.domain.model.ProfileRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * Asks the platform to run [OfflineSync.syncAll] once there is a connection, even if the app has been closed since (Android:
 * WorkManager). Asking again while one is waiting is harmless.
 */
interface ProgressSyncScheduler {
    fun schedule()
}

object NoProgressSyncScheduler : ProgressSyncScheduler {
    override fun schedule() = Unit
}

/** What became of one offline record. */
private enum class SyncOutcome {
    /** The server took it. */
    Sent,

    /** The server has a newer record of the item (watched elsewhere since), so this one was dropped: the server is the source of truth. */
    ServerNewer,

    /** The item is gone from the server, or the server refused: nothing to send it to. */
    Dropped,

    /** It could not be sent now (no connection, a server error, a locked profile); it is kept. */
    Retry,
}

/**
 * Sends what was watched offline to the server, one item at a time, and brings what the server knows back into the downloaded
 * items. An offline record only goes if the server has no newer one: its `LastPlayedDate` against when the user watched.
 * That is how a position set on another device while this one was offline is not overwritten. Finished items are marked
 * watched the ordinary way (which counts a play); a part-watched one sets the position and the date it was watched.
 */
class OfflineSync(
    private val pending: PendingReports,
    private val dao: DownloadDao,
    private val connection: ConnectionManager,
    private val changes: WatchChanges,
) {
    private val running = Mutex()

    /** Sends every offline record and then refreshes the downloaded items' watch state. True when nothing is left waiting to be sent. */
    suspend fun syncAll(): Boolean = running.withLock {
        var changed = false
        var allSent = true
        for (record in pending.progress()) {
            when (send(record)) {
                SyncOutcome.Retry -> allSent = false

                SyncOutcome.Sent -> {
                    pending.remove(record)
                    changed = true
                }

                SyncOutcome.ServerNewer, SyncOutcome.Dropped -> {
                    pending.remove(record)
                    changed = true
                }
            }
        }
        if (refresh()) changed = true
        if (changed) changes.changed()
        allSent
    }

    private suspend fun send(record: PendingProgress): SyncOutcome {
        val profile = record.profile
        val call = try {
            connection.asProfile(profile.serverId, profile.userId) { api ->
                when (val read = api.itemsById(record.itemId, profile.userId)) {
                    is ApiResult.Failure -> read

                    is ApiResult.Success -> {
                        val item = read.value.items.firstOrNull() ?: return@asProfile ApiResult.Success(SyncOutcome.Dropped)
                        val serverAt = item.userData?.lastPlayedDate?.toEpochMillisOrNull()
                        when {
                            serverAt != null && serverAt > record.watchedAt -> ApiResult.Success(SyncOutcome.ServerNewer)

                            record.played -> api.setPlayed(record.itemId, profile.userId, true).let { it.mapTo(SyncOutcome.Sent) }

                            else -> api.updateUserData(
                                profile.userId,
                                record.itemId,
                                UpdateUserItemDataDto(record.positionTicks, played = false, lastPlayedDate = Instant.fromEpochMilliseconds(record.watchedAt).toString()),
                            ).mapTo(SyncOutcome.Sent)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return SyncOutcome.Retry
        }
        return when (call) {
            ProfileCall.NoSession, ProfileCall.Unreachable -> SyncOutcome.Retry

            is ProfileCall.Answered -> when (val result = call.result) {
                is ApiResult.Success -> result.value

                is ApiResult.Failure -> when (result.error) {
                    ApiError.Network, ApiError.Unauthorized, is ApiError.Server -> SyncOutcome.Retry
                    else -> SyncOutcome.Dropped
                }
            }
        }
    }

    // Brings the watch state of every downloaded item up to what the server says, for the items nothing is waiting to be sent for:
    // once online the server is the source of truth, and the copy on the device is only its fallback. Best effort.
    private suspend fun refresh(): Boolean {
        var changed = false
        val waiting = pending.progress().map { it.profile to it.itemId }.toSet()
        val byProfile = dao.inStates(listOf(DOWNLOAD_DONE)).groupBy { ProfileRef(it.serverId, it.userId) }

        for ((profile, rows) in byProfile) {
            for (chunk in rows.map { it.itemId }.filter { profile to it !in waiting }.chunked(REFRESH_CHUNK)) {
                if (refreshChunk(profile, chunk)) changed = true
            }
        }

        return changed
    }

    private suspend fun refreshChunk(profile: ProfileRef, ids: List<String>): Boolean {
        var changed = false

        for (dto in fetchItems(profile, ids)) {
            if (refreshItem(profile, dto)) changed = true
        }

        return changed
    }

    // Nothing when the server can't be reached or doesn't answer: the next sync tries again
    private suspend fun fetchItems(profile: ProfileRef, ids: List<String>): List<BaseItemDto> {
        val call = try {
            connection.asProfile(profile.serverId, profile.userId) { it.itemsByIds(profile.userId, ids) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return emptyList()
        }

        return ((call as? ProfileCall.Answered)?.result as? ApiResult.Success)?.value?.items.orEmpty()
    }

    private suspend fun refreshItem(profile: ProfileRef, dto: BaseItemDto): Boolean {
        val data = dto.userData ?: return false
        val saved = dao.findItem(profile.serverId, profile.userId, dto.id) ?: return false

        val ticks = if (data.played) 0L else data.playbackPositionTicks
        val at = data.lastPlayedDate?.toEpochMillisOrNull() ?: saved.userDataAt
        if (saved.positionTicks == ticks && saved.played == data.played) return false

        dao.setUserData(profile.serverId, profile.userId, dto.id, ticks, data.played, at)
        return true
    }

    /** Syncs now and then whenever a connection comes back, on [scope], for as long as the app lives. Call once at start. */
    fun start(scope: CoroutineScope, network: NetworkMonitor) {
        scope.launch {
            network.state.map { it.isConnected }.distinctUntilChanged().filter { it }.collect {
                try {
                    syncAll()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Tried again at the next change, and by the scheduled work.
                }
            }
        }
    }

    private fun <T> ApiResult<T>.mapTo(outcome: SyncOutcome): ApiResult<SyncOutcome> = when (this) {
        is ApiResult.Success -> ApiResult.Success(outcome)
        is ApiResult.Failure -> this
    }

    private companion object {
        const val REFRESH_CHUNK = 40
    }
}

/** The server's date (`2026-10-20T18:00:00.0000000Z`) as epoch milliseconds; null when it can't be read. */
internal fun String.toEpochMillisOrNull(): Long? = try {
    Instant.parse(this).toEpochMilliseconds()
} catch (_: IllegalArgumentException) {
    null
}
