package app.vibeyfin.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Fields with a default are left out of the request when they hold it: the client's Json doesn't
// encode defaults. So only optional fields (null, or an empty list) have one. A field the server
// reads a different default for, such as `Protocol` (http) or `Type` (audio), has none and is always sent.

/** The body of `POST /Items/{id}/PlaybackInfo`: what this device can play, and what the server may do about it. */
@Serializable
data class PlaybackInfoRequestDto(
    @SerialName("UserId") val userId: String,
    @SerialName("DeviceProfile") val deviceProfile: DeviceProfileDto,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Int,
    @SerialName("EnableDirectPlay") val enableDirectPlay: Boolean,
    @SerialName("EnableDirectStream") val enableDirectStream: Boolean,
    @SerialName("EnableTranscoding") val enableTranscoding: Boolean,
    @SerialName("AutoOpenLiveStream") val autoOpenLiveStream: Boolean,
    /**
     * The media source the track choices below are for. The server ignores [audioStreamIndex] and
     * [subtitleStreamIndex] unless this names the source, so they are only ever sent together with it.
     */
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    /** The audio track the user chose; left out, the server picks by the user's language preference. */
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    /** The subtitle track the user chose, `-1` for none; left out, the server picks by the user's settings. */
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @SerialName("StartTimeTicks") val startTimeTicks: Long? = null,
)

/** `DeviceProfile`: the formats a device can decode, from which the server picks direct play or a transcode. */
@Serializable
data class DeviceProfileDto(
    @SerialName("Name") val name: String,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Int,
    @SerialName("MaxStaticBitrate") val maxStaticBitrate: Int,
    @SerialName("DirectPlayProfiles") val directPlayProfiles: List<DirectPlayProfileDto>,
    @SerialName("TranscodingProfiles") val transcodingProfiles: List<TranscodingProfileDto>,
    @SerialName("CodecProfiles") val codecProfiles: List<CodecProfileDto> = emptyList(),
    @SerialName("SubtitleProfiles") val subtitleProfiles: List<SubtitleProfileDto> = emptyList(),
)

@Serializable
data class DirectPlayProfileDto(
    /** Comma-separated containers, e.g. `mp4,m4v`. */
    @SerialName("Container") val container: String,
    /** `Video`. */
    @SerialName("Type") val type: String,
    @SerialName("VideoCodec") val videoCodec: String? = null,
    @SerialName("AudioCodec") val audioCodec: String? = null,
)

@Serializable
data class TranscodingProfileDto(
    @SerialName("Container") val container: String,
    /** `Video`. */
    @SerialName("Type") val type: String,
    @SerialName("VideoCodec") val videoCodec: String? = null,
    @SerialName("AudioCodec") val audioCodec: String? = null,
    /** `hls`; the server's own default is a progressive stream. */
    @SerialName("Protocol") val protocol: String,
    /** `Streaming`. */
    @SerialName("Context") val context: String,
    @SerialName("MaxAudioChannels") val maxAudioChannels: String? = null,
    @SerialName("MinSegments") val minSegments: Int? = null,
    @SerialName("BreakOnNonKeyFrames") val breakOnNonKeyFrames: Boolean? = null,
)

/** Limits on one codec: a stream beyond them can't be direct played even if the container fits. */
@Serializable
data class CodecProfileDto(
    /** `Video`. */
    @SerialName("Type") val type: String,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Conditions") val conditions: List<ProfileConditionDto> = emptyList(),
)

@Serializable
data class ProfileConditionDto(
    @SerialName("Condition") val condition: String,
    @SerialName("Property") val property: String,
    @SerialName("Value") val value: String,
    @SerialName("IsRequired") val isRequired: Boolean,
)

@Serializable
data class SubtitleProfileDto(
    @SerialName("Format") val format: String,
    @SerialName("Method") val method: String,
)

/** `PlaybackInfoResponse`: the ways the server offers to play the item. */
@Serializable
data class PlaybackInfoResponseDto(
    @SerialName("MediaSources") val mediaSources: List<MediaSourceDto> = emptyList(),
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    /** Set when the server can't offer anything, e.g. `NoCompatibleStream`. */
    @SerialName("ErrorCode") val errorCode: String? = null,
)

