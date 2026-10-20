package com.liamgleeson.vibeyfin.ui.common

import com.liamgleeson.vibeyfin.data.api.ApiError
import com.liamgleeson.vibeyfin.ui.whoswatching.SignInMessages

/** What went wrong loading something from the server and what to try, worded once for every platform. */
internal object LoadMessages {
    const val FORBIDDEN = "This account isn't allowed to see that. Ask the server's admin for access."
    const val NOT_FOUND = "That isn't on the server any more. It may have been removed or moved."
    const val UNEXPECTED = "The server sent something VibeyFin couldn't read. Try again, and update the server if it keeps happening."

    const val NO_COMPATIBLE_STREAM =
        "The server can't play this on your device. Ask the server's admin to check the file's format."
    const val TRANSCODING_NEEDED =
        "This video needs re-encoding to play on your device, and video transcoding is off. Turn on Allow video transcoding in Settings."
    const val PLAYBACK_FAILED = "This video wouldn't play. Try again, or ask the server's admin to check the file."
    const val MARK_WATCHED_FAILED = "Couldn't mark this as watched. Check your connection and try again."
    const val MARK_UNWATCHED_FAILED = "Couldn't mark this as unwatched. Check your connection and try again."
    const val PLAYBACK_NETWORK = "Lost the connection to the server. Check your network and try again."

    fun forError(error: ApiError): String = when (error) {
        ApiError.Network -> SignInMessages.UNREACHABLE
        ApiError.Unauthorized -> SignInMessages.SESSION_ENDED
        ApiError.Forbidden -> FORBIDDEN
        ApiError.NotFound -> NOT_FOUND
        is ApiError.Server -> "The server answered with an error (HTTP ${error.status}). Try again in a minute."
        is ApiError.Unexpected, ApiError.BadResponse -> UNEXPECTED
    }
}
