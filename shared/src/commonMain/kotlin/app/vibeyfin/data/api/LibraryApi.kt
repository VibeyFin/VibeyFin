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

/** `GET /Studios`: the studios behind the movies and shows [userId] can see. The server has no sort option here. */
suspend fun JellyfinApi.studios(userId: String): ApiResult<ItemsResultDto> =
    get("/Studios", mapOf("userId" to userId, "includeItemTypes" to "Movie,Series"), retry = true)

/** `GET /Genres`: the genres of the movies and shows [userId] can see, by name. */
suspend fun JellyfinApi.genres(userId: String): ApiResult<ItemsResultDto> = get(
    "/Genres",
    mapOf("userId" to userId, "includeItemTypes" to "Movie,Series", "sortBy" to "SortName", "sortOrder" to "Ascending"),
    retry = true,
)

/**
 * `GET /Items` across every library, for one category page: a type, a genre or a studio, a page at a time.
 * [genreId] and [studioId] are the server's own filters; `DateCreated` is asked for so "New" can cut the list.
 */
suspend fun JellyfinApi.filteredItems(
    userId: String,
    types: List<String>,
    genreId: String?,
    studioId: String?,
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
        put("fields", "DateCreated")
        put("startIndex", startIndex.toString())
        put("limit", limit.toString())
        genreId?.let { put("genreIds", it) }
        studioId?.let { put("studioIds", it) }
    },
    retry = true,
)
