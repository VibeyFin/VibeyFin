package app.vibeyfin.ios

import app.vibeyfin.domain.CompletedDownload
import app.vibeyfin.domain.DownloadAlerts
import app.vibeyfin.domain.DownloadFailure
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.downloads.downloadedTitle
import app.vibeyfin.ui.downloads.message

/** What to tell the user about downloads, worded here and handed to the app to post as a local notification. */
class DownloadAlert(val title: String, val body: String, val profile: ProfileRef)

class IosDownloadAlerts : DownloadAlerts {
    /** Set by the app at launch, before a background wake can bring an alert. */
    var handler: ((DownloadAlert) -> Unit)? = null

    override fun finished(profile: ProfileRef, downloads: List<CompletedDownload>) {
        handler?.invoke(DownloadAlert(downloadedTitle(downloads), Strings.downloadsReadyBody, profile))
    }

    override fun failed(profile: ProfileRef, title: String, failure: DownloadFailure) {
        handler?.invoke(DownloadAlert(Strings.downloadFailedTitle(title), failure.message(), profile))
    }
}
