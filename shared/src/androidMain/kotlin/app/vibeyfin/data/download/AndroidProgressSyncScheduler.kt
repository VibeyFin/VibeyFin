package app.vibeyfin.data.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.vibeyfin.domain.OfflineSync
import app.vibeyfin.domain.ProgressSyncScheduler
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * Makes sure what was watched offline reaches the server, even if the app is closed first: WorkManager runs [ProgressSyncWorker]
 * once there is a connection, and again later with a back-off while the server cannot be reached. The work is a short request,
 * so it needs no foreground service.
 */
class AndroidProgressSyncScheduler(private val context: Context) : ProgressSyncScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<ProgressSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        // Appended, so a record saved while one runs is still picked up by the next.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "progress-sync"
        const val BACKOFF_SECONDS = 30L
    }
}

/** Sends the progress saved offline. Koin is already started by the application when the process was started for this. */
class ProgressSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (GlobalContext.get().get<OfflineSync>().syncAll()) Result.success() else Result.retry()
}
