package com.liamgleeson.vibeyfin.data.api

import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

/**
 * `GET /UserItems/Resume`: what [userId] has started and not finished, most recently watched first.
 * Videos only; the server also lists audio books and the like. Safe to repeat, so it is retried.
 */
suspend fun JellyfinApi.resumeItems(userId: String, limit: Int): ApiResult<ItemsResultDto> = get(
    "/UserItems/Resume",
    mapOf("userId" to userId, "mediaTypes" to "Video", "limit" to limit.toString()),
    retry = true,
)

/**
 * `GET /Shows/NextUp`: for each show [userId] follows, the first episode they haven't watched. An
 * episode that is half watched is left to [resumeItems] (`enableResumable=false`), and a show that was
 * finished long ago doesn't come back for a rewatch (`enableRewatching=false`).
 */
suspend fun JellyfinApi.nextUp(userId: String, limit: Int): ApiResult<ItemsResultDto> = get(
    "/Shows/NextUp",
    mapOf(
        "userId" to userId,
        "limit" to limit.toString(),
        "enableResumable" to "false",
        "enableRewatching" to "false",
    ),
    retry = true,
)

/**
 * `POST /UserPlayedItems/{id}` marks [itemId] watched for [userId]; `DELETE` marks it unwatched (the
 * server also forgets where they stopped). Setting a state twice changes nothing, so, unlike other
 * writes, this one is retried like a read. The answer's body is not read.
 */
suspend fun JellyfinApi.setPlayed(itemId: String, userId: String, played: Boolean): ApiResult<Unit> =
    execute({ }, retry = true) {
        method = if (played) HttpMethod.Post else HttpMethod.Delete
        url("$baseUrl/UserPlayedItems/$itemId")
        parameter("userId", userId)
    }
