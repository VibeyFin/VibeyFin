package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.ItemsResultDto
import app.vibeyfin.data.api.JellyfinApi
import app.vibeyfin.data.api.episodesFrom
import app.vibeyfin.data.api.itemsMatching
import app.vibeyfin.data.api.nextUp
import app.vibeyfin.data.api.resumeItems
import app.vibeyfin.data.api.setPlayed
import app.vibeyfin.data.api.map
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.time.Instant

/**
 * What the signed-in profile is in the middle of watching, and what to watch next. Reads the current
 * session from [ConnectionManager] on every call, like [LibraryRepository]. With nobody signed in
 * every call is [ApiError.Unauthorized].
 */
class WatchRepository(private val connection: ConnectionManager, private val changes: WatchChanges) {
    /**
     * The Continue watching row: what the user has started, newest first, then the next episode of
     * each show they follow ([mergeContinueWatching]). The two lists are asked for together. If one
     * can't be had the row has the other, and only when both fail does the row fail.
     */
    suspend fun continueWatching(filter: HomeFilter? = null, now: Instant? = null): ApiResult<List<MediaItem>> = call { api, userId ->
        coroutineScope {
            val resume = async { api.resumeItems(userId, ROW_LIMIT) }
            val next = async { api.nextUp(userId, ROW_LIMIT) }
            val resumeItems = resume.await().toItems(api)
            val nextItems = next.await().toItems(api)
            when {
                resumeItems is ApiResult.Success && nextItems is ApiResult.Success ->
                    narrow(api, userId, resumeItems.value, nextItems.value, filter, now)
                resumeItems is ApiResult.Success -> narrow(api, userId, resumeItems.value, emptyList(), filter, now)
                nextItems is ApiResult.Success -> narrow(api, userId, emptyList(), nextItems.value, filter, now)
                else -> ApiResult.Failure(worseOf((resumeItems as ApiResult.Failure).error, (nextItems as ApiResult.Failure).error))
            }
        }
    }

    /**
     * Limits the two lists to [filter] before they are merged, so the row is still full when enough match. Type and
     * date are read from the items; a genre or a studio is asked of the server about the items' own ids (a show's,
     * for an episode), because Resume and Next Up cannot be filtered by them.
     */
    private suspend fun narrow(
        api: JellyfinApi,
        userId: String,
        inProgress: List<MediaItem>,
        nextUp: List<MediaItem>,
        filter: HomeFilter?,
        now: Instant?,
    ): ApiResult<List<MediaItem>> {
        if (filter == null) return ApiResult.Success(mergeContinueWatching(inProgress, nextUp))
        // Genre and studio are the server's call, about each item's subject; the rest is read from the items.
        val matching: Set<String>? = if (filter is HomeFilter.Genre || filter is HomeFilter.Studio) {
            val subjects = (inProgress + nextUp).map { it.subjectId() }.distinct()
            if (subjects.isEmpty()) return ApiResult.Success(emptyList())
            when (val result = api.itemsMatching(userId, subjects, (filter as? HomeFilter.Genre)?.id, (filter as? HomeFilter.Studio)?.id)) {
                is ApiResult.Failure -> return result
                is ApiResult.Success -> result.value.items.mapTo(mutableSetOf()) { it.id }
            }
        } else {
            null
        }
        val keep: (MediaItem) -> Boolean = { item ->
            when (filter) {
                HomeFilter.Shows -> item.kind == ItemKind.Episode || item.kind == ItemKind.Series
                HomeFilter.Movies -> item.kind == ItemKind.Movie
                HomeFilter.New -> now != null && item.isNew(now)
                is HomeFilter.Genre, is HomeFilter.Studio -> item.subjectId() in matching.orEmpty()
            }
        }
        return ApiResult.Success(mergeContinueWatching(inProgress.filter(keep), nextUp.filter(keep)))
    }

    /** What an item's genres and studios belong to: the show for an episode, the item itself otherwise. */
    private fun MediaItem.subjectId(): String = if (kind == ItemKind.Episode) seriesId ?: id else id

    /**
     * The episode after [current], in the show's own order and across seasons, or null when [current] is
     * the last one, is not an episode, or has no show. Asked of the show's episode list rather than of
     * Next Up, which can still name [current] while its `Stopped` is on its way to the server.
     */
    suspend fun nextEpisode(current: MediaItem): ApiResult<MediaItem?> {
        val seriesId = current.seriesId
        if (current.kind != ItemKind.Episode || seriesId == null) return ApiResult.Success(null)
        return call { api, userId ->
            api.episodesFrom(seriesId, current.id, userId, limit = 2).toItems(api).map { episodes ->
                val at = episodes.indexOfFirst { it.id == current.id }
                if (at < 0) null else episodes.getOrNull(at + 1)
            }
        }
    }

    /**
     * Marks [itemId] watched or unwatched on the server. The screens have already shown the change; if
     * this fails they take it back. On success [WatchChanges] says so, so what shows watch state reloads.
     */
    suspend fun setPlayed(itemId: String, played: Boolean): ApiResult<Unit> {
        val result = call { api, userId -> api.setPlayed(itemId, userId, played) }
        if (result is ApiResult.Success) changes.changed()
        return result
    }

    private fun ApiResult<ItemsResultDto>.toItems(api: JellyfinApi): ApiResult<List<MediaItem>> =
        when (this) {
            is ApiResult.Success -> ApiResult.Success(value.items.map { it.toMediaItem(api.baseUrl) })
            is ApiResult.Failure -> this
        }

    /** A rejected token matters most: it ends the session, whatever else went wrong. */
    private fun worseOf(first: ApiError, second: ApiError) =
        if (second == ApiError.Unauthorized && first != ApiError.Unauthorized) second else first

    private suspend fun <T> call(block: suspend (JellyfinApi, String) -> ApiResult<T>): ApiResult<T> {
        val connected = connection.state.value as? ConnectionState.Connected
            ?: return ApiResult.Failure(ApiError.Unauthorized)
        return block(connected.api, connected.account.userId)
    }

    companion object {
        /** Each row holds at most this many cards (`guardrails.md`). */
        const val ROW_LIMIT = 20
    }
}

/**
 * Puts what is in progress first, in the order the server gave it (newest first), then the next
 * episode of each show. A show that already has something in progress appears once, as that item, so
 * its next episode is left out; so is anything already listed. Holds at most [limit] items.
 */
internal fun mergeContinueWatching(
    inProgress: List<MediaItem>,
    nextUp: List<MediaItem>,
    limit: Int = WatchRepository.ROW_LIMIT,
): List<MediaItem> {
    val listed = inProgress.mapTo(mutableSetOf()) { it.id }
    val shows = inProgress.mapNotNullTo(mutableSetOf()) { it.seriesId }
    val next = nextUp.filter { item ->
        val show = item.seriesId
        // Adding to `shows` as we go keeps a second next-up episode of the same show out too.
        item.id !in listed && (show == null || shows.add(show))
    }
    return (inProgress + next).take(limit)
}
