package app.vibeyfin.domain

import kotlinx.coroutines.flow.StateFlow

/** What [VideoConverter.convert] did with a downloaded video. */
sealed interface VideoConversion {
    /** The player can open the file as it is. */
    data object NotNeeded : VideoConversion

    /** The converted file is at the target path; the original at the source path is no longer needed. */
    data object Converted : VideoConversion

    /** [DownloadRunner] asked it to stop; nothing is changed. */
    data object Stopped : VideoConversion

    /** The video itself is in a codec the device cannot decode, and converting it would mean re-encoding it. */
    data object Unsupported : VideoConversion

    /** The file could not be read or written. */
    data object Failed : VideoConversion
}

/**
 * Makes a downloaded video something the platform's player opens, once, on the device (iOS: AVPlayer cannot open MKV, or DTS or TrueHD
 * audio, so the container is rewritten as MP4 with the video copied and only such audio converted to AAC). Paths are relative to the
 * downloads folder. Where the player takes the original, there is none bound.
 */
interface VideoConverter {
    /**
     * Looks at the file at [source] and, if the player cannot open it, writes a playable one to [target]. [onProgress] gets 0 to 1;
     * [stop] ends the work early.
     */
    suspend fun convert(source: String, target: String, stop: StateFlow<Boolean>, onProgress: suspend (Float) -> Unit): VideoConversion

    /** Whether a video with this codec (`h264`, `hevc`, `vp9`…) can be played, after converting everything else about the file. */
    fun canPlayVideo(codec: String): Boolean
}
