package com.liamgleeson.vibeyfin.data.api

/** What an item's detail page needs beyond the list fields; the server leaves these out unless asked. */
private const val DETAIL_FIELDS = "Overview,Genres"

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
