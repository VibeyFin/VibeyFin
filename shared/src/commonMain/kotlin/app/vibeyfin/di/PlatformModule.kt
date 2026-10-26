package app.vibeyfin.di

import org.koin.core.module.Module

/**
 * Bindings only a platform can provide: the Room builder for its file location, the DataStore,
 * the [app.vibeyfin.data.vault.TokenVault] and the
 * [app.vibeyfin.data.platform.DeviceInfo] and the
 * [app.vibeyfin.domain.PlaybackCapabilitiesSource] and the
 * [app.vibeyfin.domain.NetworkMonitor]. Android's needs `androidContext()` set on
 * the Koin application first.
 */
expect fun platformModule(): Module
