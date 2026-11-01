package app.vibeyfin.domain

/**
 * Which player the iOS app uses for a video. The order is the order of the Settings picker; the [key] is what DataStore keeps.
 */
// PoC: remove with poc/playback-engine
enum class PlaybackEngineChoice(val key: String) {
    /** Each video plays in AVPlayer when the server can direct play it there, else in Aether when it can, else as a server stream. */
    Auto("auto"),
    Native("native"),
    Aether("aether"),
    ;

    companion object {
        /** Auto for a missing or unknown key. */
        fun fromKey(key: String?): PlaybackEngineChoice = entries.firstOrNull { it.key == key } ?: Auto
    }
}