/** One version of an item's file, with what the server decided to do with it for this device. */
@Serializable
data class MediaSourceDto(
    @SerialName("Id") val id: String,
    @SerialName("Container") val container: String? = null,
    @SerialName("ETag") val etag: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    /** The file's size in bytes, when the server knows it. */
    @SerialName("Size") val size: Long? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean = false,
    @SerialName("SupportsTranscoding") val supportsTranscoding: Boolean = false,
    /** A path on the server (it carries its own key), present when the item must be streamed or transcoded. */
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("TranscodingSubProtocol") val transcodingSubProtocol: String? = null,
    @SerialName("MediaStreams") val mediaStreams: List<MediaStreamDto> = emptyList(),
    /** The audio track the server would pick for this user; null when it has no opinion. */
    @SerialName("DefaultAudioStreamIndex") val defaultAudioStreamIndex: Int? = null,
    /** The subtitle track the server would show for this user, or null or `-1` for none. */
    @SerialName("DefaultSubtitleStreamIndex") val defaultSubtitleStreamIndex: Int? = null,
)

/** One track in a media source: video, an audio track or a subtitle track. */
@Serializable
data class MediaStreamDto(
    @SerialName("Index") val index: Int,
    /** `Video`, `Audio`, `Subtitle`, `EmbeddedImage` or `Data`. */
    @SerialName("Type") val type: String,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("Title") val title: String? = null,
    /** The server's name for the track, e.g. `English - Dolby Digital - 5.1 - Default`. */
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    /** For a subtitle: `External` (a file at [deliveryUrl]), `Embed`, `Hls`, `Encode` (burned into the video) or `Drop`. */
    @SerialName("DeliveryMethod") val deliveryMethod: String? = null,
    /** A path on the server for an `External` subtitle. */
    @SerialName("DeliveryUrl") val deliveryUrl: String? = null,
    /** A subtitle that is a file next to the video rather than inside it. The item itself has no [deliveryUrl] for it. */
    @SerialName("IsExternal") val isExternal: Boolean = false,
    /** The file marks the track as the one to play (or show) by default. */
    @SerialName("IsDefault") val isDefault: Boolean = false,
    /** A subtitle the file marks as forced: only the foreign dialogue, for viewers who understand the rest. */
    @SerialName("IsForced") val isForced: Boolean = false,
    /** For video: the picture size in pixels. */
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    /** For video: `SDR`, `HDR10`, `HDR10Plus`, `HLG`, `DOVI`, `DOVIWithHDR10` and so on; absent on servers before 10.9. */
    @SerialName("VideoRangeType") val videoRangeType: String? = null,
    /** In bits per second. */
    @SerialName("BitRate") val bitRate: Int? = null,
    @SerialName("RealFrameRate") val realFrameRate: Float? = null,
    @SerialName("BitDepth") val bitDepth: Int? = null,
    /** For video: the codec level as the server counts it (H.264 level 4.1 is 41). */
    @SerialName("Level") val level: Double? = null,
)

/** The body of `POST /Sessions/Playing`. */
@Serializable
data class PlaybackStartDto(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String,
    @SerialName("PlaySessionId") val playSessionId: String,
    @SerialName("PlayMethod") val playMethod: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean,
    @SerialName("CanSeek") val canSeek: Boolean,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
)

/** The body of `POST /Sessions/Playing/Progress`. */
@Serializable
data class PlaybackProgressDto(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String,
    @SerialName("PlaySessionId") val playSessionId: String,
    @SerialName("PlayMethod") val playMethod: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean,
    @SerialName("CanSeek") val canSeek: Boolean,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
)

/** The body of `POST /Sessions/Playing/Stopped`. */
@Serializable
data class PlaybackStopDto(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String,
    @SerialName("PlaySessionId") val playSessionId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("Failed") val failed: Boolean,
)
