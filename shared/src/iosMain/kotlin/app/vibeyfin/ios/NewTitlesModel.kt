package app.vibeyfin.ios

import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.di.ApplicationScope
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.NewTitlesPoller
import app.vibeyfin.domain.ProfileNewTitles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

/**
 * New-title polling for the iOS background task: every saved profile with notifications on, each as itself, with no
 * profile current. The poll runs on the application scope, so it is not cancelled when the caller goes away.
 */
class NewTitlesModel {
    private val poller = KoinPlatform.getKoin().get<NewTitlesPoller>()
    private val scope = KoinPlatform.getKoin().get<CoroutineScope>(ApplicationScope)
    private val dao = KoinPlatform.getKoin().get<AccountDao>()
    private val connection = KoinPlatform.getKoin().get<ConnectionManager>()

    /** Polls every profile and calls [onResult] on the main thread with the profiles that have new titles (maybe none). */
    fun poll(onResult: (List<ProfileNewTitles>) -> Unit) {
        scope.launch {
            val result = poller.pollAll()
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    /**
     * Calls [onResult] on the main thread with how many saved profiles [poll] would ask for (notifications on, not
     * kids, a session). Zero means there is nothing to schedule; more than one means a notification names its profile.
     */
    fun notifiableProfileCount(onResult: (Int) -> Unit) {
        scope.launch {
            val count = dao.getNotifiableProfiles().size
            withContext(Dispatchers.Main) { onResult(count) }
        }
    }

    /** Whether this profile is the one signed in right now, so a tapped notification may open its content. */
    fun isSignedIn(serverId: String, userId: String): Boolean {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return false
        return account.serverId == serverId && account.userId == userId
    }
}
