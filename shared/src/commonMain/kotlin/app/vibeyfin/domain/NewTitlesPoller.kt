package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.BaseItemDto
import app.vibeyfin.data.api.JellyfinApi
import app.vibeyfin.data.api.filteredItems
import app.vibeyfin.data.api.nextUp
import app.vibeyfin.data.api.resumeItems
import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.data.db.NotifiedItemEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** A title to notify about: a new episode ([seriesName] is its show) or a new movie. */
data class NewTitle(val itemId: String, val title: String, val seriesName: String?, val isMovie: Boolean)

/** The [titles] new for one profile, as [NewTitlesPoller.pollAll] reports them. */
data class ProfileNewTitles(val serverId: String, val userId: String, val profileName: String, val titles: List<NewTitle>)

/**
 * Finds the episodes and movies added to the library since a profile was last told, for new-title notifications.
 * Jellyfin has no push, so the platforms call [poll] from their background work. Runs as the profile through
 * [ConnectionManager.asProfile], so it need not be the signed-in one; a token the server rejects locks the profile
 * there, and with no token left the next poll asks nothing.
 *
 * At most once: a title is marked as notified when [poll] returns it, before the platform posts anything. If posting
 * fails or the process dies first, that title is not reported again, so the platform's worker must not retry a poll
 * on the assumption that a failed post can be reported later. Marking is atomic, so two polls that overlap report a
 * title once.
 *
 * The first-poll rule applies whenever the profile has no notified rows: on the very first poll, after notifications
 * were switched off and on, or after the scope changed ([ProfileSettings] clears the rows then).
 */
class NewTitlesPoller(
    private val connection: ConnectionManager,
    private val dao: AccountDao,
    private val clock: Clock,
) {
    /**
     * Polls every saved profile that has notifications on and a session, each as itself and in turn, for background
     * work that runs while no profile is current. Returns only the profiles with something to report. A profile that
     * fails or turns out locked reports nothing and doesn't stop the others; [poll] applies the same rules again.
     */
    suspend fun pollAll(): List<ProfileNewTitles> = dao.getNotifiableProfiles().mapNotNull { profile ->
        val titles = try {
            poll(profile.serverId, profile.userId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        titles.takeIf { it.isNotEmpty() }?.let { ProfileNewTitles(profile.serverId, profile.userId, profile.name, it) }
    }

    /**
     * The titles that are new for the profile, each reported once. Empty, without asking the server, when the
     * profile's notifications are off, it is a kids profile, or it has no session (signed out or locked).
     *
     * A title counts when it was added in the last [MAX_AGE]. On a first poll only what was added in the last
     * [FIRST_POLL_AGE] is reported, and the rest is marked as known, so switching notifications on doesn't announce
     * the whole library.
     */
    suspend fun poll(serverId: String, userId: String): List<NewTitle> {
        if (dao.observeNotificationsEnabled(serverId, userId).first() != true) return emptyList()
        if (dao.observeKidsProfile(serverId, userId).first() == true) return emptyList()
        val everything = dao.observeNotificationScope(serverId, userId).first() == ProfileSettings.NOTIFY_EVERYTHING

        val now = clock.now()
        val known = now - MAX_AGE
        val call = connection.asProfile(serverId, userId) { api ->
            val added = when (val r = addedSince(api, userId, known)) {
                is ApiResult.Success -> r.value
                is ApiResult.Failure -> return@asProfile r
            }
            if (everything) return@asProfile ApiResult.Success(Listing(added, followed = emptySet()))
            // Followed shows: in Continue watching (started, or next up) or on My List.
            val resume = when (val r = api.resumeItems(userId, FOLLOW_LIMIT)) {
                is ApiResult.Success -> r.value.items
                is ApiResult.Failure -> return@asProfile r
            }
            val next = when (val r = api.nextUp(userId, FOLLOW_LIMIT)) {
                is ApiResult.Success -> r.value.items
                is ApiResult.Failure -> return@asProfile r
            }
            val myList = when (
                val r = api.filteredItems(
                    userId, types = listOf("Series"), genreId = null, sortBy = "SortName", sortOrder = "Ascending",
                    startIndex = 0, limit = FOLLOW_LIMIT, favoritesOnly = true,
                )
            ) {
                is ApiResult.Success -> r.value.items
                is ApiResult.Failure -> return@asProfile r
            }
            val followed = (resume + next).mapNotNullTo(mutableSetOf()) { it.seriesId } + myList.map { it.id }
            ApiResult.Success(Listing(added, followed))
        }
        val listing = ((call as? ProfileCall.Answered)?.result as? ApiResult.Success)?.value ?: return emptyList()

        val firstPoll = !dao.hasNotified(serverId, userId)
        val fresh = now - if (firstPoll) FIRST_POLL_AGE else MAX_AGE
        val reported = mutableListOf<NewTitle>()
        for (item in listing.added) {
            val isMovie = item.type == "Movie"
            if (!isMovie && item.type != "Episode") continue
            if (!isMovie && !everything && item.seriesId !in listing.followed) continue
            val added = item.addedAt() ?: continue
            if (added < known) continue
            // -1: already notified, by an earlier poll or one running alongside.
            if (dao.insertNotified(NotifiedItemEntity(serverId, userId, item.id, now.toEpochMilliseconds())) == -1L) continue
            if (added >= fresh) reported += NewTitle(item.id, item.name.orEmpty(), item.seriesName.takeUnless { isMovie }, isMovie)
        }
        dao.pruneNotified(serverId, userId, (now - PRUNE_AFTER).toEpochMilliseconds())
        return reported
    }

    /**
     * The movies and episodes added since [cutoff], newest first, one entry per episode: `GET /Items` by `DateCreated`,
     * a page at a time until a page reaches past [cutoff] or the list ends, and never more than [MAX_PAGES] pages.
     */
    private suspend fun addedSince(api: JellyfinApi, userId: String, cutoff: Instant): ApiResult<List<BaseItemDto>> {
        val all = mutableListOf<BaseItemDto>()
        repeat(MAX_PAGES) { page ->
            val items = when (
                val r = api.filteredItems(
                    userId, types = listOf("Movie", "Episode"), genreId = null, sortBy = "DateCreated", sortOrder = "Descending",
                    startIndex = page * PAGE_SIZE, limit = PAGE_SIZE, fields = "DateCreated",
                )
            ) {
                is ApiResult.Success -> r.value.items
                is ApiResult.Failure -> return r
            }
            all += items
            val oldest = items.lastOrNull()?.addedAt()
            if (items.size < PAGE_SIZE || (oldest != null && oldest < cutoff)) return ApiResult.Success(all)
        }
        return ApiResult.Success(all)
    }

    private fun BaseItemDto.addedAt(): Instant? = dateCreated?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private class Listing(val added: List<BaseItemDto>, val followed: Set<String>)

    private companion object {
        const val PAGE_SIZE = 100

        /** Bounds a runaway listing (a whole library imported at once) to this many pages of [PAGE_SIZE]. */
        const val MAX_PAGES = 10
        const val FOLLOW_LIMIT = 200

        /** Older additions are never reported; the background work may not run for days, on iOS especially. */
        val MAX_AGE = 14.days

        /** What a first poll reports; older additions are only marked as known. */
        val FIRST_POLL_AGE = 1.days

        /** Notified rows older than this are dropped; well past [MAX_AGE], so a dropped title can't notify again. */
        val PRUNE_AFTER = 90.days
    }
}
