package app.vibeyfin.app.item

import app.vibeyfin.ui.downloads.DownloadAction

/** What the item page can do about downloads: the Download button, an episode's menu, and the questions. */
class DownloadCallbacks(
    /** The Download button on a movie's or an episode's page, when it has no download yet. */
    val onDownload: () -> Unit,
    val onDownloadEpisode: (String) -> Unit,
    /** Pause, resume or remove (after asking) the download of an item. */
    val onAction: (itemId: String, DownloadAction) -> Unit,
    val onDownloadSeries: () -> Unit,
    val onDownloadSeason: (seasonId: String) -> Unit,
    val onRemoveSeries: () -> Unit,
) {
    companion object {
        /** No downloads: for previews and tests. */
        val None = DownloadCallbacks({}, {}, { _, _ -> }, {}, {}, {})
    }
}
