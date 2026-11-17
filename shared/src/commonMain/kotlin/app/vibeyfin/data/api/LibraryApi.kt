package app.vibeyfin.data.api

// What an item's detail page needs beyond the list fields; the server leaves these out unless asked.
private const val DETAIL_FIELDS = "Overview,Genres,MediaStreams,Trickplay,People,ChildCount,RemoteTrailers,LocalTrailerCount,ProviderIds,MediaSources"

/**
 * `GET /UserViews`: the libraries [userId] can see. These are items too, so a library opens like
 * any folder with [items].
 */
suspend fun JellyfinApi.userViews(userId: String): ApiResult<ItemsResultDto> = get("/UserViews", mapOf("userId" to userId), retry = true)

/** `GET /Items?parentId=`: one page of what is inside a library, folder, series or season. */
suspend fun JellyfinApi.items(
    parentId: String,
    userId: String,
    startIndex: Int,
    limit: Int,
): ApiResult<ItemsResultDto> = get(
    "/Items",
    mapOf(
        "parentId" to parentId,
        "userId" to userId,
        "sortBy" to "SortName",
        "sortOrder" to "Ascending",
        "startIndex" to startIndex.toString(),
        "limit" to limit.toString(),
    ),
    retry = true,
)

/**
 * `GET /Items?ids=`: one item with its metadata. This works on every server we accept (10.10+),
 * unlike the single-item routes. An id the server doesn't know is a success with no items.
 */
suspend fun JellyfinApi.itemsById(id: String, userId: String): ApiResult<ItemsResultDto> = get("/Items", mapOf("ids" to id, "userId" to userId, "fields" to DETAIL_FIELDS), retry = true)

/**
 * `GET /Items?ids=`: several items with only what the list fields hold, which includes the user's own record of each
 * (`UserData`). For checking what the user has watched of what they downloaded.
 */
suspend fun JellyfinApi.itemsByIds(userId: String, ids: List<String>): ApiResult<ItemsResultDto> = get("/Items", mapOf("ids" to ids.joinToString(","), "userId" to userId), retry = true)

/** The URL of an item's artwork on the server at [baseUrl]; [type] is `Primary` or `Backdrop`. */
fun itemImageUrl(baseUrl: String, itemId: String, type: String, tag: String, maxWidth: Int): String = "$baseUrl/Items/$itemId/Images/$type?tag=$tag&maxWidth=$maxWidth"

/** `GET /Genres`: the genres of the [types] [userId] can see (`Movie`, `Series`), by name, with how many titles each holds. */
suspend fun JellyfinApi.genres(userId: String, types: List<String> = listOf("Movie", "Series")): ApiResult<ItemsResultDto> = get(
    "/Genres",
    mapOf(
        "userId" to userId,
        "includeItemTypes" to types.joinToString(","),
        "sortBy" to "SortName",
        "sortOrder" to "Ascending",
        "fields" to "ItemCounts",
    ),
    retry = true,
)

/**
 * `GET /Items` for one category page, a page at a time: [query] holds the page's types, order and filters, as
 * `FilterSet.serverQuery` builds them.
 */
suspend fun JellyfinApi.filteredItems(
    userId: String,
    query: Map<String, String>,
    startIndex: Int,
    limit: Int,
    /** Extra fields the server leaves out of a list unless asked, comma-separated (`Genres`). */
    fields: String? = null,
): ApiResult<ItemsResultDto> = get(
    "/Items",
    buildMap {
        putAll(query)
        put("userId", userId)
        fields?.let { put("fields", it) }
        put("startIndex", startIndex.toString())
        put("limit", limit.toString())
    },
    retry = true,
)

/** `GET /Studios`: the studios and networks of the [types] [userId] can see, with how many titles each holds. */
suspend fun JellyfinApi.studios(userId: String, types: List<String>, parentId: String?): ApiResult<ItemsResultDto> = get(
    "/Studios",
    buildMap {
        put("userId", userId)
        put("includeItemTypes", types.joinToString(","))
        put("fields", "ItemCounts")
        parentId?.let { put("parentId", it) }
    },
    retry = true,
)

