package app.vibeyfin.ios

import app.vibeyfin.di.ApplicationScope
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

    /** Polls every profile and calls [onResult] on the main thread with the profiles that have new titles (maybe none). */
    fun poll(onResult: (List<ProfileNewTitles>) -> Unit) {
        scope.launch {
            val result = poller.pollAll()
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }
}
