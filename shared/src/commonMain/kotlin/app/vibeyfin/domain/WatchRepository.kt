package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.ItemsResultDto
import app.vibeyfin.data.api.JellyfinApi
import app.vibeyfin.data.api.UpdateUserItemDataDto
import app.vibeyfin.data.api.episodesAround
import app.vibeyfin.data.api.episodesFrom
import app.vibeyfin.data.api.map
import app.vibeyfin.data.api.nextUp
import app.vibeyfin.data.api.nextUpOf
import app.vibeyfin.data.api.resumeItems
import app.vibeyfin.data.api.setFavorite
import app.vibeyfin.data.api.setPlayed
import app.vibeyfin.data.api.updateUserData
import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.data.db.DismissedNextUpEntity
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * What the signed-in profile is in the middle of watching, and what to watch next. Reads the current
 * session from [ConnectionManager] on every call, like [LibraryRepository]. With nobody signed in
 * every call is [ApiError.Unauthorized].
 */
class WatchRepository(private val connection: ConnectionManager, private val changes: WatchChanges, private val accounts: AccountDao) {
    /**
     * The Continue watching row: what the user has started, newest first, then the next episode of
     * each show they follow ([mergeContinueWatching]). The two lists are asked for together. If one
     * can't be had the row has the other, and only when both fail does the row fail. A show whose episode the user removed is left
     * out of Next Up until Next Up names another of its episodes.
     */
    suspend fun continueWatching(): ApiResult<List<MediaItem>> = call { api, userId ->
        val serverId = serverId() ?: return@call ApiResult.Failure(ApiError.Unauthorized)
        val dismissed = accounts.dismissedNextUp(serverId, userId).associate { it.seriesId to it.episodeId }
        coroutineScope {
            val resume = async { api.resumeItems(userId, ROW_LIMIT) }
            // Hidden shows would otherwise use up places in the row.
            val next = async { api.nextUp(userId, ROW_LIMIT + dismissed.size) }
            val resumeItems = resume.await().toItems(api)
            val nextAnswer = next.await().toItems(api)
            if (nextAnswer is ApiResult.Success) {
                movedOnFromDismissed(nextAnswer.value, dismissed).forEach { accounts.forgetDismissedNextUp(serverId, userId, it) }
            }
            val nextItems = nextAnswer.map { it.withoutDismissed(dismissed) }
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

    /** The episode before [current], in the show's own order and across seasons, or null when [current] is the first one, is not an episode, or has no show. */
    suspend fun previousEpisode(current: MediaItem): ApiResult<MediaItem?> {
        val seriesId = current.seriesId
        if (current.kind != ItemKind.Episode || seriesId == null) return ApiResult.Success(null)
        return call { api, userId ->
            api.episodesAround(seriesId, current.id, userId).toItems(api).map { episodes ->
                val at = episodes.indexOfFirst { it.id == current.id }
                if (at < 1) null else episodes[at - 1]
            }
        }
    }

    /** Puts [itemId] on "My List" or takes it off. The page has already shown the change; if this fails it takes it back. */
    suspend fun setFavorite(itemId: String, favorite: Boolean): ApiResult<Unit> {
        val result = call { api, userId -> api.setFavorite(itemId, userId, favorite) }
        if (result is ApiResult.Success) changes.favoriteChanged(FavoriteChange(itemId, favorite))
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

    /**
     * "Remove from Continue watching" on a card in progress: it goes back to position 0 on the server, leaving its played state,
     * play count and last played date. An episode's show is then hidden from Next Up on this device at whatever Next Up names now,
     * or it would come straight back as a Next Up card.
     */
    suspend fun removeFromContinueWatching(itemId: String, seriesId: String?): ApiResult<Unit> {
        val result = call { api, userId ->
            val serverId = serverId() ?: return@call ApiResult.Failure(ApiError.Unauthorized)
            val cleared = api.setPosition(userId, itemId, ticks = 0)
            if (cleared is ApiResult.Success && seriesId != null) {
                val episodeId = (api.nextUpOf(userId, seriesId, resumable = false) as? ApiResult.Success)?.value?.items?.firstOrNull()?.id ?: itemId
                accounts.dismissNextUp(DismissedNextUpEntity(serverId, userId, seriesId, episodeId))
            }
            cleared
        }
        if (result is ApiResult.Success) changes.changed()
        return result
    }

    /** Undo of [removeFromContinueWatching]: the position goes back and the show is no longer hidden. */
    suspend fun restoreToContinueWatching(itemId: String, seriesId: String?, resumePositionMs: Long): ApiResult<Unit> {
        val result = call { api, userId ->
            val serverId = serverId() ?: return@call ApiResult.Failure(ApiError.Unauthorized)
            val restored = api.setPosition(userId, itemId, ticks = resumePositionMs * TICKS_PER_MILLISECOND)
            if (restored is ApiResult.Success && seriesId != null) accounts.forgetDismissedNextUp(serverId, userId, seriesId)
            restored
        }
        if (result is ApiResult.Success) changes.changed()
        return result
    }

    private suspend fun JellyfinApi.setPosition(userId: String, itemId: String, ticks: Long) = updateUserData(userId, itemId, UpdateUserItemDataDto(playbackPositionTicks = ticks))

    private fun ApiResult<ItemsResultDto>.toItems(api: JellyfinApi): ApiResult<List<MediaItem>> = when (this) {
        is ApiResult.Success -> ApiResult.Success(value.items.map { it.toMediaItem(api.baseUrl) })
        is ApiResult.Failure -> this
    }

    // A rejected token matters most: it ends the session, whatever else went wrong.
    private fun worseOf(first: ApiError, second: ApiError) = if (second == ApiError.Unauthorized && first != ApiError.Unauthorized) second else first

    private suspend fun <T> call(block: suspend (JellyfinApi, String) -> ApiResult<T>): ApiResult<T> = connection.asConnected(ApiResult.Failure(ApiError.Unauthorized)) { block(it.api, it.account.userId) }

    private fun serverId() = connection.asConnected<String?>(null) { it.server.id }

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

internal fun List<MediaItem>.withoutDismissed(dismissed: Map<String, String>): List<MediaItem> = filter { dismissed[it.seriesId] != it.id }

/** The shows hidden at an episode Next Up has since moved on from: they are back. */
internal fun movedOnFromDismissed(nextUp: List<MediaItem>, dismissed: Map<String, String>): List<String> = nextUp.mapNotNull { item ->
    item.seriesId?.takeIf { it in dismissed && dismissed[it] != item.id }
}
