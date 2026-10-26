package app.vibeyfin.app.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** The two things the scheduler asks of WorkManager, so it can be checked without one. */
interface UniqueWork {
    fun enqueue(name: String, policy: ExistingPeriodicWorkPolicy, request: PeriodicWorkRequest)

    fun cancel(name: String)
}

private class WorkManagerUniqueWork(private val workManager: WorkManager) : UniqueWork {
    override fun enqueue(name: String, policy: ExistingPeriodicWorkPolicy, request: PeriodicWorkRequest) {
        workManager.enqueueUniquePeriodicWork(name, policy, request)
    }

    override fun cancel(name: String) {
        workManager.cancelUniqueWork(name)
    }
}

/** Runs [NewTitlesWorker] every few hours while any profile gets notifications, and stops it when none does. */
object NewTitlesScheduler {
    const val WORK_NAME = "new-titles"
    const val INTERVAL_HOURS = 4L

    fun apply(context: Context, enabled: Boolean) = apply(WorkManagerUniqueWork(WorkManager.getInstance(context)), enabled)

    fun apply(work: UniqueWork, enabled: Boolean) {
        // UPDATE: work already queued keeps its timer (re-applying at every start does not reset it), and a changed
        // interval or constraint still reaches installs that have the work.
        if (enabled) work.enqueue(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request()) else work.cancel(WORK_NAME)
    }

    /**
     * Applies [notifyingProfiles] (how many profiles get notifications) for as long as [scope] lives: at app start, and
     * whenever a toggle, a sign-out, an expired session or a removed profile changes it.
     */
    fun follow(context: Context, notifyingProfiles: Flow<Int>, scope: CoroutineScope) {
        val appContext = context.applicationContext
        scope.launch { changes(notifyingProfiles).collect { apply(appContext, it) } }
    }

    /** Whether the work should run, each time that changes: on while any profile gets notifications. */
    fun changes(notifyingProfiles: Flow<Int>): Flow<Boolean> = notifyingProfiles.map { it > 0 }.distinctUntilChanged()

    fun request(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<NewTitlesWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
}
