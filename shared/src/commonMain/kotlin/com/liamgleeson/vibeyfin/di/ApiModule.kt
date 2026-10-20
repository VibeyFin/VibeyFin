package com.liamgleeson.vibeyfin.di

import com.liamgleeson.vibeyfin.data.api.ClientInfoProvider
import com.liamgleeson.vibeyfin.data.api.JellyfinApiFactory
import com.liamgleeson.vibeyfin.data.api.JellyfinHttpClient
import com.liamgleeson.vibeyfin.data.api.Timeouts
import com.liamgleeson.vibeyfin.data.api.UnauthorizedEvents
import com.liamgleeson.vibeyfin.domain.AuthRepository
import com.liamgleeson.vibeyfin.domain.ConnectionManager
import com.liamgleeson.vibeyfin.domain.LibraryRepository
import com.liamgleeson.vibeyfin.domain.PlaybackRecovery
import com.liamgleeson.vibeyfin.domain.PlaybackReporter
import com.liamgleeson.vibeyfin.domain.PlaybackRepository
import com.liamgleeson.vibeyfin.domain.ServerConnector
import com.liamgleeson.vibeyfin.domain.ServerResolver
import com.liamgleeson.vibeyfin.domain.StartupRouter
import com.liamgleeson.vibeyfin.domain.WatchChanges
import com.liamgleeson.vibeyfin.domain.WatchRepository
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
    single { StartupRouter(get(), get(), get(), get()) }
    single { LibraryRepository(get()) }
    single { WatchRepository(get(), get()) }

    single(ApplicationScope) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { PlaybackRepository(get(), get(), get(), get()) }
    single { WatchChanges() }
    single { PlaybackRecovery(get(), get()) }
    // One per player: it holds what that playback has reported so far.
    factory { PlaybackReporter(get(), get(ApplicationScope), get(), get(), get()) }
}
