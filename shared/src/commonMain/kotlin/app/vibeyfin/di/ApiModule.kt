package app.vibeyfin.di

import app.vibeyfin.data.api.ClientInfoProvider
import app.vibeyfin.data.api.JellyfinApiFactory
import app.vibeyfin.data.api.JellyfinHttpClient
import app.vibeyfin.data.api.ServerReachability
import app.vibeyfin.data.api.Timeouts
import app.vibeyfin.data.api.UnauthorizedEvents
import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.BingeCounter
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.DownloadRepository
import app.vibeyfin.domain.DownloadRunner
import app.vibeyfin.domain.DownloadSpeeds
import app.vibeyfin.domain.DownloadWake
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.LocalPlaybackReporter
import app.vibeyfin.domain.OfflineLibrary
import app.vibeyfin.domain.OfflineMode
import app.vibeyfin.domain.OfflineProgress
import app.vibeyfin.domain.OfflineSync
import app.vibeyfin.domain.PlaybackRecovery
import app.vibeyfin.domain.PlaybackReporter
import app.vibeyfin.domain.PlaybackRepository
import app.vibeyfin.domain.ServerConnector
import app.vibeyfin.domain.ServerResolver
import app.vibeyfin.domain.StartupRouter
import app.vibeyfin.domain.WatchChanges
import app.vibeyfin.domain.WatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Work that must outlive any screen, such as telling the server playback stopped. */
val ApplicationScope = named("applicationScope")

/** Talking to Jellyfin servers. Needs a `DeviceInfo` from the platform. */
fun apiModule() = module {
    single { ClientInfoProvider(get(), get()) }
    single {
        ServerResolver(
            JellyfinHttpClient.create(
                clientInfo = get<ClientInfoProvider>()::current,
                timeouts = Timeouts.Probe,
            ),
        )
    }

    single { UnauthorizedEvents() }
    single { ServerReachability() }
    single {
        JellyfinApiFactory(
            JellyfinHttpClient.create(
                clientInfo = get<ClientInfoProvider>()::current,
                timeouts = Timeouts.Api,
            ),
            unauthorized = get(),
            reachability = get(),
        )
    }
    single { ServerConnector(get(), get()) }
    single { ConnectionManager(get(), get(), get(), get(), get()) }
    single { OfflineMode(get(), get(), get(ApplicationScope)) }
    single { DownloadWake() }
    single { DownloadSpeeds() }
    single { OfflineLibrary(get(), get(), get()) }
    single { OfflineProgress(get(), get(), get()) }
    single { OfflineSync(get(), get(), get(), get()) }
    // One per player, like PlaybackReporter: it holds where this playback is.
    factory { LocalPlaybackReporter(get(), get(ApplicationScope), get(), get(), get(), get()) }
    single { DownloadRepository(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { DownloadRunner(get(), get(), get(), get(), get(), get(), get(), speeds = get(), external = getOrNull(), converter = getOrNull()) }
    single { AuthRepository(get(), get(), get()) }
    single { StartupRouter(get(), get(), get(), get(), get()) }
    single { LibraryRepository(get()) }
    single { WatchRepository(get(), get()) }

    single(ApplicationScope) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { PlaybackRepository(get(), get(), get(), get()) }
    single { WatchChanges() }
    single { PlaybackRecovery(get(), get()) }
    // One per player: it holds what that playback has reported so far.
    factory { PlaybackReporter(get(), get(ApplicationScope), get(), get(), get()) }

    // Singles: both must survive a new PlaybackViewModel.
    single { BingeCounter() }
}
