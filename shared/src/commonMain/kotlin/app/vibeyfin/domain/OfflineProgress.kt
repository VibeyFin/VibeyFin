package app.vibeyfin.domain

import app.vibeyfin.data.db.DownloadDao
import app.vibeyfin.domain.model.ProfileRef
import kotlin.time.Clock

/** Jellyfin's own default thresholds: a stop before 5 % keeps no place, from 90 % the item counts as watched. */
internal const val MIN_RESUME_FRACTION = 0.05
internal const val PLAYED_FROM_FRACTION = 0.90

/**
 * Keeps what a profile did to a downloaded item while the server was not asked: where it stopped, and whether it finished. The
 * record goes two places: the download's saved copy, so the screens show it at once, and the pending reports, which
 * [OfflineSync] sends to the server when it can. It applies Jellyfin's own rules, so the server ends up where playing the item
 * through the server would have left it.
 */
class OfflineProgress(
    private val dao: DownloadDao,
    private val pending: PendingReports,
    private val clock: Clock,
) {
    /** Records that [profile] stopped [itemId] at [positionMs] of [runtimeMs] (null when the length is not known). */
    suspend fun record(profile: ProfileRef, itemId: String, positionMs: Long, runtimeMs: Long?): PendingProgress {
        val known = runtimeMs?.takeIf { it > 0 }
        val played = known != null && positionMs >= known * PLAYED_FROM_FRACTION
        val keepsPlace = !played && (known == null || positionMs >= known * MIN_RESUME_FRACTION)
        val ticks = if (keepsPlace) positionMs * TICKS_PER_MILLISECOND else 0L
        val now = clock.now().toEpochMilliseconds()
        val record = PendingProgress(profile, itemId, ticks, played, now)
        pending.saveProgress(record)
        dao.setUserData(profile.serverId, profile.userId, itemId, ticks, played, now)
        return record
    }
}
