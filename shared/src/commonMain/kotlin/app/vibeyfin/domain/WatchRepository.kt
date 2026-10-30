package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.ItemsResultDto
import app.vibeyfin.data.api.JellyfinApi
import app.vibeyfin.data.api.episodesFrom
import app.vibeyfin.data.api.map
import app.vibeyfin.data.api.nextUp
import app.vibeyfin.data.api.nextUpOf
import app.vibeyfin.data.api.resumeItems
import app.vibeyfin.data.api.setFavorite
import app.vibeyfin.data.api.setPlayed
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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
    suspend fun continueWatching(): ApiResult<List<MediaItem>> = call { api, userId ->
        coroutineScope {
            val resume = async { api.resumeItems(userId, ROW_LIMIT) }
            val next = async { api.nextUp(userId, ROW_LIMIT) }
            val resumeItems = resume.await().toItems(api)
            val nextItems = next.await().toItems(api)
            when {
                resumeItems is ApiResult.Success && nextItems is ApiResult.Success ->
                    ApiResult.Success(mergeContinueWatching(resumeItems.value, nextItems.value))

                resumeItems is ApiResult.Success -> ApiResult.Success(mergeContinueWatching(resumeItems.value, emptyList()))

                nextItems is ApiResult.Success -> ApiResult.Success(mergeContinueWatching(emptyList(), nextItems.value))

                else -> ApiResult.Failure(worseOf((resumeItems as ApiResult.Failure).error, (nextItems as ApiResult.Failure).error))
            }
        }
    }

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

    /** Puts [itemId] on "My List" or takes it off. The page has already shown the change; if this fails it takes it back. */
    suspend fun setFavorite(itemId: String, favorite: Boolean): ApiResult<Unit> {
        val result = call { api, userId -> api.setFavorite(itemId, userId, favorite) }
        // Home's My List row reads the favourites again.
        if (result is ApiResult.Success) changes.changed()
        return result
    }

    /**
     * The episode of [seriesId] to play next, a half-watched one included, or null when the show was never started or
     * is finished (the page then offers its first episode).
     */
    suspend fun nextUpOf(seriesId: String): ApiResult<MediaItem?> = call { api, userId ->
        api.nextUpOf(userId, seriesId).toItems(api).map { it.firstOrNull() }
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

    private fun ApiResult<ItemsResultDto>.toItems(api: JellyfinApi): ApiResult<List<MediaItem>> = when (this) {
        is ApiResult.Success -> ApiResult.Success(value.items.map { it.toMediaItem(api.baseUrl) })
        is ApiResult.Failure -> this
    }

    // A rejected token matters most: it ends the session, whatever else went wrong.
    private fun worseOf(first: ApiError, second: ApiError) = if (second == ApiError.Unauthorized && first != ApiError.Unauthorized) second else first

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
