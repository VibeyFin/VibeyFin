package com.liamgleeson.vibeyfin.domain

/**
 * What the playback stats overlay shows: how the server delivers the video and what the player
 * measures. The plan fills what the server said ([method], [transcodeReasons], the codecs and size
 * of the file); the native player adds what only it knows through [LiveDiagnostics]. A null value is
 * one nobody knows, and the screen shows it as unavailable.
 */
data class PlaybackDiagnostics(
    val method: PlayMethod?,
    /** Why the server converts the stream, as it names them in the stream URL; empty for a direct play. */
    val transcodeReasons: List<String>,
    val videoCodec: String?,
    val audioCodec: String?,
    val width: Int?,
    val height: Int?,
    val frameRate: Float?,
    val bitrateKbps: Int?,
    val droppedFrames: Int?,
    val throughputKbps: Int?,
    val bufferMs: Long?,
)

/** What the native player measures about the stream it is playing. Anything it can't say is null. */
data class LiveDiagnostics(
    val videoCodec: String?,
    val audioCodec: String?,
    val width: Int?,
    val height: Int?,
    val frameRate: Float?,
    val bitrateKbps: Int?,
    val droppedFrames: Int?,
    val throughputKbps: Int?,
    val bufferMs: Long?,
)

/** The player's own values win; one it didn't report leaves the plan's value as it was. */
fun PlaybackDiagnostics.withLive(live: LiveDiagnostics): PlaybackDiagnostics = copy(
    videoCodec = live.videoCodec ?: videoCodec,
    audioCodec = live.audioCodec ?: audioCodec,
    width = live.width ?: width,
    height = live.height ?: height,
    frameRate = live.frameRate ?: frameRate,
    bitrateKbps = live.bitrateKbps ?: bitrateKbps,
    droppedFrames = live.droppedFrames ?: droppedFrames,
    throughputKbps = live.throughputKbps ?: throughputKbps,
    bufferMs = live.bufferMs ?: bufferMs,
)

/** What the server said about the stream the plan opens, with the audio track [audioIndex] playing. */
internal fun PlaybackPlan.toDiagnostics(audioIndex: Int?): PlaybackDiagnostics = PlaybackDiagnostics(
    method = method,
    transcodeReasons = if (method == PlayMethod.DirectPlay) emptyList() else transcodeReasons(url),
    videoCodec = videoFormat?.codec,
    audioCodec = audioTracks.firstOrNull { it.index == audioIndex }?.codec,
    width = videoFormat?.width,
    height = videoFormat?.height,
    frameRate = videoFormat?.frameRate,
    bitrateKbps = videoFormat?.bitrateKbps,
    droppedFrames = null,
    throughputKbps = null,
    bufferMs = null,
)

/** The `TranscodeReasons` in the query of a stream URL (`VideoCodecNotSupported`, ...); empty when it names none. */
internal fun transcodeReasons(url: String): List<String> =
    url.substringAfter('?', "")
        .split('&')
        .firstOrNull { it.substringBefore('=').equals("TranscodeReasons", ignoreCase = true) }
        ?.substringAfter('=', "")
        ?.split(',')
        ?.filter { it.isNotEmpty() }
        .orEmpty()
