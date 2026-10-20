package com.liamgleeson.vibeyfin.di

import androidx.room3.RoomDatabase
import com.liamgleeson.vibeyfin.data.db.VibeyFinDatabase
import com.liamgleeson.vibeyfin.data.db.buildVibeyFinDatabase
import com.liamgleeson.vibeyfin.data.prefs.DataStoreDevicePreferences
import com.liamgleeson.vibeyfin.domain.AccountRepository
import com.liamgleeson.vibeyfin.domain.DevicePreferences
import com.liamgleeson.vibeyfin.domain.PendingReports
import com.liamgleeson.vibeyfin.domain.ProfileSettings
import com.liamgleeson.vibeyfin.domain.ServerRepository
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
    single { PendingReports(get(), get()) }
}
