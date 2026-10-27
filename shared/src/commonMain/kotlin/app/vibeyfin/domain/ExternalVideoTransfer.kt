package app.vibeyfin.domain

import kotlinx.coroutines.flow.StateFlow

/** Everything a platform needs to fetch one video by itself. */
class ExternalTransferRequest(
    /** Names the transfer across restarts: the item's folder (`server/profile/item`). */
    val key: String,
    /** The server address that answered, and the token to sign the request with. */
    val baseUrl: String,
    val token: String?,
    val itemId: String,
    /** Where the bytes go, relative to the downloads folder: `…/video.part`, which becomes the video once complete. */
    val partPath: String,
    /** The profile allows mobile data, so the transfer may use it. */
    val allowsCellular: Boolean,
)

/** How an [ExternalVideoTransfer] ended. */
sealed interface ExternalTransferResult {
    /** The whole file is at the request's [ExternalTransferRequest.partPath]. */
    data object Finished : ExternalTransferResult

    /** [DownloadRunner] asked it to stop (a pause, a removal, a network the profile may not use); what was fetched is kept to go on from. */
    data object Stopped : ExternalTransferResult

    /** The connection was lost: try again when there is one. */
    data object Interrupted : ExternalTransferResult

    /** The server answered with an error that may pass. */
    data object ServerError : ExternalTransferResult

    /** It cannot go on. */
    data class Failed(val failure: DownloadFailure) : ExternalTransferResult
}

/**
 * Fetches a video in the platform's own way, for a platform whose background transfers are not ours to run (iOS: a background
 * URLSession, which the system carries on with the app suspended or closed). [DownloadRunner] hands it the video and waits; the
 * pictures and subtitle files, the storage rules and the bookkeeping stay the runner's. If the platform finishes while the app is
 * away, the file is already at the part path when the runner next runs, and it completes the download from there.
 */
interface ExternalVideoTransfer {
    /** Starts the transfer, or picks up one that is already running for [ExternalTransferRequest.key], and waits for it to end. [stop] asks it to stop. */
    suspend fun transfer(
        request: ExternalTransferRequest,
        stop: StateFlow<Boolean>,
        onProgress: suspend (done: Long, total: Long?) -> Unit,
    ): ExternalTransferResult
}
