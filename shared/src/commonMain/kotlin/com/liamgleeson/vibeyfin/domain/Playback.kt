package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.api.ApiError

/** How the server delivers the item to this device. The names are what Jellyfin's session reports expect. */
enum class PlayMethod(val wireName: String) {
    /** The file itself, untouched. */
    DirectPlay("DirectPlay"),

    /** The same video and audio in a different container, which is cheap for the server. */
    DirectStream("DirectStream"),

    /** The server re-encodes it. */
    Transcode("Transcode"),
}

/** One audio or subtitle track the user can pick. */
data class PlaybackTrack(
    /** The server's index of the track in the file. */
    val index: Int,
    /** The name the server (or the file) gives it, such as `English - Dolby Digital - 5.1`; null if it gives none. */
    val name: String?,
    val selected: Boolean,
)

/** A text subtitle the server sends as a file, for the player to load next to the video. */
data class SubtitleFile(
    /** The subtitle track's index, as in [PlaybackTrack.index]. */
    val index: Int,
    val url: String,
    /** `text/vtt`, `application/x-subrip` or `text/x-ssa`. */
    val mimeType: String,
    val language: String?,
    val label: String?,
)

/** What the player needs to start one item, and what the progress reports need to name it. */
data class PlaybackPlan(
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String,
    val method: PlayMethod,
    /** A direct URL, or an HLS playlist for a stream the server prepares. */
    val url: String,
    /** Sent with every request the player makes for [url]. */
    val headers: Map<String, String>,
    val runtimeMs: Long?,
    /** Every audio track in the file, in the file's order. */
    val audioTracks: List<PlaybackTrack> = emptyList(),
    /** Every subtitle track, without "off". */
    val subtitleTracks: List<PlaybackTrack> = emptyList(),
    /** Every subtitle the server can send as a file, so the player can switch between them without asking again. */
    val subtitleFiles: List<SubtitleFile> = emptyList(),
    /** The chosen subtitle is burned into the video by the server, so changing it means a new stream. */
    val subtitleBurnedIn: Boolean = false,
    /** The audio and subtitle tracks this stream plays, for the reports; null for none. */
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    /** The picture of the file, for the player's format badges; null when the server doesn't describe a video stream. */
    val videoFormat: VideoFormat? = null,
)

/** The outcome of asking the server how to play an item. */
sealed interface PlaybackNegotiation {
    data class Ready(val plan: PlaybackPlan) : PlaybackNegotiation

    /** The server found no way to deliver the item to this device. */
    data object NoCompatibleStream : PlaybackNegotiation

    /** Only re-encoding the video would play it, and the profile doesn't allow that. */
    data object NeedsTranscoding : PlaybackNegotiation

    data class Failed(val error: ApiError) : PlaybackNegotiation
}

/** Jellyfin counts positions in 100-nanosecond ticks. */
internal const val TICKS_PER_MILLISECOND = 10_000L
