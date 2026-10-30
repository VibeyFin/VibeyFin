package app.vibeyfin.data.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import app.vibeyfin.domain.DownloadNetwork
import app.vibeyfin.domain.DownloadScheduler
import java.util.concurrent.TimeUnit

/**
 * Runs [DownloadWorker] through WorkManager, which keeps the work across the app being closed, the phone being locked
 * or restarted, and starts it when the network is right. Work is appended to what is queued, so a request made while
 * the worker runs is never lost to the moment it finishes; each extra one finds nothing to do and ends at once.
 */
class AndroidDownloadScheduler(private val context: Context) : DownloadScheduler {
    override val isSupported = true

    override fun schedule(network: DownloadNetwork) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        when (network) {
                            // WorkManager stops running work when its network is lost, so work that must outlast that has none.
                            DownloadNetwork.None -> NetworkType.NOT_REQUIRED

                            DownloadNetwork.Any -> NetworkType.CONNECTED

                            DownloadNetwork.Unmetered -> NetworkType.UNMETERED
                        },
                    )
                    .build(),
            )
            // The user asked for it, so it starts at once and is allowed to run as a foreground service. Out of quota it still runs.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "downloads"
        const val BACKOFF_SECONDS = 30L
    }
}
