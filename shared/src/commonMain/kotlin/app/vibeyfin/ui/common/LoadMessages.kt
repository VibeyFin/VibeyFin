package app.vibeyfin.ui.common

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.ui.whoswatching.SignInMessages

/** What went wrong loading something from the server and what to try, worded once for every platform. */
internal object LoadMessages {
    const val FORBIDDEN = "This account isn't allowed to see that. Ask the server's admin for access."
    const val NOT_FOUND = "That isn't on the server any more. It may have been removed or moved."
    const val UNEXPECTED = "The server sent something VibeyFin couldn't read. Try again, and update the server if it keeps happening."

    const val NO_COMPATIBLE_STREAM =
        "The server can't play this on your device. Ask the server's admin to check the file's format."
    const val PLAYBACK_FAILED = "This video wouldn't play. Try again, or ask the server's admin to check the file."
    const val ADD_TO_MY_LIST_FAILED = "Couldn't add this to My List. Check your connection and try again."
    const val REMOVE_FROM_MY_LIST_FAILED = "Couldn't remove this from My List. Check your connection and try again."
    const val MARK_WATCHED_FAILED = "Couldn't mark this as watched. Check your connection and try again."
    const val MARK_UNWATCHED_FAILED = "Couldn't mark this as unwatched. Check your connection and try again."
    const val DOWNLOAD_FORBIDDEN =
        "This account isn't allowed to download. Ask the server's admin to turn downloads on for it."
    const val DOWNLOAD_NO_SPACE = "There isn't enough space on this device. Remove a download or free some space, then try again."
    const val DOWNLOAD_STORAGE_LIMIT = "This would go over your storage limit for downloads. Remove a download or raise the limit in Settings."
    const val DOWNLOAD_INTERRUPTED = "The download stopped. Check your connection and try again."
    const val DOWNLOAD_SIGN_IN = "Sign in again to go on with this download."
    const val DOWNLOAD_NOT_ON_SERVER = "This is no longer on the server, so it can't be downloaded."
    const val DOWNLOAD_UNPLAYABLE = "This video is in a format this device can't play, so it can't be downloaded here."
    const val DOWNLOAD_NO_FILE = "This has no file to download."
    const val NOT_DOWNLOADED = "This isn't downloaded, so it can't open without a connection."
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
