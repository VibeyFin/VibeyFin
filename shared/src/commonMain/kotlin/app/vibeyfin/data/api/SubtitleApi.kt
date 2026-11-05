package app.vibeyfin.data.api

import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `RemoteSubtitleInfo`, with only the fields we read. */
@Serializable
data class RemoteSubtitleDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("ProviderName") val providerName: String? = null,
    @SerialName("Format") val format: String? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("IsHashMatch") val isHashMatch: Boolean? = null,
)

/** `GET /Items/{id}/RemoteSearch/Subtitles/{language}`: asks the server's subtitle providers. A server with none answers an empty list. */
suspend fun JellyfinApi.remoteSubtitles(itemId: String, language: String): ApiResult<List<RemoteSubtitleDto>> = get("/Items/$itemId/RemoteSearch/Subtitles/$language")

/**
 * `POST /Items/{id}/RemoteSearch/Subtitles/{subtitleId}`. The server answers 204 even when the provider failed, and only queues
 * the refresh that lists the track, so success can only be seen by the track appearing on the item.
 */
suspend fun JellyfinApi.downloadRemoteSubtitle(itemId: String, subtitleId: String): ApiResult<Unit> = execute({ }) {
    method = HttpMethod.Post
    url("$baseUrl/Items/$itemId/RemoteSearch/Subtitles/${subtitleId.encodeURLPathPart()}")
}
