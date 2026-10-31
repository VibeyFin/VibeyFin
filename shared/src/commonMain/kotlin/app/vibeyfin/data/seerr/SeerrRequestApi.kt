package app.vibeyfin.data.seerr

import app.vibeyfin.data.api.ApiResult
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

// Requests: asking for a title, listing them, withdrawing one, and the quota that limits them. Writes are never retried.

/** Which seasons of a show to request. */
sealed interface SeasonChoice {
    /** Every season not yet available or requested; Seerr works out which. */
    data object All : SeasonChoice

    data class Only(val seasons: List<Int>) : SeasonChoice
}

/**
 * `POST /request` for a movie. Null when Seerr found nothing left to request (it answers `202` then). Seerr answers
 * [app.vibeyfin.data.api.ApiError.Forbidden] when the user may not request it (in 4K, say) or has reached their quota,
 * and `409` ([app.vibeyfin.data.api.ApiError.Unexpected]) when it is already requested.
 */
suspend fun SeerrApi.requestMovie(tmdbId: Int, is4k: Boolean = false): ApiResult<SeerrRequestDto?> = newRequest(SeerrNewRequestDto(mediaType = "movie", mediaId = tmdbId, is4k = is4k))

/** `POST /request` for some or all seasons of a show. Null when every season asked for is already requested or available. */
suspend fun SeerrApi.requestShow(tmdbId: Int, seasons: SeasonChoice, is4k: Boolean = false): ApiResult<SeerrRequestDto?> {
    val chosen = when (seasons) {
        SeasonChoice.All -> JsonPrimitive("all")
        is SeasonChoice.Only -> JsonArray(seasons.seasons.map(::JsonPrimitive))
    }
    return newRequest(SeerrNewRequestDto(mediaType = "tv", mediaId = tmdbId, seasons = chosen, is4k = is4k))
}

private suspend fun SeerrApi.newRequest(body: SeerrNewRequestDto): ApiResult<SeerrRequestDto?> = execute({ response ->
    if (response.status == HttpStatusCode.Accepted) null else response.body<SeerrRequestDto>()
}) {
    method = HttpMethod.Post
    url(endpoint("/request"))
    contentType(ContentType.Application.Json)
    setBody(body)
}

/**
 * `GET /request`: the requests of user [requestedBy] (Seerr's user id), most recently changed first. Seerr only ever
 * lists a user's own requests unless they manage requests, so [requestedBy] keeps a manager's list to their own too.
 */
suspend fun SeerrApi.requests(requestedBy: Int, take: Int = 20, skip: Int = 0): ApiResult<SeerrRequestPageDto> = get(
    "/request",
    mapOf("requestedBy" to "$requestedBy", "take" to "$take", "skip" to "$skip", "sort" to "modified", "filter" to "all"),
    retry = true,
)

/** `DELETE /request/{id}`: withdraws a request. Seerr lets the requester do this only while it waits for approval. */
suspend fun SeerrApi.deleteRequest(requestId: Int): ApiResult<Unit> = execute({ }) {
    method = HttpMethod.Delete
    url(endpoint("/request/$requestId"))
}

/** `GET /user/{id}/quota`: how many requests the user has left. */
suspend fun SeerrApi.quota(userId: Int): ApiResult<SeerrQuotaDto> = get("/user/$userId/quota", retry = true)
