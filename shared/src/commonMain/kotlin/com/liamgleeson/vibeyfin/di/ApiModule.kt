package com.liamgleeson.vibeyfin.di

import com.liamgleeson.vibeyfin.data.api.ClientInfoProvider
import com.liamgleeson.vibeyfin.data.api.JellyfinHttpClient
import com.liamgleeson.vibeyfin.data.api.Timeouts
import com.liamgleeson.vibeyfin.domain.ServerResolver
import org.koin.dsl.module

/** The Jellyfin client pieces that don't need a signed-in session. Needs a `DeviceInfo` from the platform. */
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
}
