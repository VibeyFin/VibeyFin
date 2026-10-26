package app.vibeyfin.di

import app.vibeyfin.domain.DownloadRepository
import app.vibeyfin.domain.NetworkMonitor
import app.vibeyfin.domain.OfflineSync
import app.vibeyfin.domain.PlaybackRecovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts Koin once per process. Each platform entry point calls this; [platform] is for what
 * must be set before modules load (Android: `androidContext`). [platformModule] is added here.
 *
 * It also sends the `Stopped` of any play session an earlier run left behind (a crash, a killed
 * process), in the background so that nothing waits for the network, and starts the background work again for
 * downloads an earlier run left unfinished, and starts sending what was watched offline whenever the server can be reached.
 */
fun initKoin(platform: KoinAppDeclaration = {}): KoinApplication = startKoin {
    platform()
    modules(platformModule(), appModule())
}.also { app ->
    val koin = app.koin
    koin.get<CoroutineScope>(ApplicationScope).launch { koin.get<PlaybackRecovery>().recoverOnStart() }
    koin.get<CoroutineScope>(ApplicationScope).launch { koin.get<DownloadRepository>().resumePending() }
    // What was watched offline is sent now, and again whenever a connection comes back.
    koin.get<OfflineSync>().start(koin.get<CoroutineScope>(ApplicationScope), koin.get<NetworkMonitor>())
}
