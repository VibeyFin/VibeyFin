package com.liamgleeson.vibeyfin.app.player

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import com.liamgleeson.vibeyfin.domain.LiveDiagnostics

/** A short codec name for a MIME type: `video/avc` is "h264", `audio/mp4a-latm` is "aac". Unknown types show their subtype. */
fun codecName(mimeType: String?): String? {
    val type = mimeType?.lowercase()?.takeIf { it.contains('/') } ?: return null
    return when (type) {
        "video/avc" -> "h264"
        "video/hevc" -> "hevc"
        "video/av01" -> "av1"
        "video/x-vnd.on2.vp9" -> "vp9"
        "video/x-vnd.on2.vp8" -> "vp8"
        "video/mp4v-es" -> "mpeg4"
        "video/dolby-vision" -> "dolby vision"
        "audio/mp4a-latm" -> "aac"
        "audio/ac3" -> "ac3"
        "audio/eac3" -> "eac3"
        "audio/eac3-joc" -> "eac3 atmos"
        "audio/true-hd" -> "truehd"
        "audio/vnd.dts" -> "dts"
        "audio/vnd.dts.hd" -> "dts-hd"
        "audio/mpeg" -> "mp3"
        "audio/opus" -> "opus"
        "audio/vorbis" -> "vorbis"
        "audio/flac" -> "flac"
        else -> type.substringAfter('/').removePrefix("x-")
    }
}

/** Media3 uses -1 for "not known"; a rate or bitrate of zero or less is never real. */
fun knownKbps(bitsPerSecond: Int): Int? = if (bitsPerSecond > 0) bitsPerSecond / 1000 else null

fun knownKbps(bitsPerSecond: Long): Int? = if (bitsPerSecond > 0) (bitsPerSecond / 1000).toInt() else null

fun knownFrameRate(frameRate: Float): Float? = if (frameRate > 0f) frameRate else null

/** What the player says about the decoded streams right now; whatever it can't say stays null. */
@androidx.annotation.OptIn(UnstableApi::class)
fun liveDiagnostics(
    video: Format?,
    audio: Format?,
    width: Int,
    height: Int,
    droppedFrames: Int,
    bandwidthBps: Long,
    bufferMs: Long,
): LiveDiagnostics = LiveDiagnostics(
    videoCodec = codecName(video?.sampleMimeType),
    audioCodec = codecName(audio?.sampleMimeType),
    width = width.takeIf { it > 0 },
    height = height.takeIf { it > 0 },
    frameRate = video?.let { knownFrameRate(it.frameRate) },
    bitrateKbps = video?.let { knownKbps(it.bitrate) },
    droppedFrames = droppedFrames,
    throughputKbps = knownKbps(bandwidthBps),
    bufferMs = bufferMs,
)
