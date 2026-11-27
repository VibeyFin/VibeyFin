package app.vibeyfin.di

import app.vibeyfin.data.platform.DeviceInfo
import app.vibeyfin.data.seerr.SeerrApiFactory
import app.vibeyfin.data.seerr.SeerrHttpClient
import app.vibeyfin.dev.SkeletonGate
import app.vibeyfin.domain.SeerrConnection
import app.vibeyfin.domain.SeerrRepository
import app.vibeyfin.domain.SeerrStore
import org.koin.dsl.module

/** Talking to Seerr: its own HTTP client, the profile's Seerr session, and the repository features use. */
fun seerrModule() = module {
    single { SeerrApiFactory(SeerrHttpClient.create(gate = get<SkeletonGate>().takeIf { get<DeviceInfo>().debugBuild })) }
    single { SeerrStore(get(), get(), get()) }
    single { SeerrConnection(get(), get(), get(), get(ApplicationScope)) }
    single { SeerrRepository(get()) }
}