/** `GET /Items/Filters2`: the tags and the audio and subtitle languages among the [types] [userId] can see. */
suspend fun JellyfinApi.queryFilters(userId: String, types: List<String>, parentId: String?): ApiResult<QueryFiltersDto> = get(
    "/Items/Filters2",
    buildMap {
        put("userId", userId)
        put("includeItemTypes", types.joinToString(","))
        put("recursive", "true")
        parentId?.let { put("parentId", it) }
    },
    retry = true,
)

/** `GET /Items/Filters`: the years and age ratings in one library; the server answers with nothing unless given [parentId]. */
suspend fun JellyfinApi.legacyFilters(userId: String, types: List<String>, parentId: String): ApiResult<LegacyFiltersDto> = get(
    "/Items/Filters",
    mapOf("userId" to userId, "includeItemTypes" to types.joinToString(","), "parentId" to parentId),
    retry = true,
)

/**
 * `GET /Items/Latest?parentId=`: what was added to the library most recently, newest first, as a plain list. Episodes
 * are grouped under their show and season by the server (`groupItems`), so a new season is one poster. [unplayedOnly] leaves out
 * what the user has watched.
 */
suspend fun JellyfinApi.latestItems(userId: String, parentId: String, limit: Int, unplayedOnly: Boolean = false): ApiResult<List<BaseItemDto>> = get(
    "/Items/Latest",
    buildMap {
        put("userId", userId)
        put("parentId", parentId)
        put("limit", limit.toString())
        put("groupItems", "true")
        if (unplayedOnly) put("isPlayed", "false")
    },
    retry = true,
)

/** `GET /Items/{id}/Similar`: what the server thinks is like the item, at most [limit]. */
suspend fun JellyfinApi.similarItems(userId: String, id: String, limit: Int): ApiResult<ItemsResultDto> = get("/Items/$id/Similar", mapOf("userId" to userId, "limit" to limit.toString()), retry = true)

/** `GET /Shows/{id}/Seasons`: a show's seasons in the server's order, Specials included. */
suspend fun JellyfinApi.seasonsOf(userId: String, seriesId: String): ApiResult<ItemsResultDto> = get("/Shows/$seriesId/Seasons", mapOf("userId" to userId), retry = true)

/** `GET /Shows/{id}/Episodes?seasonId=`: the episodes of one season, with their synopsis. */
suspend fun JellyfinApi.episodesOfSeason(userId: String, seriesId: String, seasonId: String): ApiResult<ItemsResultDto> = get("/Shows/$seriesId/Episodes", mapOf("userId" to userId, "seasonId" to seasonId, "fields" to "Overview"), retry = true)

/** `GET /Persons`: people whose name matches [term], at most [limit], by name. */
suspend fun JellyfinApi.persons(userId: String, term: String, limit: Int): ApiResult<ItemsResultDto> = get(
    "/Persons",
    mapOf("userId" to userId, "searchTerm" to term, "limit" to limit.toString()),
    retry = true,
)

/** `GET /Items?personIds=`: the movies and shows in the library that [personId] appears in, newest first. */
suspend fun JellyfinApi.itemsOfPerson(userId: String, personId: String): ApiResult<ItemsResultDto> = get(
    "/Items",
    mapOf(
        "userId" to userId,
        "personIds" to personId,
        "recursive" to "true",
        "includeItemTypes" to "Movie,Series",
        "sortBy" to "PremiereDate,SortName",
        "sortOrder" to "Descending",
    ),
    retry = true,
)

/** `GET /Items/{id}/SpecialFeatures`: behind the scenes, extras and the like; a plain list, not a page. */
suspend fun JellyfinApi.specialFeatures(userId: String, id: String): ApiResult<List<BaseItemDto>> = get("/Items/$id/SpecialFeatures", mapOf("userId" to userId), retry = true)

/** `GET /Items/{id}/Collections`: the collections (box sets) the item is part of. */
suspend fun JellyfinApi.collectionsOf(userId: String, id: String): ApiResult<ItemsResultDto> = get("/Items/$id/Collections", mapOf("userId" to userId), retry = true)

/** `GET /Items/{id}/LocalTrailers`: the trailers the server holds for the item, as playable items. */
suspend fun JellyfinApi.localTrailers(userId: String, id: String): ApiResult<List<BaseItemDto>> = get("/Items/$id/LocalTrailers", mapOf("userId" to userId), retry = true)
