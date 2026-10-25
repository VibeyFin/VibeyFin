package app.vibeyfin.di

import app.vibeyfin.data.api.ClientInfoProvider
import app.vibeyfin.data.api.JellyfinApiFactory
import app.vibeyfin.data.api.JellyfinHttpClient
import app.vibeyfin.data.api.Timeouts
import app.vibeyfin.data.api.UnauthorizedEvents
import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.BingeCounter
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.NowPlaying
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
    single {
        JellyfinApiFactory(
            JellyfinHttpClient.create(
                clientInfo = get<ClientInfoProvider>()::current,
                timeouts = Timeouts.Api,
            ),
            unauthorized = get(),
        )
    }
    single { ServerConnector(get(), get()) }
    single { ConnectionManager(get(), get(), get(), get(), get()) }
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
    single { NowPlaying() }
}
