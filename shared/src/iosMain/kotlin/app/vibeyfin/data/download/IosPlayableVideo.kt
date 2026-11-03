package app.vibeyfin.data.download

import app.vibeyfin.domain.PlayableVideo
import app.vibeyfin.domain.PlaybackCapabilitiesSource

/** A video plays when any of the engines (AVPlayer, then Aether) lists its codec. */
class IosPlayableVideo(private val capabilities: PlaybackCapabilitiesSource) : PlayableVideo {
    override fun canPlay(codec: String) = capabilities.candidates().any { codec.lowercase() in it.capabilities.videoCodecs }
}
