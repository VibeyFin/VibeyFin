package app.vibeyfin.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room3.Room
import androidx.room3.RoomDatabase
import app.vibeyfin.data.db.VibeyFinDatabase
import app.vibeyfin.data.download.IosBackgroundTransfer
import app.vibeyfin.data.download.IosDownloadFiles
import app.vibeyfin.data.download.IosDownloadScheduler
import app.vibeyfin.data.download.IosPlayableVideo
import app.vibeyfin.data.platform.DeviceInfo
import app.vibeyfin.data.platform.IosDeviceInfo
import app.vibeyfin.data.platform.IosNetworkMonitor
import app.vibeyfin.data.platform.IosPlaybackCapabilities
import app.vibeyfin.data.prefs.createDevicePreferencesDataStore
import app.vibeyfin.data.vault.KeychainTokenVault
import app.vibeyfin.data.vault.TokenVault
import app.vibeyfin.domain.DownloadAlerts
import app.vibeyfin.domain.DownloadFiles
import app.vibeyfin.domain.DownloadRunner
import app.vibeyfin.domain.DownloadScheduler
import app.vibeyfin.domain.ExternalVideoTransfer
import app.vibeyfin.domain.LanDiscovery
import app.vibeyfin.domain.NetworkMonitor
import app.vibeyfin.domain.NoLanDiscovery
import app.vibeyfin.domain.NoProgressSyncScheduler
import app.vibeyfin.domain.PlayableVideo
import app.vibeyfin.domain.PlaybackCapabilitiesSource
import app.vibeyfin.domain.ProgressSyncScheduler
import app.vibeyfin.ios.IosDownloadAlerts
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
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
    // Until the downloads phase for iOS lands an NWPathMonitor, iOS never goes offline by itself.
    single<NetworkMonitor> { IosNetworkMonitor() }
    // Downloads: the files in Documents, so they show in the Files app under "On My iPhone / VibeyFin" (out of backups), and videos
    // through a background URLSession.
    single { IosDownloadFiles("${documentsDirectory()}/Downloads") }
    single<DownloadFiles> { get<IosDownloadFiles>() }
    single { IosBackgroundTransfer(get(), get()) }
    single<ExternalVideoTransfer> { get<IosBackgroundTransfer>() }
    // A download is kept as the server sent it; an engine plays it, so a codec no engine lists is refused before it is fetched.
    single<PlayableVideo> { IosPlayableVideo(get()) }
    single<DownloadScheduler> { IosDownloadScheduler(get(ApplicationScope), get()) { get<DownloadRunner>() } }
    single { IosDownloadAlerts() }
    single<DownloadAlerts> { get<IosDownloadAlerts>() }
    single<ProgressSyncScheduler> { NoProgressSyncScheduler }
}

// Documents is what the Files app shows of the app (with `UIFileSharingEnabled`); only the downloads go there.
@OptIn(ExperimentalForeignApi::class)
private fun documentsDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return checkNotNull(url?.path) { "Documents directory is unavailable." }
}

// Application Support is private to the app and, unlike Documents, not shown in the Files app.
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
