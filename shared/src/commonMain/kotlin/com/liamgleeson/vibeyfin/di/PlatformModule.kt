package com.liamgleeson.vibeyfin.di

import org.koin.core.module.Module

/**
 * Bindings only a platform can provide: the Room builder for its file location, the DataStore,
 * the [com.liamgleeson.vibeyfin.data.vault.TokenVault] and the
 * [com.liamgleeson.vibeyfin.data.platform.DeviceInfo] and the
 * [com.liamgleeson.vibeyfin.domain.PlaybackCapabilitiesSource]. Android's needs `androidContext()` set on
 * the Koin application first.
 */
expect fun platformModule(): Module
