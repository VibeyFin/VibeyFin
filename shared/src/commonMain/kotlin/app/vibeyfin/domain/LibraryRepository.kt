package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.BaseItemDto
import app.vibeyfin.data.api.JellyfinApi
import app.vibeyfin.data.api.items
import app.vibeyfin.data.api.itemImageUrl
import app.vibeyfin.data.api.itemsById
import app.vibeyfin.data.api.map
import app.vibeyfin.data.api.userViews
import app.vibeyfin.data.api.genres
import app.vibeyfin.data.api.filteredItems
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.ItemPage
import app.vibeyfin.domain.model.MediaItem

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

    /** The genres to pick from, by name. */
    suspend fun genres(): ApiResult<List<FilterOption>> = call { api, userId ->
        api.genres(userId).map { result -> result.items.toOptions() }
    }

    /**
     * One page of the category page for [filter], across every library: Shows (series), Movies, New, or a
     * genre's movies and shows. New is by release year, newest first, then by premiere date within a
     * year; the rest are by name.
     */
    suspend fun filteredPage(filter: HomeFilter, startIndex: Int = 0, limit: Int = PAGE_SIZE): ApiResult<ItemPage> =
        call { api, userId ->
            val types = when (filter) {
                HomeFilter.Shows -> listOf("Series")
                HomeFilter.Movies -> listOf("Movie")
                else -> listOf("Movie", "Series")
            }
            val newest = filter == HomeFilter.New
            api.filteredItems(
                userId, types, (filter as? HomeFilter.Genre)?.id,
                sortBy = if (newest) "ProductionYear,PremiereDate" else "SortName",
                sortOrder = if (newest) "Descending" else "Ascending",
                startIndex = startIndex,
                limit = limit,
            ).map { result ->
                ItemPage(result.items.map { it.toMediaItem(api.baseUrl) }, result.totalRecordCount, result.startIndex)
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
private const val LANDSCAPE_WIDTH = 640
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
    val thumbTag = imageTags["Thumb"]
    val landscape = when {
        thumbTag != null -> "Thumb" to thumbTag
        backdropTag != null -> "Backdrop" to backdropTag
        posterTag != null -> "Primary" to posterTag
        else -> null
    }
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
        seriesId = seriesId,
        runtimeMs = runTimeTicks?.takeIf { it > 0 }?.let { it / TICKS_PER_MILLISECOND },
        // A finished item has no position to resume from, whatever the server last stored.
        resumePositionMs = userData?.takeIf { !it.played }?.let { it.playbackPositionTicks / TICKS_PER_MILLISECOND } ?: 0,
        played = userData?.played == true,
        landscapeUrl = landscape?.let { (type, tag) -> itemImageUrl(baseUrl, id, type, tag, LANDSCAPE_WIDTH) },
        posterBlurHash = posterTag?.let { blurHash("Primary", it) },
        backdropBlurHash = backdropTag?.let { blurHash("Backdrop", it) },
        landscapeBlurHash = landscape?.let { (type, tag) -> blurHash(type, tag) },
        videoFormat = mediaStreams.firstOrNull { it.type == "Video" }?.toVideoFormat(),
    )
}

/** The BlurHash the server sent for the image of [type] with [tag]; the tag keeps it from belonging to an older picture. */
private fun BaseItemDto.blurHash(type: String, tag: String): String? =
    imageBlurHashes[type]?.get(tag)?.takeIf { it.isNotBlank() }

private fun List<BaseItemDto>.toOptions(): List<FilterOption> =
    mapNotNull { dto -> dto.name?.takeIf { it.isNotBlank() }?.let { FilterOption(dto.id, it) } }
        .sortedBy { it.name.lowercase() }
