package com.liamgleeson.vibeyfin.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.liamgleeson.vibeyfin.data.db.VibeyFinDatabase
import com.liamgleeson.vibeyfin.data.platform.DeviceInfo
import com.liamgleeson.vibeyfin.data.platform.IosDeviceInfo
import com.liamgleeson.vibeyfin.data.platform.IosPlaybackCapabilities
import com.liamgleeson.vibeyfin.data.prefs.createDevicePreferencesDataStore
import com.liamgleeson.vibeyfin.data.vault.KeychainTokenVault
import com.liamgleeson.vibeyfin.data.vault.TokenVault
import com.liamgleeson.vibeyfin.domain.LanDiscovery
import com.liamgleeson.vibeyfin.domain.NoLanDiscovery
import com.liamgleeson.vibeyfin.domain.PlaybackCapabilitiesSource
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual fun platformModule(): Module = module {
    single<RoomDatabase.Builder<VibeyFinDatabase>> {
        Room.databaseBuilder<VibeyFinDatabase>(name = "${appSupportDirectory()}/vibeyfin.db")
    }
    single<DataStore<Preferences>> {
        createDevicePreferencesDataStore("${appSupportDirectory()}/device.preferences_pb")
    }
    single<TokenVault> { KeychainTokenVault() }
    single<DeviceInfo> { IosDeviceInfo() }
    single<PlaybackCapabilitiesSource> { IosPlaybackCapabilities() }
    // Broadcasting needs a multicast entitlement Apple has to approve, so iOS adds servers by address.
    single<LanDiscovery> { NoLanDiscovery }
}

/** Application Support is private to the app and, unlike Documents, not shown in the Files app. */
@OptIn(ExperimentalForeignApi::class)
private fun appSupportDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return checkNotNull(url?.path) { "Application Support directory is unavailable." }
}
