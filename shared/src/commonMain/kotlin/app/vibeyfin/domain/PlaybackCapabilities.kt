package app.vibeyfin.domain

import app.vibeyfin.data.api.ClientInfo
import app.vibeyfin.data.api.CodecProfileDto
import app.vibeyfin.data.api.DeviceProfileDto
import app.vibeyfin.data.api.DirectPlayProfileDto
import app.vibeyfin.data.api.ProfileConditionDto
import app.vibeyfin.data.api.SubtitleProfileDto
import app.vibeyfin.data.api.TranscodingProfileDto

/**
 * What this device's player can decode, in the server's names (`h264`, `aac`, `mkv`). The server
 * plays a file directly only when its container and codecs are all listed here; otherwise it
 * remuxes or transcodes.
 */
data class PlaybackCapabilities(
    /** Containers the player opens directly, e.g. `mp4`, `mkv`. */
    val containers: List<String>,
    val videoCodecs: List<String>,
    val audioCodecs: List<String>,
    /**
     * Text subtitle formats the player loads from a separate file (`srt`, `vtt`, `ass`). The server
     * sends the chosen track as one of these; a subtitle it can't send that way (an image-based one,
     * or any when this is empty) is burned into the video instead, which means a transcode.
     */
    val subtitleFormats: List<String> = emptyList(),
    /**
     * Video codecs the player takes inside the server's HLS stream. A file in one of these is repackaged
     * (a remux) instead of re-encoded when only its container or audio is the problem.
     */
    val streamVideoCodecs: List<String> = listOf("h264"),
)

/** Reports [PlaybackCapabilities] for this device. Bound per platform in `platformModule()`. */
interface PlaybackCapabilitiesSource {
    fun current(): PlaybackCapabilities
}

/**
 * The bitrate the Maximum quality choice asks the server for, in bits per second. Above it the
 * server transcodes down, even when the device could decode the file. Generous because most servers
 * are on the same network. A lower quality is a profile setting ([ProfileSettings.maxStreamingBitrate]).
 */
const val MAX_STREAMING_BITRATE = 60_000_000

/** The bitrate to ask for given a quality setting: 0 is Maximum, anything else is the cap itself. */
fun effectiveBitrate(setting: Int): Int = if (setting == 0) MAX_STREAMING_BITRATE else setting

private const val VIDEO = "Video"

/** Optional: a stream that doesn't say (no level in its metadata) is judged by the player instead. */
private fun lessThanOrEqual(property: String, value: String) =
    ProfileConditionDto(condition = "LessThanEqual", property = property, value = value, isRequired = false)

/** Transcodes are always AAC stereo in HLS, and H.264 unless the file's own codec can be carried as it is. */
private const val TRANSCODE_AUDIO = "aac"

/** The newest H.264 level (5.2) common phone decoders handle, as the server counts it (level × 10). */
private const val H264_MAX_LEVEL = "52"

internal fun PlaybackCapabilities.toDeviceProfile(maxBitrate: Int): DeviceProfileDto = DeviceProfileDto(
    name = ClientInfo.CLIENT_NAME,
    maxStreamingBitrate = maxBitrate,
    maxStaticBitrate = maxBitrate,
    directPlayProfiles = listOf(
        DirectPlayProfileDto(
            container = containers.joinToString(","),
            type = VIDEO,
            videoCodec = videoCodecs.joinToString(","),
            audioCodec = audioCodecs.joinToString(","),
        ),
    ),
    transcodingProfiles = listOf(
        TranscodingProfileDto(
            container = "ts",
            type = VIDEO,
            protocol = "hls",
            context = "Streaming",
            videoCodec = streamVideoCodecs.joinToString(","),
            audioCodec = TRANSCODE_AUDIO,
            maxAudioChannels = "2",
            minSegments = 1,
            breakOnNonKeyFrames = true,
        ),
    ),
    subtitleProfiles = subtitleFormats.map { SubtitleProfileDto(format = it, method = "External") },
    // 10-bit H.264 and very high levels are where hardware decoders give up, whatever the container.
    codecProfiles = listOf(
        CodecProfileDto(
            type = VIDEO,
            codec = "h264",
            conditions = listOf(
                lessThanOrEqual("VideoLevel", H264_MAX_LEVEL),
                lessThanOrEqual("VideoBitDepth", "8"),
            ),
        ),
        CodecProfileDto(
            type = VIDEO,
            codec = "hevc",
            conditions = listOf(lessThanOrEqual("VideoBitDepth", "10")),
        ),
    ),
)
