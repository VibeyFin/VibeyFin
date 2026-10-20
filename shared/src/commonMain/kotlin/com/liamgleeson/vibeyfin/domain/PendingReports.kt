package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.db.PENDING_STOPPED
import com.liamgleeson.vibeyfin.data.db.PendingReportDao
import com.liamgleeson.vibeyfin.data.db.PendingReportEntity
import com.liamgleeson.vibeyfin.domain.model.ProfileRef
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

    /** Forgets sessions last heard of more than [age] ago. */
    suspend fun discardOlderThan(age: Duration) {
        dao.deleteOlderThan(PENDING_STOPPED, (clock.now() - age).toEpochMilliseconds())
    }
}
