package app.vibeyfin.data.platform

import app.vibeyfin.domain.PlaybackCapabilities
import app.vibeyfin.domain.PlaybackCapabilitiesSource

/**
 * What AVPlayer plays natively. It opens MP4-family files but not Matroska or WebM, so those are
 * remuxed to HLS by the server. HEVC is listed because every iPhone that can run iOS 16 decodes it
 * in hardware. DTS and TrueHD audio aren't listed; the server converts them.
 */
class IosPlaybackCapabilities : PlaybackCapabilitiesSource {
    override fun current() = PlaybackCapabilities(
        containers = listOf("mp4", "m4v", "mov"),
        videoCodecs = listOf("h264", "hevc"),
        audioCodecs = listOf("aac", "mp3", "ac3", "eac3", "alac"),
    )
}
