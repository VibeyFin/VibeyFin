package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.BaseItemDto
import app.vibeyfin.data.api.ItemsResultDto
import app.vibeyfin.data.api.JellyfinApi
import app.vibeyfin.data.api.NameValueDto
import app.vibeyfin.data.api.collectionsOf
import app.vibeyfin.data.api.episodesOfSeason
import app.vibeyfin.data.api.filteredItems
import app.vibeyfin.data.api.genres
import app.vibeyfin.data.api.itemImageUrl
import app.vibeyfin.data.api.items
import app.vibeyfin.data.api.itemsById
import app.vibeyfin.data.api.itemsOfPerson
import app.vibeyfin.data.api.latestItems
import app.vibeyfin.data.api.legacyFilters
import app.vibeyfin.data.api.localTrailers
import app.vibeyfin.data.api.map
import app.vibeyfin.data.api.persons
import app.vibeyfin.data.api.queryFilters
import app.vibeyfin.data.api.searchHints
import app.vibeyfin.data.api.seasonsOf
import app.vibeyfin.data.api.similarItems
import app.vibeyfin.data.api.specialFeatures
import app.vibeyfin.data.api.studios
import app.vibeyfin.data.api.userViews
import app.vibeyfin.domain.model.CastMember
import app.vibeyfin.domain.model.FilteredPage
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.ItemPage
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.domain.model.Person
import app.vibeyfin.domain.model.SearchKind
import app.vibeyfin.domain.model.SearchResult
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What is on the server for the signed-in profile: its libraries, what is inside them, and one
 * item's metadata. Reads the current session from [ConnectionManager] on every call, so it follows
 * a switch of address or profile. With nobody signed in every call is [ApiError.Unauthorized].
 */
class LibraryRepository(private val connection: ConnectionManager, private val clock: Clock) {
    suspend fun libraries(): ApiResult<List<MediaItem>> = call { api, userId ->
        api.userViews(userId).map { result -> result.items.map { it.toMediaItem(api.baseUrl) } }
    }

