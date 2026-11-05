package app.vibeyfin.ui.downloads

import app.vibeyfin.domain.DownloadRunner
import app.vibeyfin.ui.common.Strings

/** What the system shows: a title and second line for the download in hand, and the bytes of the whole queue for the bar. */
class DownloadStatus(val title: String, val subtitle: String, val queueDone: Long, val queueTotal: Long)

/** Turns the runner's per-download [DownloadRunner.Progress] reports into the [DownloadStatus] to show. */
class DownloadStatusTracker {
    private var last: DownloadStatus? = null
    private var finishedBytes: Long = 0
    private var finishedCount: Int = 0
    private var currentId: String? = null
    private var currentDone: Long = 0
    private var currentTotal: Long = 0

    fun update(progress: DownloadRunner.Progress): DownloadStatus {
        val waiting: DownloadRunner.Waiting? = progress.waiting
        val previous: DownloadStatus? = last
        val status: DownloadStatus = if (waiting != null) {
            DownloadStatus(
                previous?.title ?: Strings.downloadingNotificationTitle(progress.title),
                waitingText(waiting),
                previous?.queueDone ?: 0,
                previous?.queueTotal ?: 0,
            )
        } else {
            moveTo(progress)
            DownloadStatus(
                Strings.downloadingNotificationTitle(progress.title),
                Strings.downloadSizeProgress(progress.bytesDone, progress.bytesTotal),
                finishedBytes + currentDone,
                queueTotal(progress.remaining),
            )
        }
        last = status
        return status
    }

    private fun moveTo(progress: DownloadRunner.Progress) {
        if (progress.itemId != currentId) {
            if (currentTotal > 0 && currentDone >= currentTotal) {
                finishedBytes += currentTotal
                finishedCount++
            }
            currentId = progress.itemId
        }
        currentDone = progress.bytesDone
        currentTotal = progress.bytesTotal ?: 0
    }

    private fun queueTotal(remaining: Int): Long {
        val knownCount: Int = finishedCount + if (currentTotal > 0) 1 else 0
        val average: Long = if (knownCount > 0) (finishedBytes + currentTotal) / knownCount else 0
        return finishedBytes + maxOf(currentTotal, currentDone) + (remaining - 1).coerceAtLeast(0) * average
    }

    private fun waitingText(waiting: DownloadRunner.Waiting): String = when (waiting) {
        DownloadRunner.Waiting.Network -> Strings.downloadWaitingForNetwork
        DownloadRunner.Waiting.Wifi -> Strings.downloadWaitingForWifi
        DownloadRunner.Waiting.Server -> Strings.downloadWaitingForServer
    }
}
