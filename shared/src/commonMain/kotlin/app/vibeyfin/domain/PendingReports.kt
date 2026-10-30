package app.vibeyfin.domain

import app.vibeyfin.data.db.PENDING_PROGRESS
import app.vibeyfin.data.db.PENDING_STOPPED
import app.vibeyfin.data.db.PendingReportDao
import app.vibeyfin.data.db.PendingReportEntity
import app.vibeyfin.domain.model.ProfileRef
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * A play session the server may not know has ended: everything `Stopped` needs, and the profile
 * that has to send it, since only that profile's token can.
 */
data class PendingStop(
    val profile: ProfileRef,
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String,
    val positionTicks: Long,
    val failed: Boolean,
)

/**
 * What a profile did to an item while the server could not be reached: [positionTicks] is where they stopped, [played]
 * whether they finished it, and [watchedAt] (epoch milliseconds) when. It is sent once online, unless the server has a
 * newer record of the item.
 */
data class PendingProgress(
    val profile: ProfileRef,
    val itemId: String,
    val positionTicks: Long,
    val played: Boolean,
    val watchedAt: Long,
)

/**
 * The play sessions that have started and not yet been confirmed stopped, kept on disk so that a
 * crash or a killed process doesn't leave the server showing the item as playing. A session is
 * saved when it starts, saved again as its position moves, and removed once the server has
 * answered.
 */
class PendingReports(
    private val dao: PendingReportDao,
    private val clock: Clock,
) {
    /** Adds the session, or brings the saved one's position up to date. */
    suspend fun save(stop: PendingStop) {
        dao.save(
            PendingReportEntity(
                serverId = stop.profile.serverId,
                userId = stop.profile.userId,
                kind = PENDING_STOPPED,
                itemId = stop.itemId,
                mediaSourceId = stop.mediaSourceId,
                playSessionId = stop.playSessionId,
                positionTicks = stop.positionTicks,
                failed = stop.failed,
                updatedAt = clock.now().toEpochMilliseconds(),
            ),
        )
    }

    suspend fun remove(stop: PendingStop) {
        dao.delete(stop.profile.serverId, stop.profile.userId, PENDING_STOPPED, stop.playSessionId)
    }

    /** Oldest first. */
    suspend fun stops(): List<PendingStop> = dao.all(PENDING_STOPPED).map {
        PendingStop(
            profile = ProfileRef(it.serverId, it.userId),
            itemId = it.itemId,
            mediaSourceId = it.mediaSourceId,
            playSessionId = it.playSessionId,
            positionTicks = it.positionTicks,
            failed = it.failed,
        )
    }

    /** Keeps [progress] as the item's offline record, replacing an earlier one: the latest is the only one worth sending. */
    suspend fun saveProgress(progress: PendingProgress) {
        dao.replace(
            PendingReportEntity(
                serverId = progress.profile.serverId,
                userId = progress.profile.userId,
                kind = PENDING_PROGRESS,
                itemId = progress.itemId,
                mediaSourceId = "",
                playSessionId = "",
                positionTicks = progress.positionTicks,
                failed = false,
                updatedAt = clock.now().toEpochMilliseconds(),
                played = progress.played,
                watchedAt = progress.watchedAt,
            ),
        )
    }

    /** The offline record of [itemId] for [profile], if there is one. */
    suspend fun progress(profile: ProfileRef, itemId: String): PendingProgress? = dao.find(profile.serverId, profile.userId, PENDING_PROGRESS, itemId)?.toProgress()

    /** Oldest first. */
    suspend fun progress(): List<PendingProgress> = dao.all(PENDING_PROGRESS).map { it.toProgress() }

    suspend fun remove(progress: PendingProgress) {
        dao.deleteForItem(progress.profile.serverId, progress.profile.userId, PENDING_PROGRESS, progress.itemId)
    }

    private fun PendingReportEntity.toProgress() = PendingProgress(
        profile = ProfileRef(serverId, userId),
        itemId = itemId,
        positionTicks = positionTicks,
        played = played,
        watchedAt = watchedAt,
    )

    /** Forgets play sessions last heard of more than [age] ago. Offline progress is never discarded by age: it is waiting for a connection, not for a crash to be noticed. */
    suspend fun discardOlderThan(age: Duration) {
        dao.deleteOlderThan(PENDING_STOPPED, (clock.now() - age).toEpochMilliseconds())
    }
}