    /** One page of what is inside [parentId]: a library, folder, series or season. */
    suspend fun children(parentId: String, startIndex: Int = 0, limit: Int = PAGE_SIZE): ApiResult<ItemPage> = call { api, userId ->
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

    /** What the server thinks is like [id], at most [limit]: "More like this"; without what was watched when [hideWatched]. */
    suspend fun similar(id: String, limit: Int = SIMILAR_LIMIT, hideWatched: Boolean = false): ApiResult<List<MediaItem>> = call { api, userId ->
        // Similar has no played filter on the server, so more are asked for and the watched ones dropped here.
        val asked = if (hideWatched) limit * SIMILAR_OVERFETCH else limit
        api.similarItems(userId, id, asked).map { result ->
            result.items.map { it.toMediaItem(api.baseUrl) }.filterNot { hideWatched && it.played }.take(limit)
        }
    }

    /** The trailer the server itself holds for [id], or null when it has none. */
    suspend fun localTrailer(id: String): ApiResult<MediaItem?> = call { api, userId ->
        api.localTrailers(userId, id).map { items -> items.firstOrNull()?.toMediaItem(api.baseUrl) }
    }

    /** Movies, shows, episodes, people and collections whose name matches [term], best match first. */
    suspend fun search(term: String): ApiResult<List<SearchResult>> = call { api, userId ->
        api.searchHints(userId, term, SEARCH_LIMIT).map { result ->
            result.hints.mapNotNull { hint ->
                val kind = when (hint.type) {
                    "Movie" -> SearchKind.Movie
                    "Series" -> SearchKind.Show
                    "Episode" -> SearchKind.Episode
                    "Person" -> SearchKind.Person
                    "BoxSet" -> SearchKind.Collection
                    else -> return@mapNotNull null
                }
                SearchResult(
                    id = hint.id,
                    title = hint.name.orEmpty(),
                    kind = kind,
                    subtitle = when (kind) {
                        SearchKind.Episode -> listOfNotNull(hint.seriesName, episodeCode(hint.seasonNumber, hint.episodeNumber)).joinToString(" \u00B7 ").ifBlank { null }
                        SearchKind.Person -> null
                        else -> hint.productionYear?.toString()
                    },
                    imageUrl = hint.primaryImageTag?.let { itemImageUrl(api.baseUrl, hint.id, "Primary", it, POSTER_WIDTH) },
                    backdropUrl = hint.backdropImageTag?.let { itemImageUrl(api.baseUrl, hint.backdropImageItemId ?: hint.id, "Backdrop", it, BACKDROP_WIDTH) },
                    aspectRatio = hint.primaryImageAspectRatio?.toFloat()?.takeIf { it > 0f }
                        ?: if (kind == SearchKind.Episode) WIDE else TALL,
                )
            }
        }
    }

    /** People whose name matches [term], for the Library page's People filter; nothing for a blank term. */
    suspend fun searchPeople(term: String): ApiResult<List<FilterOption>> = call { api, userId ->
        if (term.isBlank()) return@call ApiResult.Success(emptyList())
        api.persons(userId, term.trim(), PEOPLE_LIMIT).map { result -> result.items.map { FilterOption(it.id, it.name.orEmpty()) } }
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

    /** What was added to [libraryId] most recently, at most [limit], episodes grouped under their show; without what was watched when [hideWatched]. */
    suspend fun latest(libraryId: String, limit: Int = ROW_LIMIT, hideWatched: Boolean = false): ApiResult<List<MediaItem>> = call { api, userId ->
        api.latestItems(userId, libraryId, limit, unplayedOnly = hideWatched).map { items ->
            // A new episode or season is shown as its show, with the show's own poster, once however many are new.
            items.map { it.asSeries(api.baseUrl) ?: it.toMediaItem(api.baseUrl) }.distinctBy { it.id }
        }
    }

    /**
     * The genres to pick from, by name, with how many titles each holds. On the Movies page only movies are counted, on
     * Shows only shows, and everywhere else (Home's Categories, New) both; a genre with none of the page's kind is left out.
     */
    suspend fun genres(filter: HomeFilter? = null, type: TypeFilter? = null): ApiResult<List<FilterOption>> = call { api, userId ->
        val kind = genreKind(filter, type)
        val all = api.genres(userId, kind.types).map { result -> result.items.toOptions(kind) }
        if (filter != HomeFilter.MyList || all !is ApiResult.Success) return@call all
        // On My List the numbers are the titles on the list, not the whole library's: the server cannot count a genre among
        // favourites, so they are counted from the list itself.
        val favourites = api.filteredItems(userId, FilterSet(type = type).serverQuery(filter), startIndex = 0, limit = MY_LIST_GENRE_SCAN, fields = "Genres")
        when (favourites) {
            is ApiResult.Failure -> favourites

            is ApiResult.Success -> {
                val counts = favourites.value.items.flatMap { it.genres.distinct() }.groupingBy { it }.eachCount()
                ApiResult.Success(all.value.mapNotNull { option -> counts[option.name]?.let { option.copy(count = it) } })
            }
        }
    }

    /**
     * One page of [filter]'s category page, across every library, ordered and narrowed by [filters]. When some of them
     * can't be applied by the server ([FilterSet.needsDevicePass]) the server's pages are read and checked here, and the
     * total is only known once the whole list has been read.
     */
    suspend fun filteredPage(
        filter: HomeFilter,
        filters: FilterSet = FilterSet(),
        startIndex: Int = 0,
        limit: Int = PAGE_SIZE,
        /** Extra fields the server leaves out of a list unless asked, comma-separated (`Genres`). */
        fields: String? = null,
    ): ApiResult<FilteredPage> = call { api, userId ->
        val query = filters.serverQuery(filter)

        if (!filters.needsDevicePass) {
            api.filteredItems(userId, query, startIndex, limit, fields).map { it.toFilteredPage(api.baseUrl) }
        } else {
            val asked = (fields?.split(",").orEmpty() + filters.deviceFields()).distinct().joinToString(",")
            api.scanOnDevice(userId, filters, query, startIndex, limit, asked, clock.now())
        }
    }

    /** The studios and networks to pick from on [filter]'s page, with how many of its kind of title each holds. */
    suspend fun studios(filter: HomeFilter, filters: FilterSet): ApiResult<List<FilterOption>> = call { api, userId ->
        val kind = genreKind(filter, filters.type)
        api.studios(userId, kind.types, filters.library?.id).map { result -> result.items.toOptions(kind) }
    }

    /** The libraries of films and shows, to narrow a page to one. */
    suspend fun videoLibraries(): ApiResult<List<FilterOption>> = libraries().map { all ->
        all.filter { it.holdsVideo && it.collectionType != "boxsets" }.map { FilterOption(it.id, it.title) }
    }

    /**
     * The years, age ratings, tags and languages among [filter]'s titles, in [filters]' library or every one. The server
     * gives years and age ratings only per library, so those are read from each library and merged.
     */
    suspend fun filterValues(filter: HomeFilter, filters: FilterSet): ApiResult<FilterValues> = call { api, userId ->
        val types = filters.itemTypes(filter)
        val libraryIds = filters.library?.let { listOf(it.id) }
            ?: when (val all = videoLibraries()) {
                is ApiResult.Failure -> return@call all
                is ApiResult.Success -> all.value.map { it.id }
            }
        val named = when (val result = api.queryFilters(userId, types, filters.library?.id)) {
            is ApiResult.Failure -> return@call result
            is ApiResult.Success -> result.value
        }
        val perLibrary = libraryIds.map { id ->
            when (val result = api.legacyFilters(userId, types, id)) {
                is ApiResult.Failure -> return@call result
                is ApiResult.Success -> result.value
            }
        }
        val scores = (api.parentalRatings() as? ApiResult.Success)?.value.orEmpty()
            .mapNotNull { rating -> rating.name?.let { name -> rating.value?.let { name to it } } }
            .toMap()
        val ratings = perLibrary.flatMap { it.officialRatings }.filter { it.isNotBlank() }.distinct()

        ApiResult.Success(
            FilterValues(
                years = perLibrary.flatMap { it.years }.distinct().sortedDescending(),
                ageRatings = ratings.filter { it in scores }.sortedWith(compareBy({ scores[it] }, { it })),
                tags = named.tags.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() },
                audioLanguages = named.audioLanguages.toLanguageOptions(),
                subtitleLanguages = named.subtitleLanguages.toLanguageOptions(),
            ),
        )
    }

    private suspend fun <T> call(block: suspend (JellyfinApi, String) -> ApiResult<T>): ApiResult<T> = connection.asConnected(ApiResult.Failure(ApiError.Unauthorized)) { block(it.api, it.account.userId) }

    companion object {
        const val PAGE_SIZE = 60

        /** A search asks for this many matches in all, across every kind. */
        const val SEARCH_LIMIT = 40

        /** The People filter lists at most this many matches. */
        const val PEOPLE_LIMIT = 30

        /** A page read when some filters are checked on the device, and how many are read at most once one title matched. */
        const val SCAN_PAGE_SIZE = 100
        const val SCAN_PAGES = 5

        /** The most favourites read to count My List's genres. */
        const val MY_LIST_GENRE_SCAN = 500

        /** A Home row holds at most this many items (`guardrails.md`). */
        const val ROW_LIMIT = 20

        /** "More like this" holds this many posters. */
        const val SIMILAR_LIMIT = 12

        /** How many times [SIMILAR_LIMIT] is asked for when watched titles are left out of "More like this". */
        const val SIMILAR_OVERFETCH = 3
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
    val kind = itemKind()
    val posterTag = imageTags["Primary"]
    val backdropTag = backdropImageTags.firstOrNull()
    val landscape = landscapeImage(posterTag, backdropTag)

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
        premiereDate = premiereDate?.take(10)?.takeIf { it.length == 10 },
        seriesName = seriesName,
        seasonNumber = parentIndexNumber,
        episodeNumber = indexNumber,
        // A collection is always a tall poster, whatever shape its picture is (it is cropped to fit).
        posterAspectRatio = posterAspectRatio(kind),
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
        audioTracks = mediaStreams.filter { it.type == "Audio" }.map { it.toStreamTrack() },
        subtitleTracks = mediaStreams.filter { it.type == "Subtitle" }.map { it.toStreamTrack() },
        fileBytes = mediaSources.firstOrNull()?.size?.takeIf { it > 0 },
        collectionType = collectionType?.takeIf { it.isNotBlank() },
        logoUrl = imageTags["Logo"]?.let { itemImageUrl(baseUrl, id, "Logo", it, LOGO_WIDTH) },
        cast = castMembers(baseUrl),
        seasonCount = if (kind == ItemKind.Series) childCount else null,
        isFavorite = userData?.isFavorite == true,
        localTrailerCount = localTrailerCount ?: 0,
        remoteTrailerUrl = remoteTrailers.firstNotNullOfOrNull { trailer -> trailer.url?.takeIf { it.startsWith("https://") || it.startsWith("http://") } },
        tmdbId = providerIds.entries.firstOrNull { it.key.equals("Tmdb", ignoreCase = true) }?.value?.toIntOrNull(),
    )
}

private fun BaseItemDto.itemKind(): ItemKind = when (type) {
    "CollectionFolder", "UserView" -> ItemKind.Library
    "Folder" -> ItemKind.Folder
    "Movie" -> ItemKind.Movie
    "Series" -> ItemKind.Series
    "Season" -> ItemKind.Season
    "Episode" -> ItemKind.Episode
    "BoxSet" -> ItemKind.Collection
    else -> ItemKind.Other
}

// The picture for a wide card, as its image type and tag: a thumb, else a backdrop, else the poster
private fun BaseItemDto.landscapeImage(posterTag: String?, backdropTag: String?): Pair<String, String>? {
    val thumbTag = imageTags["Thumb"]

    return when {
        thumbTag != null -> "Thumb" to thumbTag
        backdropTag != null -> "Backdrop" to backdropTag
        posterTag != null -> "Primary" to posterTag
        else -> null
    }
}

private fun BaseItemDto.posterAspectRatio(kind: ItemKind): Float {
    if (kind == ItemKind.Collection) return TALL

    return primaryImageAspectRatio?.toFloat()?.takeIf { it > 0f }
        ?: if (kind == ItemKind.Movie || kind == ItemKind.Series || kind == ItemKind.Season) TALL else WIDE
}

private fun BaseItemDto.castMembers(baseUrl: String): List<CastMember> = people.filter { it.type == "Actor" || it.type == "GuestStar" }.mapNotNull { person ->
    person.name?.takeIf { it.isNotBlank() }?.let { name ->
        CastMember(person.id, name, person.role?.takeIf { it.isNotBlank() }, person.primaryImageTag?.let { itemImageUrl(baseUrl, person.id, "Primary", it, CAST_WIDTH) })
    }
}

// The BlurHash the server sent for the image of [type] with [tag]; the tag keeps it from belonging to an older picture.
private fun BaseItemDto.blurHash(type: String, tag: String): String? = imageBlurHashes[type]?.get(tag)?.takeIf { it.isNotBlank() }

// An episode or a season as the show it belongs to: the show's id and name, its poster, a folder that opens its
// seasons. Null for anything else, or when the server did not say which show it is.
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

// Reads the server's pages from [startIndex] and keeps what passes the device's filters, until [limit] are found or the
// list ends. Once something is found it stops after a few pages, so one call never reads the whole library twice.
private suspend fun JellyfinApi.scanOnDevice(
    userId: String,
    filters: FilterSet,
    query: Map<String, String>,
    startIndex: Int,
    limit: Int,
    fields: String,
    now: Instant,
): ApiResult<FilteredPage> {
    val found = mutableListOf<MediaItem>()
    var next = startIndex
    var pagesRead = 0
    var exhausted = false

    while (!exhausted && keepReading(found.size, limit, pagesRead)) {
        val page = when (val result = filteredItems(userId, query, next, LibraryRepository.SCAN_PAGE_SIZE, fields)) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value
        }
        pagesRead++
        for (dto in page.items) {
            next++
            if (filters.matchesOnDevice(dto, now)) found += dto.toMediaItem(baseUrl)
            if (found.size == limit) break
        }
        exhausted = page.items.isEmpty() || next >= page.totalRecordCount
    }
    return ApiResult.Success(FilteredPage(found, total = found.size.takeIf { exhausted && startIndex == 0 }, nextIndex = next, hasMore = !exhausted))
}

