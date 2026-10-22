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
import app.vibeyfin.data.api.collectionsOf
import app.vibeyfin.data.api.itemsOfPerson
import app.vibeyfin.data.api.specialFeatures
import app.vibeyfin.domain.model.Person
import app.vibeyfin.data.api.localTrailers
import app.vibeyfin.data.api.latestItems
import app.vibeyfin.data.api.seasonsOf
import app.vibeyfin.data.api.episodesOfSeason
import app.vibeyfin.data.api.similarItems
import app.vibeyfin.data.api.filteredItems
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.ItemPage
import app.vibeyfin.domain.model.CastMember
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

    /** What the server thinks is like [id], at most [limit]: "More like this". */
    suspend fun similar(id: String, limit: Int = SIMILAR_LIMIT): ApiResult<List<MediaItem>> = call { api, userId ->
        api.similarItems(userId, id, limit).map { result -> result.items.map { it.toMediaItem(api.baseUrl) } }
    }

    /** The trailer the server itself holds for [id], or null when it has none. */
    suspend fun localTrailer(id: String): ApiResult<MediaItem?> = call { api, userId ->
        api.localTrailers(userId, id).map { items -> items.firstOrNull()?.toMediaItem(api.baseUrl) }
    }

    /** [id]'s own page: a cast member's biography and picture. A person the server doesn't know is [ApiError.NotFound]. */
    suspend fun person(id: String): ApiResult<Person> = call { api, userId ->
        when (val result = api.itemsById(id, userId)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> result.value.items.firstOrNull()
                ?.let { ApiResult.Success(it.toPerson(api.baseUrl)) }
                ?: ApiResult.Failure(ApiError.NotFound)
        }
    }

    /** The movies and shows in the library that [personId] appears in, newest first. */
    suspend fun appearancesOf(personId: String): ApiResult<List<MediaItem>> = call { api, userId ->
        api.itemsOfPerson(userId, personId).map { result -> result.items.map { it.toMediaItem(api.baseUrl) } }
    }

    /** Behind the scenes and extras of [id]; each is a video that plays from its own page. */
    suspend fun specialFeatures(id: String): ApiResult<List<MediaItem>> = call { api, userId ->
        api.specialFeatures(userId, id).map { items -> items.map { it.toMediaItem(api.baseUrl) } }
    }

    /** The collections (box sets) [id] is part of, each one a folder that opens its members. */
    suspend fun collectionsOf(id: String): ApiResult<List<MediaItem>> = call { api, userId ->
        api.collectionsOf(userId, id).map { result -> result.items.map { it.toMediaItem(api.baseUrl) } }
    }

    /** A show's seasons in the server's order, Specials included. */
    suspend fun seasons(seriesId: String): ApiResult<List<MediaItem>> = call { api, userId ->
        api.seasonsOf(userId, seriesId).map { result -> result.items.map { it.toMediaItem(api.baseUrl) } }
    }

    /** The episodes of one season, in order, with their synopsis. */
    suspend fun episodes(seriesId: String, seasonId: String): ApiResult<List<MediaItem>> = call { api, userId ->
        api.episodesOfSeason(userId, seriesId, seasonId).map { result -> result.items.map { it.toMediaItem(api.baseUrl) } }
    }

    /** What was added to [libraryId] most recently, at most [limit], episodes grouped under their show. */
    suspend fun latest(libraryId: String, limit: Int = ROW_LIMIT): ApiResult<List<MediaItem>> = call { api, userId ->
        api.latestItems(userId, libraryId, limit).map { items ->
            // A new episode or season is shown as its show, with the show's own poster, once however many are new.
            items.map { it.asSeries(api.baseUrl) ?: it.toMediaItem(api.baseUrl) }.distinctBy { it.id }
        }
    }

    /**
     * The genres to pick from, by name, with how many titles each holds. On the Movies page only movies are counted, on
     * Shows only shows, and everywhere else (Home's Categories, New) both; a genre with none of the page's kind is left out.
     */
    suspend fun genres(filter: HomeFilter? = null): ApiResult<List<FilterOption>> = call { api, userId ->
        val kind = when (filter) {
            HomeFilter.Movies -> GenreKind.Movies
            HomeFilter.Shows -> GenreKind.Shows
            else -> GenreKind.Both
        }
        api.genres(userId, kind.types).map { result -> result.items.toOptions(kind) }
    }

    /**
     * One page of the category page for [filter], across every library: Shows (series), Movies, New, or a genre's
     * movies and shows, in [sort] order, optionally narrowed to a [genreId] (on a page that is not itself a genre).
     * Name is A to Z, Added is what reached the server most recently, Rating is best first.
     */
    suspend fun filteredPage(
        filter: HomeFilter,
        sort: PageSort = filter.defaultSort,
        genreId: String? = null,
        startIndex: Int = 0,
        limit: Int = PAGE_SIZE,
    ): ApiResult<ItemPage> = call { api, userId ->
        val types = when (filter) {
            HomeFilter.Shows -> listOf("Series")
            HomeFilter.Movies -> listOf("Movie")
            else -> listOf("Movie", "Series")
        }
        val (sortBy, sortOrder) = when (sort) {
            PageSort.Name -> "SortName" to "Ascending"
            PageSort.Added -> "DateCreated" to "Descending"
            PageSort.Rating -> "CommunityRating,SortName" to "Descending"
        }
        api.filteredItems(
            userId, types, (filter as? HomeFilter.Genre)?.id ?: genreId,
            sortBy = sortBy, sortOrder = sortOrder, startIndex = startIndex, limit = limit,
            favoritesOnly = filter == HomeFilter.MyList,
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

        /** A Home row holds at most this many items (`guardrails.md`). */
        const val ROW_LIMIT = 20

        /** "More like this" holds this many posters. */
        const val SIMILAR_LIMIT = 12
    }
}

