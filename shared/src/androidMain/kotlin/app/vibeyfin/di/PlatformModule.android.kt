package app.vibeyfin.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room3.Room
import androidx.room3.RoomDatabase
import app.vibeyfin.data.db.VibeyFinDatabase
import app.vibeyfin.data.discovery.AndroidMulticastLock
import app.vibeyfin.data.discovery.UdpLanDiscovery
import app.vibeyfin.data.download.AndroidDownloadFiles
import app.vibeyfin.data.download.AndroidDownloadScheduler
import app.vibeyfin.data.download.AndroidProgressSyncScheduler
import app.vibeyfin.data.platform.AndroidDeviceInfo
import app.vibeyfin.data.platform.AndroidNetworkMonitor
import app.vibeyfin.data.platform.AndroidPlaybackCapabilities
import app.vibeyfin.data.platform.DeviceInfo
import app.vibeyfin.data.prefs.createDevicePreferencesDataStore
import app.vibeyfin.data.vault.AndroidKeystoreTokenVault
import app.vibeyfin.data.vault.TokenVault
import app.vibeyfin.domain.DownloadFiles
import app.vibeyfin.domain.DownloadScheduler
import app.vibeyfin.domain.LanDiscovery
import app.vibeyfin.domain.NetworkMonitor
import app.vibeyfin.domain.PlaybackCapabilitiesSource
import app.vibeyfin.domain.ProgressSyncScheduler
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
    single<PlaybackCapabilitiesSource> { AndroidPlaybackCapabilities() }
    single<NetworkMonitor> { AndroidNetworkMonitor(androidContext()) }
    single<DownloadFiles> { AndroidDownloadFiles(androidContext()) }
    single<DownloadScheduler> { AndroidDownloadScheduler(androidContext()) }
    single<ProgressSyncScheduler> { AndroidProgressSyncScheduler(androidContext()) }
    single<LanDiscovery> { UdpLanDiscovery(AndroidMulticastLock(androidContext())) }
}
