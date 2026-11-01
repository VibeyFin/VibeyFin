package app.vibeyfin.data.platform

import app.vibeyfin.domain.EngineCapabilities
import app.vibeyfin.domain.PlaybackCapabilities
import app.vibeyfin.domain.PlaybackCapabilitiesSource
import app.vibeyfin.domain.PlaybackEngineChoice
import app.vibeyfin.ios.PlaybackEngineChoiceStore

class IosPlaybackCapabilities : PlaybackCapabilitiesSource {
    // PoC: the list follows the engine that will play, so the server direct plays what that engine can decode.
    override fun current() = candidates().first().capabilities

    override fun candidates() = when (PlaybackEngineChoiceStore.current) {
        PlaybackEngineChoice.Aether -> listOf(EngineCapabilities(PlaybackEngineChoice.Aether, AETHER))

        PlaybackEngineChoice.Native -> listOf(EngineCapabilities(PlaybackEngineChoice.Native, NATIVE))

        PlaybackEngineChoice.Auto -> listOf(
            EngineCapabilities(PlaybackEngineChoice.Native, NATIVE),
            EngineCapabilities(PlaybackEngineChoice.Aether, AETHER),
        )
    }

    private companion object {
        val NATIVE = PlaybackCapabilities(
            containers = listOf("mp4", "m4v", "mov"),
            videoCodecs = listOf("h264", "hevc"),
            audioCodecs = listOf("aac", "mp3", "ac3", "eac3", "alac"),
            // Text subtitles are fetched by the app and painted over the video, whatever the player; image-based ones (PGS, DVD, DVB) Aether decodes and the app paints.
            subtitleFormats = listOf("srt", "subrip", "ass", "ssa", "vtt"),
            fetchedSubtitleCodecs = listOf("subrip", "srt", "ass", "ssa", "webvtt", "mov_text"),
        )

        // Aether demuxes with FFmpeg and decodes with VideoToolbox, dav1d or libavcodec; its audio it stream-copies or bridges.
        // Text subtitles are fetched by the app and painted over the video, whatever the engine; image-based ones stay a server burn-in.
        val AETHER = PlaybackCapabilities(
            containers = listOf(
                "mp4", "m4v", "mov", "mkv", "webm", "avi", "ts", "mpegts", "m2ts", "wmv", "asf", "ogg", "ogv", "flv", "3gp", "3g2",
            ),
            videoCodecs = listOf(
                "h264", "hevc", "av1", "vp8", "vp9", "mpeg2video", "mpeg4", "msmpeg4v3", "vc1", "wmv3", "theora", "h263",
            ),
            audioCodecs = listOf(
                "aac", "mp3", "mp2", "ac3", "eac3", "dts", "truehd", "mlp", "flac", "opus", "vorbis", "alac", "wmav2", "wmapro",
                "pcm_s16le", "pcm_s24le",
            ),
            subtitleFormats = listOf("srt", "subrip", "ass", "ssa", "vtt"),
            fetchedSubtitleCodecs = listOf("subrip", "srt", "ass", "ssa", "webvtt", "mov_text"),
            embeddedSubtitleFormats = listOf("pgssub", "dvdsub", "dvbsub"),
            streamVideoCodecs = listOf("h264", "hevc"),
        )
    }
}
