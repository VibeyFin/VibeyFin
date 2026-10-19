package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.api.ApiError
import com.liamgleeson.vibeyfin.data.api.ApiResult
import com.liamgleeson.vibeyfin.data.api.BaseItemDto
import com.liamgleeson.vibeyfin.data.api.JellyfinApi
import com.liamgleeson.vibeyfin.data.api.items
import com.liamgleeson.vibeyfin.data.api.itemImageUrl
import com.liamgleeson.vibeyfin.data.api.itemsById
import com.liamgleeson.vibeyfin.data.api.map
import com.liamgleeson.vibeyfin.data.api.userViews
import com.liamgleeson.vibeyfin.domain.model.ItemKind
import com.liamgleeson.vibeyfin.domain.model.ItemPage
import com.liamgleeson.vibeyfin.domain.model.MediaItem

/**
 * What is on the server for the signed-in profile: its libraries, what is inside them, and one
 * item's metadata. Reads the current session from [ConnectionManager] on every call, so it follows
 * a switch of address or profile. With nobody signed in every call is [ApiError.Unauthorized].
 */
class LibraryRepository(private val connection: ConnectionManager) {
    suspend fun libraries(): ApiResult<List<MediaItem>> = call { api, userId ->
        api.userViews(userId).map { result -> result.items.map { it.toMediaItem(api.baseUrl) } }
    }

    /** One page of what is inside [parentId]: a library, folder, series or season. */
    suspend fun children(parentId: String, startIndex: Int = 0, limit: Int = PAGE_SIZE): ApiResult<ItemPage> =
        call { api, userId ->
            api.items(parentId, userId, startIndex, limit).map { result ->
                ItemPage(result.items.map { it.toMediaItem(api.baseUrl) }, result.totalRecordCount, result.startIndex)
            }
        }

    /** One item with its overview and genres. An id the server doesn't know is [ApiError.NotFound]. */
    suspend fun item(id: String): ApiResult<MediaItem> = call { api, userId ->
        when (val result = api.itemsById(id, userId)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> result.value.items.firstOrNull()
                ?.let { ApiResult.Success(it.toMediaItem(api.baseUrl)) }
                ?: ApiResult.Failure(ApiError.NotFound)
        }
    }

    private suspend fun <T> call(block: suspend (JellyfinApi, String) -> ApiResult<T>): ApiResult<T> {
        val connected = connection.state.value as? ConnectionState.Connected
            ?: return ApiResult.Failure(ApiError.Unauthorized)
        return block(connected.api, connected.account.userId)
    }

    companion object {
        const val PAGE_SIZE = 60
    }
}

private const val POSTER_WIDTH = 400
private const val BACKDROP_WIDTH = 1280
private const val TALL = 2f / 3f
private const val WIDE = 16f / 9f
private const val TICKS_PER_MINUTE = 600_000_000L

internal fun BaseItemDto.toMediaItem(baseUrl: String): MediaItem {
    val kind = when (type) {
        "CollectionFolder", "UserView" -> ItemKind.Library
        "Folder" -> ItemKind.Folder
        "Movie" -> ItemKind.Movie
        "Series" -> ItemKind.Series
        "Season" -> ItemKind.Season
        "Episode" -> ItemKind.Episode
        else -> ItemKind.Other
    }
    val posterTag = imageTags["Primary"]
    val backdropTag = backdropImageTags.firstOrNull()
    return MediaItem(
        id = id,
        title = name.orEmpty(),
        kind = kind,
        isFolder = isFolder,
        year = productionYear,
        runtimeMinutes = runTimeTicks?.takeIf { it > 0 }?.let { ((it + TICKS_PER_MINUTE / 2) / TICKS_PER_MINUTE).toInt().coerceAtLeast(1) },
        genres = genres,
        overview = overview?.takeIf { it.isNotBlank() },
        rating = communityRating,
        ageRating = officialRating?.takeIf { it.isNotBlank() },
        seriesName = seriesName,
        seasonNumber = parentIndexNumber,
        episodeNumber = indexNumber,
        posterAspectRatio = primaryImageAspectRatio?.toFloat()?.takeIf { it > 0f }
            ?: if (kind == ItemKind.Movie || kind == ItemKind.Series || kind == ItemKind.Season) TALL else WIDE,
        posterUrl = posterTag?.let { itemImageUrl(baseUrl, id, "Primary", it, POSTER_WIDTH) },
        backdropUrl = backdropTag?.let { itemImageUrl(baseUrl, id, "Backdrop", it, BACKDROP_WIDTH) },
    )
}
