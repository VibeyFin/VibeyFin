package app.vibeyfin.ios

import app.vibeyfin.domain.OfflineMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import org.koin.mp.KoinPlatform

/** Whether the app is offline (no network, or Offline mode on), for the shell that decides which tabs and which Home to show. */
class OfflineModeModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    /** Calls [onChange] with the current value now and with every change. */
    fun observe(onChange: (Boolean) -> Unit) {
        job?.cancel()
        job = KoinPlatform.getKoin().get<OfflineMode>().isOffline.onEach { onChange(it) }.launchIn(scope)
    }

    fun close() {
        job?.cancel()
    }
}
