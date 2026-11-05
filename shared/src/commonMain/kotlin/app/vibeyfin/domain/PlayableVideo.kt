package app.vibeyfin.domain

/** Whether a video can be played on the device once downloaded, known before the first byte is fetched. */
interface PlayableVideo {
    /** Whether a video with this codec (`h264`, `hevc`, `vp9`…) plays. */
    fun canPlay(codec: String): Boolean
}
