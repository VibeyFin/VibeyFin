package app.vibeyfin.ios

import app.vibeyfin.domain.Download
import app.vibeyfin.domain.DownloadRunner
import app.vibeyfin.ui.downloads.DownloadAction
import app.vibeyfin.ui.downloads.DownloadBadge
import app.vibeyfin.ui.downloads.DownloadStatusLine
import app.vibeyfin.ui.downloads.actions
import app.vibeyfin.ui.downloads.badge
import app.vibeyfin.ui.downloads.failureMessage
import app.vibeyfin.ui.downloads.isDone
import app.vibeyfin.ui.downloads.stateWord
import app.vibeyfin.ui.downloads.status

/**
 * What the shared code works out about a [Download] (the words, the actions, the badge), for SwiftUI: Kotlin extension properties
 * are not visible from Swift, so each is asked for here, and the screens keep no rules of their own.
 */
object DownloadBridge {
    fun actions(download: Download): List<DownloadAction> = download.actions

    fun badge(download: Download): DownloadBadge = download.badge

    fun isDone(download: Download): Boolean = download.isDone

    fun status(download: Download, waiting: DownloadRunner.Waiting): DownloadStatusLine = download.status(waiting)

    fun failureMessage(download: Download): String? = download.failureMessage

    fun stateWord(download: Download): String = download.stateWord
}
