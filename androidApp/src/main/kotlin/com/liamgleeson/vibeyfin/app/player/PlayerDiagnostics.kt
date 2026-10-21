package com.liamgleeson.vibeyfin.app.player

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import com.liamgleeson.vibeyfin.domain.LiveDiagnostics
import com.liamgleeson.vibeyfin.ui.common.Strings

/** A short codec name for a MIME type: `video/avc` is "h264", `audio/mp4a-latm` is "aac". Unknown types show their subtype. */
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
    videoCodec = Strings.codecName(video?.sampleMimeType),
    audioCodec = Strings.codecName(audio?.sampleMimeType),
    width = width.takeIf { it > 0 },
    height = height.takeIf { it > 0 },
    frameRate = video?.let { knownFrameRate(it.frameRate) },
    bitrateKbps = video?.let { knownKbps(it.bitrate) },
    droppedFrames = droppedFrames,
    throughputKbps = knownKbps(bandwidthBps),
    bufferMs = bufferMs,
)
