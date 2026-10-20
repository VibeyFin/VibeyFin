package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.api.ApiError
import com.liamgleeson.vibeyfin.data.api.ApiResult
import com.liamgleeson.vibeyfin.data.api.ItemsResultDto
import com.liamgleeson.vibeyfin.data.api.JellyfinApi
import com.liamgleeson.vibeyfin.data.api.nextUp
import com.liamgleeson.vibeyfin.data.api.resumeItems
import com.liamgleeson.vibeyfin.domain.model.MediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * What the signed-in profile is in the middle of watching, and what to watch next. Reads the current
 * session from [ConnectionManager] on every call, like [LibraryRepository]. With nobody signed in
 * every call is [ApiError.Unauthorized].
 */
class WatchRepository(private val connection: ConnectionManager) {
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
