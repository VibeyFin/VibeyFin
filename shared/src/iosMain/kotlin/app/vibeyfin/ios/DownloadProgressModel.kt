package app.vibeyfin.ios

import app.vibeyfin.data.download.IosDownloadScheduler
import app.vibeyfin.di.ApplicationScope
import app.vibeyfin.domain.DownloadScheduler
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.ui.downloads.DownloadStatus
import app.vibeyfin.ui.downloads.DownloadStatusTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

/**
 * The downloads as the app's system progress and notifications need them, for the whole app and not one screen. Calls back on the
 * main thread.
 */
class DownloadProgressModel {
    private val koin = KoinPlatform.getKoin()
    private val scope = koin.get<CoroutineScope>(ApplicationScope)
    private val scheduler = koin.get<DownloadScheduler>() as IosDownloadScheduler
    private val alerts = koin.get<IosDownloadAlerts>()
    private val settings = koin.get<ProfileSettings>()
    private var observing: Job? = null

    /** Calls [onChange] with the download in hand each time it moves, and with null when no download is going. */
    fun observe(onChange: (DownloadStatus?) -> Unit) {
        observing?.cancel()
        observing = scope.launch(Dispatchers.Main) {
            var tracker = DownloadStatusTracker()
            scheduler.progress.collect { progress ->
                if (progress == null) tracker = DownloadStatusTracker()
                onChange(progress?.let(tracker::update))
            }
        }
    }

    /** Calls [onAlert] when a queue finishes or a download fails, even in the background wake that finished it. */
    fun onAlert(onAlert: (DownloadAlert) -> Unit) {
        alerts.handler = { alert -> scope.launch(Dispatchers.Main) { onAlert(alert) } }
    }

    /** Calls [onResult] on the main thread with whether the signed-in profile wants download notifications. */
    fun alertsWanted(onResult: (Boolean) -> Unit) {
        scope.launch {
            val wanted = settings.downloadNotifications.first() == true
            withContext(Dispatchers.Main) { onResult(wanted) }
        }
    }
}
