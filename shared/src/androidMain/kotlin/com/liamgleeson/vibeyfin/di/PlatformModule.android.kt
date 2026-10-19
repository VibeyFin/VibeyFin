package com.liamgleeson.vibeyfin.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.liamgleeson.vibeyfin.data.db.VibeyFinDatabase
import com.liamgleeson.vibeyfin.data.discovery.AndroidMulticastLock
import com.liamgleeson.vibeyfin.data.discovery.UdpLanDiscovery
import com.liamgleeson.vibeyfin.data.platform.AndroidDeviceInfo
import com.liamgleeson.vibeyfin.data.platform.DeviceInfo
import com.liamgleeson.vibeyfin.data.prefs.createDevicePreferencesDataStore
import com.liamgleeson.vibeyfin.data.vault.AndroidKeystoreTokenVault
import com.liamgleeson.vibeyfin.data.vault.TokenVault
import com.liamgleeson.vibeyfin.domain.LanDiscovery
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<RoomDatabase.Builder<VibeyFinDatabase>> {
        val context = androidContext()
        Room.databaseBuilder<VibeyFinDatabase>(
            context = context,
            name = context.getDatabasePath("vibeyfin.db").absolutePath,
        )
    }
    single<DataStore<Preferences>> {
        createDevicePreferencesDataStore(
            androidContext().filesDir.resolve("device.preferences_pb").absolutePath,
        )
    }
    single<TokenVault> { AndroidKeystoreTokenVault() }
    single<DeviceInfo> { AndroidDeviceInfo(androidContext()) }
    single<LanDiscovery> { UdpLanDiscovery(AndroidMulticastLock(androidContext())) }
}
