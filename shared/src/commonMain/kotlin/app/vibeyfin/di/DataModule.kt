package app.vibeyfin.di

import androidx.room3.RoomDatabase
import app.vibeyfin.data.db.VibeyFinDatabase
import app.vibeyfin.data.db.buildVibeyFinDatabase
import app.vibeyfin.data.prefs.DataStoreDevicePreferences
import app.vibeyfin.domain.AccountRepository
import app.vibeyfin.domain.DevicePreferences
import app.vibeyfin.domain.PendingReports
import app.vibeyfin.domain.PinLock
import app.vibeyfin.domain.ProfilePolicy
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.domain.SearchHistory
import app.vibeyfin.domain.ServerRepository
import org.koin.dsl.module
import kotlin.time.Clock

/** Storage and repositories. Needs [platformModule] for the builder, DataStore and vault. */
fun dataModule() = module {
    single<Clock> { Clock.System }

    single { get<RoomDatabase.Builder<VibeyFinDatabase>>().buildVibeyFinDatabase() }
    single { get<VibeyFinDatabase>().serverDao() }
    single { get<VibeyFinDatabase>().accountDao() }
    single { get<VibeyFinDatabase>().pendingReportDao() }

    single<DevicePreferences> { DataStoreDevicePreferences(get()) }
    single { ServerRepository(get(), get(), get(), get()) }
    single { AccountRepository(get(), get(), get()) }
    single { ProfileSettings(get(), get()) }
    single { PinLock(get(), get(), get()) }
    single { ProfilePolicy(get()) }
    single { SearchHistory(get(), get()) }
    single { PendingReports(get(), get()) }
}
