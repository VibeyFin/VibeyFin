package app.vibeyfin.ui.downloads

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.Download
import app.vibeyfin.domain.DownloadFailure
import app.vibeyfin.domain.DownloadRunner
import app.vibeyfin.domain.DownloadState
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What the user can do to a download that exists, by its state. Screens word each with [Strings]. */
enum class DownloadAction { Pause, Resume, Remove }

/** Pause while it is on its way, resume when it was paused or failed, and remove whatever its state. */
val Download.actions: List<DownloadAction>
    get() = when (state) {
        DownloadState.Queued, DownloadState.Running, DownloadState.WaitingForNetwork -> listOf(DownloadAction.Pause, DownloadAction.Remove)
        DownloadState.Paused, DownloadState.Failed -> listOf(DownloadAction.Resume, DownloadAction.Remove)
        DownloadState.Done -> listOf(DownloadAction.Remove)
    }

val Download.isDone: Boolean get() = state == DownloadState.Done

/** What a download button or a badge on a picture shows: one picture for each, whatever the platform draws. */
enum class DownloadBadge {
    /** Nothing is downloaded: the plain download arrow. */
    None,

    /** Queued, or waiting for Wi-Fi, a connection or the server. */
    Waiting,

    /** Bytes are coming in: a ring that fills. */
    Running,
    Paused,
    Failed,

    /** On the device: a filled check, in ink (aqua never signals status). */
    Done,
}

val Download.badge: DownloadBadge
    get() = when (state) {
        DownloadState.Queued, DownloadState.WaitingForNetwork -> DownloadBadge.Waiting
        DownloadState.Running -> DownloadBadge.Running
        DownloadState.Paused -> DownloadBadge.Paused
        DownloadState.Failed -> DownloadBadge.Failed
        DownloadState.Done -> DownloadBadge.Done
    }

/** One badge for a show: what is on its way wins over what is done, so the show reads as busy while any episode is. */
fun List<Download>.badge(): DownloadBadge = when {
    isEmpty() -> DownloadBadge.None
    any { it.state == DownloadState.Running } -> DownloadBadge.Running
    any { it.state == DownloadState.Queued || it.state == DownloadState.WaitingForNetwork } -> DownloadBadge.Waiting
    any { it.state == DownloadState.Failed } -> DownloadBadge.Failed
    any { it.state == DownloadState.Paused } -> DownloadBadge.Paused
    else -> DownloadBadge.Done
}

/** How far these downloads are together, 0 to 1; null when none of their sizes is known. */
fun List<Download>.progress(): Float? {
    val total = sumOf { it.bytesTotal ?: 0L }
    if (total <= 0) return null
    return (sumOf { it.bytesDone }.toFloat() / total).coerceIn(0f, 1f)
}

/** The short word under a download button's icon. */
val DownloadBadge.label: String
    get() = when (this) {
        DownloadBadge.None -> Strings.download
        DownloadBadge.Waiting -> Strings.downloadLabelWaiting
        DownloadBadge.Running -> Strings.downloading
        DownloadBadge.Paused -> Strings.downloadPaused
        DownloadBadge.Failed -> Strings.downloadLabelFailed
        DownloadBadge.Done -> Strings.downloaded
    }

/** The bytes it takes, or will take: the file's size once known, what is here until then. */
val Download.bytes: Long get() = bytesTotal ?: bytesDone

/** The one line under a download: its state, and how far it is. [waiting] says what a download that is waiting waits for. */
fun Download.statusLine(waiting: DownloadRunner.Waiting): String = when (state) {
    DownloadState.Done -> Strings.downloadedWithSize(bytes)

    DownloadState.Running -> listOfNotNull(Strings.downloading, Strings.downloadProgress(bytesDone, bytesTotal), timeLeft?.let { Strings.downloadTimeLeft(it.inWholeSeconds) }).joinToString(" · ")

    DownloadState.Queued -> Strings.downloadQueued

    DownloadState.WaitingForNetwork -> when (waiting) {
        DownloadRunner.Waiting.Network -> Strings.downloadWaitingForNetwork
        DownloadRunner.Waiting.Wifi -> Strings.downloadWaitingForWifi
        DownloadRunner.Waiting.Server -> Strings.downloadWaitingForServer
    }

    DownloadState.Paused -> if (bytesDone > 0) "${Strings.downloadPaused} · ${Strings.downloadProgress(bytesDone, bytesTotal)}" else Strings.downloadPaused

    DownloadState.Failed -> Strings.downloadFailed
}

/** Why a failed download failed, with what to try; null for one that did not fail. */
val Download.failureMessage: String? get() = if (state == DownloadState.Failed) (failure ?: DownloadFailure.Unknown).message() else null

/** The word a screen reader says for the state of a download. */
val Download.stateWord: String
    get() = when (state) {
        DownloadState.Done -> Strings.downloaded
        DownloadState.Running -> Strings.downloading
        DownloadState.Queued, DownloadState.WaitingForNetwork -> Strings.downloadQueued
        DownloadState.Paused -> Strings.downloadPaused
        DownloadState.Failed -> Strings.downloadFailed
    }

fun DownloadFailure.message(): String = when (this) {
    DownloadFailure.Forbidden -> LoadMessages.DOWNLOAD_FORBIDDEN
    DownloadFailure.NoSpace -> LoadMessages.DOWNLOAD_NO_SPACE
    DownloadFailure.StorageLimit -> LoadMessages.DOWNLOAD_STORAGE_LIMIT
    DownloadFailure.SignIn -> LoadMessages.DOWNLOAD_SIGN_IN
    DownloadFailure.NotOnServer -> LoadMessages.DOWNLOAD_NOT_ON_SERVER
    DownloadFailure.Unplayable -> LoadMessages.DOWNLOAD_UNPLAYABLE
    DownloadFailure.Unknown -> LoadMessages.DOWNLOAD_INTERRUPTED
}

/** The message for a download that could not be asked for: the server said no, or could not be reached. */
internal fun unreachableMessage() = LoadMessages.forError(ApiError.Network)

/** The profile that is signed in now, or null while nobody is. */
internal val ConnectionManager.profile: Flow<ProfileRef?>
    get() = state.map { it.profileOrNull() }.distinctUntilChanged()

internal fun ConnectionState.profileOrNull(): ProfileRef? = (this as? ConnectionState.Connected)?.let { ProfileRef(it.server.id, it.account.userId) }
