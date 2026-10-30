package app.vibeyfin.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /MediaSegments/{id}`: the intro, outro, recap and other parts the server has marked in an
 * item. Servers before 10.10 don't have it and answer 404.
 */
suspend fun JellyfinApi.mediaSegments(itemId: String): ApiResult<MediaSegmentsResultDto> = get("/MediaSegments/$itemId", retry = true)

@Serializable
data class MediaSegmentsResultDto(
    @SerialName("Items") val items: List<MediaSegmentDto> = emptyList(),
)

@Serializable
data class MediaSegmentDto(
    /** `Unknown`, `Outro`, `Intro`, `Preview`, `Recap` or `Commercial`. */
    @SerialName("Type") val type: String? = null,
    @SerialName("StartTicks") val startTicks: Long = 0,
    @SerialName("EndTicks") val endTicks: Long = 0,
)
