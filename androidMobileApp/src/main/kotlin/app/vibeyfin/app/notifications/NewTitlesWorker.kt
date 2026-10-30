package app.vibeyfin.app.notifications

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.domain.NewTitlesPoller
import app.vibeyfin.domain.ProfileNewTitles
import kotlinx.coroutines.CancellationException
import org.koin.core.context.GlobalContext

/** What [NewTitlesWorker] works with. The app takes it from Koin; tests set [NewTitlesWork.override]. */
class NewTitlesDependencies(
    val pollAll: suspend () -> List<ProfileNewTitles>,
    /** How many profiles get notifications, so a notification names its profile when there is more than one. */
    val notifyingProfiles: suspend () -> Int,
    val canPost: () -> Boolean,
    val publisher: NewTitlesPublisher,
    /** Where a failed run is noted (Logcat in the app). */
    val log: (String) -> Unit,
)

object NewTitlesWork {
    private const val TAG = "NewTitlesWorker"

    @VisibleForTesting
    @Volatile
    var override: NewTitlesDependencies? = null

    fun dependencies(context: Context): NewTitlesDependencies = override ?: run {
        val koin = GlobalContext.get()
        val poller = koin.get<NewTitlesPoller>()
        val dao = koin.get<AccountDao>()

        NewTitlesDependencies(
            pollAll = poller::pollAll,
            notifyingProfiles = { dao.getNotifiableProfiles().size },
            canPost = { canPostNotifications(context) },
            publisher = NotificationPublisher(context),
            log = { Log.w(TAG, it) },
        )
    }
}

/**
 * Asks every profile with notifications on for new titles ([NewTitlesPoller.pollAll]) and posts them. The poller marks
 * a title as notified before it is posted, so the work never retries: a failed run returns success and the next
 * periodic run carries on. While the app cannot post (permission refused or notifications blocked in system settings)
 * it does not poll at all: no network for nothing, and titles stay unmarked, so after a grant the next run still posts
 * what arrived in the poller's window (grouped per show).
 */
class NewTitlesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val work = NewTitlesWork.dependencies(applicationContext)

        try {
            if (!work.canPost()) return Result.success()

            val found = work.pollAll()

            if (found.isNotEmpty()) work.publisher.post(found, nameProfiles = work.notifyingProfiles() > 1)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // A background check must never crash the worker. Only the kind of failure: a message could carry a server address or a title
            work.log("New-title check failed: ${e::class.simpleName}")
        }

        return Result.success()
    }
}
