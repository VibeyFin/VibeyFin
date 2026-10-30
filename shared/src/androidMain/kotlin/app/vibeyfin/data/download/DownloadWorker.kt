package app.vibeyfin.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.vibeyfin.domain.DownloadNetwork
import app.vibeyfin.domain.DownloadRunner
import app.vibeyfin.domain.DownloadScheduler
import kotlinx.coroutines.CancellationException
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.minutes

/**
 * Runs the downloads in the background, as a foreground service with a progress notification, so that they go on with the
 * screen off or the app closed. The process may have been started just for this: Koin has been started by the
 * application, and [DownloadRunner] reads everything else from the database.
 *
 * If Android stops the worker (a time limit on foreground work, or the system's own needs) the download is left as it was,
 * and WorkManager runs it again; the next run asks the server for the rest of the file.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val notifications = DownloadNotifications(applicationContext)

    override suspend fun getForegroundInfo(): ForegroundInfo = notifications.info(null)

    override suspend fun doWork(): Result {
        val koin = GlobalContext.get()
        val runner = koin.get<DownloadRunner>()
        // Android may refuse to start a foreground service for work that began in the background; the work still runs.
        showNotification(null)
        // Waits in the foreground service for a network, Wi-Fi or the server before giving up, so that it keeps the service.
        val next = runner.run(patience = PATIENCE) { progress -> showNotification(progress) }
        return when (next) {
            DownloadRunner.Next.Done -> Result.success()

            DownloadRunner.Next.WhenUnmetered -> {
                koin.get<DownloadScheduler>().schedule(DownloadNetwork.Unmetered)
                Result.success()
            }

            DownloadRunner.Next.WhenConnected -> {
                koin.get<DownloadScheduler>().schedule(DownloadNetwork.Any)
                Result.success()
            }

            DownloadRunner.Next.Retry -> Result.retry()
        }
    }

    private companion object {
        val PATIENCE = 30.minutes
    }

    // Whether the worker is in the foreground; only then is the notification the service's own, and can be updated in place.
    private var foreground = false

    private suspend fun showNotification(progress: DownloadRunner.Progress?) {
        try {
            if (foreground) {
                // WorkManager does not reliably redraw the notification on a second setForeground, so it is updated directly.
                notifications.update(progress)
            } else {
                setForeground(notifications.info(progress))
                foreground = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Not allowed to run in the foreground right now.
        }
    }
}