private fun keepReading(found: Int, limit: Int, pagesRead: Int): Boolean = found < limit && (found == 0 || pagesRead < LibraryRepository.SCAN_PAGES)

private fun ItemsResultDto.toFilteredPage(baseUrl: String): FilteredPage = FilteredPage(
    items = items.map { it.toMediaItem(baseUrl) },
    total = totalRecordCount,
    nextIndex = startIndex + items.size,
    hasMore = items.isNotEmpty() && startIndex + items.size < totalRecordCount,
)

private fun List<NameValueDto>.toLanguageOptions(): List<FilterOption> = mapNotNull { language ->
    val code = language.value?.takeIf { it.isNotBlank() && it != "und" } ?: return@mapNotNull null
    FilterOption(code, language.name?.takeIf { it.isNotBlank() } ?: code)
}.distinctBy { it.id }.sortedBy { it.name.lowercase() }

private fun genreKind(filter: HomeFilter?, type: TypeFilter?): GenreKind = when {
    filter == HomeFilter.Movies || type == TypeFilter.Movies -> GenreKind.Movies
    filter == HomeFilter.Shows || type == TypeFilter.Shows -> GenreKind.Shows
    else -> GenreKind.Both
}

/** Which titles a genre's count is made of. */
private enum class GenreKind(val types: List<String>) {
    Movies(listOf("Movie")),
    Shows(listOf("Series")),
    Both(listOf("Movie", "Series")),
}

private fun List<BaseItemDto>.toOptions(kind: GenreKind): List<FilterOption> = mapNotNull { dto ->
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

// "S1 E4"; null when the server sent neither number.
internal fun episodeCode(season: Int?, episode: Int?): String? = when {
    season != null && episode != null -> "S$season E$episode"
    episode != null -> "E$episode"
    else -> null
}
