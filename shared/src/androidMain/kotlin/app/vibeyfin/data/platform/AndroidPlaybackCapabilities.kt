package app.vibeyfin.data.platform

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import app.vibeyfin.domain.PlaybackCapabilities
import app.vibeyfin.domain.PlaybackCapabilitiesSource

/**
 * What Media3's ExoPlayer can decode here. H.264 and AAC are always there. HEVC, VP9 and AV1 depend
 * on the phone, and only count when a hardware decoder exists: software decoding them is too slow
 * to be worth offering over a server transcode. Dolby and DTS audio is listed because the player
 * carries an ffmpeg audio decoder; audio is light enough to decode in software.
 */
class AndroidPlaybackCapabilities : PlaybackCapabilitiesSource {
    private val capabilities by lazy {
        PlaybackCapabilities(
            containers = listOf("mp4", "m4v", "mov", "mkv", "webm"),
            videoCodecs = buildList {
                add("h264")
                if (hasHardwareDecoder("video/hevc")) add("hevc")
                if (hasHardwareDecoder("video/x-vnd.on2.vp9")) add("vp9")
                if (hasHardwareDecoder("video/av01")) add("av1")
            },
            // Dolby and DTS audio is decoded by the ffmpeg extension in the player, so the server can leave it alone.
            audioCodecs = listOf("aac", "mp3", "opus", "flac", "vorbis", "ac3", "eac3", "dts", "truehd"),
            // Media3 reads these as side-loaded text tracks. Image-based subtitles aren't listed.
            subtitleFormats = listOf("srt", "vtt", "ass", "ssa"),
        )
    }

    override fun current() = capabilities

    private fun hasHardwareDecoder(mimeType: String): Boolean =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } && info.isHardware()
        }

    private fun MediaCodecInfo.isHardware(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isHardwareAccelerated
        } else {
            // Before Android 10 there is no flag; Android's own software decoders have these names.
            !name.startsWith("OMX.google.") && !name.startsWith("c2.android.")
        }
}
