package app.vibeyfin.domain

import app.vibeyfin.data.api.MediaStreamDto
import kotlinx.serialization.Serializable

/** The kind of picture a video holds, as far as the player's badges tell. */
enum class DynamicRange { Sdr, Hdr10, DolbyVision, Hlg }

/** The video stream of a file: what it is, not how the server delivers it. */
@Serializable
data class VideoFormat(
    val codec: String?,
    val width: Int?,
    val height: Int?,
    val dynamicRange: DynamicRange,
    val frameRate: Float?,
    val bitrateKbps: Int?,
)

@Serializable
data class StreamTrack(
    val displayTitle: String? = null,
    val title: String? = null,
    val language: String? = null,
    val codec: String? = null,
    val forced: Boolean = false,
    val external: Boolean = false,
    val index: Int? = null,
)

internal fun MediaStreamDto.toStreamTrack(): StreamTrack = StreamTrack(
    displayTitle = displayTitle?.takeIf { it.isNotBlank() },
    title = title?.takeIf { it.isNotBlank() },
    language = language?.takeIf { it.isNotBlank() },
    codec = codec?.takeIf { it.isNotBlank() },
    forced = isForced,
    external = isExternal,
    index = index,
)

internal fun MediaStreamDto.toVideoFormat(): VideoFormat = VideoFormat(
    codec = codec,
    width = width,
    height = height,
    dynamicRange = videoRangeType.toDynamicRange(),
    frameRate = realFrameRate,
    bitrateKbps = bitRate?.div(1000),
)

/** `DOVI*` is Dolby Vision, `HDR10` and `HDR10Plus` are HDR10, `HLG` is HLG; anything else, or nothing, is SDR. */
internal fun String?.toDynamicRange(): DynamicRange = when {
    this == null -> DynamicRange.Sdr
    startsWith("DOVI") -> DynamicRange.DolbyVision
    this == "HDR10" || this == "HDR10Plus" -> DynamicRange.Hdr10
    this == "HLG" -> DynamicRange.Hlg
    else -> DynamicRange.Sdr
}
