package app.vibeyfin.domain

import app.vibeyfin.domain.model.ProfileRef

/** A download that finished: its own title, and the show's name when it is an episode. */
data class CompletedDownload(val title: String, val seriesName: String?)

/** Tells the user about downloads while the app is away: the platform turns these into notifications. */
interface DownloadAlerts {
    /** Every download in the queue is done; [downloads] finished for [profile]. */
    fun finished(profile: ProfileRef, downloads: List<CompletedDownload>)

    /** The download of [title] ended in [failure]. */
    fun failed(profile: ProfileRef, title: String, failure: DownloadFailure)
}

object NoDownloadAlerts : DownloadAlerts {
    override fun finished(profile: ProfileRef, downloads: List<CompletedDownload>): Unit = Unit

    override fun failed(profile: ProfileRef, title: String, failure: DownloadFailure): Unit = Unit
}
