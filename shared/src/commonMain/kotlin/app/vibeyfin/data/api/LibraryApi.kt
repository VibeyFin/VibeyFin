package app.vibeyfin.data.api

/** What an item's detail page needs beyond the list fields; the server leaves these out unless asked. */
private const val DETAIL_FIELDS = "Overview,Genres,MediaStreams,Trickplay"

/**
 * `GET /UserViews`: the libraries [userId] can see. These are items too, so a library opens like
 * any folder with [items].
 */
suspend fun JellyfinApi.userViews(userId: String): ApiResult<ItemsResultDto> =
    get("/UserViews", mapOf("userId" to userId), retry = true)

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
suspend fun JellyfinApi.itemsById(id: String, userId: String): ApiResult<ItemsResultDto> =
    get("/Items", mapOf("ids" to id, "userId" to userId, "fields" to DETAIL_FIELDS), retry = true)

/** The URL of an item's artwork on the server at [baseUrl]; [type] is `Primary` or `Backdrop`. */
fun itemImageUrl(baseUrl: String, itemId: String, type: String, tag: String, maxWidth: Int): String =
    "$baseUrl/Items/$itemId/Images/$type?tag=$tag&maxWidth=$maxWidth"

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
 * `GET /Items` across every library, for one category page: a type, and optionally a genre, a page at a time.
 * [genreId] is the server's own filter; [sortBy] is a comma-separated list of its sort keys.
 */
suspend fun JellyfinApi.filteredItems(
    userId: String,
    types: List<String>,
    genreId: String?,
    sortBy: String,
    sortOrder: String,
    startIndex: Int,
    limit: Int,
): ApiResult<ItemsResultDto> = get(
    "/Items",
    buildMap {
        put("userId", userId)
        put("recursive", "true")
        put("includeItemTypes", types.joinToString(","))
        put("sortBy", sortBy)
        put("sortOrder", sortOrder)
        put("startIndex", startIndex.toString())
        put("limit", limit.toString())
        genreId?.let { put("genreIds", it) }
    },
    retry = true,
)