private const val POSTER_WIDTH = 400
private const val BACKDROP_WIDTH = 1280
private const val LANDSCAPE_WIDTH = 640
private const val LOGO_WIDTH = 640
private const val CAST_WIDTH = 200
private const val TALL = 2f / 3f
private const val WIDE = 16f / 9f
private const val TICKS_PER_MINUTE = 600_000_000L

private fun BaseItemDto.toPerson(baseUrl: String): Person {
    val tag = imageTags["Primary"]
    return Person(
        id = id,
        name = name.orEmpty(),
        overview = overview?.takeIf { it.isNotBlank() },
        bornYear = premiereDate?.take(4)?.toIntOrNull(),
        birthplace = productionLocations.firstOrNull()?.takeIf { it.isNotBlank() },
        imageUrl = tag?.let { itemImageUrl(baseUrl, id, "Primary", it, POSTER_WIDTH) },
        imageBlurHash = tag?.let { blurHash("Primary", it) },
    )
}

internal fun BaseItemDto.toMediaItem(baseUrl: String): MediaItem {
    val kind = when (type) {
        "CollectionFolder", "UserView" -> ItemKind.Library
        "Folder" -> ItemKind.Folder
        "Movie" -> ItemKind.Movie
        "Series" -> ItemKind.Series
        "Season" -> ItemKind.Season
        "Episode" -> ItemKind.Episode
        "BoxSet" -> ItemKind.Collection
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
        // A collection is always a tall poster, whatever shape its picture is (it is cropped to fit).
        posterAspectRatio = if (kind == ItemKind.Collection) TALL else primaryImageAspectRatio?.toFloat()?.takeIf { it > 0f }
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
        collectionType = collectionType?.takeIf { it.isNotBlank() },
        logoUrl = imageTags["Logo"]?.let { itemImageUrl(baseUrl, id, "Logo", it, LOGO_WIDTH) },
        cast = people.filter { it.type == "Actor" || it.type == "GuestStar" }.mapNotNull { person ->
            person.name?.takeIf { it.isNotBlank() }?.let { name ->
                CastMember(person.id, name, person.role?.takeIf { it.isNotBlank() }, person.primaryImageTag?.let { itemImageUrl(baseUrl, person.id, "Primary", it, CAST_WIDTH) })
            }
        },
        seasonCount = if (kind == ItemKind.Series) childCount else null,
        isFavorite = userData?.isFavorite == true,
        localTrailerCount = localTrailerCount ?: 0,
        remoteTrailerUrl = remoteTrailers.firstNotNullOfOrNull { trailer -> trailer.url?.takeIf { it.startsWith("https://") || it.startsWith("http://") } },
    )
}

/** The BlurHash the server sent for the image of [type] with [tag]; the tag keeps it from belonging to an older picture. */
private fun BaseItemDto.blurHash(type: String, tag: String): String? =
    imageBlurHashes[type]?.get(tag)?.takeIf { it.isNotBlank() }

/**
 * An episode or a season as the show it belongs to: the show's id and name, its poster, a folder that opens its
 * seasons. Null for anything else, or when the server did not say which show it is.
 */
private fun BaseItemDto.asSeries(baseUrl: String): MediaItem? {
    if ((type != "Episode" && type != "Season") || seriesId == null) return null
    return toMediaItem(baseUrl).copy(
        id = seriesId,
        title = seriesName ?: name.orEmpty(),
        kind = ItemKind.Series,
        isFolder = true,
        // The show's own pictures: this item's picture is a still or a season cover.
        posterUrl = seriesPrimaryImageTag?.let { itemImageUrl(baseUrl, seriesId, "Primary", it, POSTER_WIDTH) },
        posterAspectRatio = TALL,
        posterBlurHash = null,
        backdropUrl = null,
        backdropBlurHash = null,
        landscapeUrl = null,
        landscapeBlurHash = null,
        seriesName = null,
        seasonNumber = null,
        episodeNumber = null,
        seriesId = null,
        runtimeMs = null,
        runtimeMinutes = null,
        resumePositionMs = 0,
        played = false,
    )
}

/** Which titles a genre's count is made of. */
private enum class GenreKind(val types: List<String>) {
    Movies(listOf("Movie")),
    Shows(listOf("Series")),
    Both(listOf("Movie", "Series")),
}

private fun List<BaseItemDto>.toOptions(kind: GenreKind): List<FilterOption> =
    mapNotNull { dto ->
        val name = dto.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        // When the server sent no counts at all the genre is listed without a number.
        val known = dto.movieCount != null || dto.seriesCount != null
        val count = when (kind) {
            GenreKind.Movies -> dto.movieCount ?: 0
            GenreKind.Shows -> dto.seriesCount ?: 0
            GenreKind.Both -> (dto.movieCount ?: 0) + (dto.seriesCount ?: 0)
        }
        if (known && count == 0) null else FilterOption(dto.id, name, count.takeIf { known })
    }.sortedBy { it.name.lowercase() }
