package app.vibeyfin.di

import app.vibeyfin.domain.DownloadRepository
import app.vibeyfin.domain.NetworkMonitor
import app.vibeyfin.domain.OfflineSync
import app.vibeyfin.domain.PlaybackRecovery
import app.vibeyfin.i18n.AppLocale
import app.vibeyfin.i18n.StringLayers
import app.vibeyfin.i18n.Texts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts Koin once per process. Each platform entry point calls this; [platform] is for what
 * must be set before modules load (Android: `androidContext`). [platformModule] is added here. [stringLayers] are the text sets the
 * client loads, in override order.
 *
 * It also sends the `Stopped` of any play session an earlier run left behind (a crash, a killed
 * process), in the background so that nothing waits for the network, and starts the background work again for
 * downloads an earlier run left unfinished, and starts sending what was watched offline whenever the server can be reached.
 */
fun initKoin(stringLayers: List<String> = StringLayers.mobileClient, platform: KoinAppDeclaration = {}): KoinApplication = startKoin {
    Texts.useLayers(stringLayers)
    platform()
    modules(platformModule(), appModule())
}.also { app ->
    val koin = app.koin
    koin.get<AppLocale>().start(koin.get<CoroutineScope>(ApplicationScope))
    koin.get<CoroutineScope>(ApplicationScope).launch { koin.get<PlaybackRecovery>().recoverOnStart() }
    koin.get<CoroutineScope>(ApplicationScope).launch { koin.get<DownloadRepository>().resumePending() }
    // What was watched offline is sent now, and again whenever a connection comes back.
    koin.get<OfflineSync>().start(koin.get<CoroutineScope>(ApplicationScope), koin.get<NetworkMonitor>())
}
