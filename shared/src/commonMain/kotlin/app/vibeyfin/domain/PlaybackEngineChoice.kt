package app.vibeyfin.domain

/** The player a stream plays in on iOS. The negotiation picks it per stream, in the order the capabilities source lists them. */
enum class PlaybackEngineChoice {
    /** AVPlayer, for what iOS decodes itself. */
    Native,

    /** Aether, which demuxes with FFmpeg for containers and codecs AVPlayer can't play. */
    Aether,
}
