package app.vibeyfin.ui.downloads

import app.vibeyfin.domain.CompletedDownload
import app.vibeyfin.ui.common.Strings

/** The title of the notification for finished downloads: one by name, several episodes of one show under the show, otherwise a count. */
fun downloadedTitle(downloads: List<CompletedDownload>): String {
    val show: String? = downloads.map { it.seriesName }.distinct().singleOrNull()
    return when {
        downloads.size == 1 -> Strings.downloadedTitle(downloads.single().title)
        show != null -> Strings.downloadedEpisodes(downloads.size, show)
        else -> Strings.downloadedVideos(downloads.size)
    }
}
