package app.vibeyfin.di

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
 * process), in the background so that nothing waits for the network.
 */
fun initKoin(platform: KoinAppDeclaration = {}): KoinApplication = startKoin {
    platform()
    modules(platformModule(), appModule())
}.also { app ->
    val koin = app.koin
    koin.get<CoroutineScope>(ApplicationScope).launch { koin.get<PlaybackRecovery>().recoverOnStart() }
}
