package com.liamgleeson.vibeyfin.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.liamgleeson.vibeyfin.data.api.ClientInfoProvider
import com.liamgleeson.vibeyfin.data.db.VibeyFinDatabase
import com.liamgleeson.vibeyfin.data.platform.DeviceInfo
import com.liamgleeson.vibeyfin.data.vault.TokenVault
import com.liamgleeson.vibeyfin.domain.AccountRepository
import com.liamgleeson.vibeyfin.domain.DevicePreferences
import com.liamgleeson.vibeyfin.domain.LanDiscovery
import com.liamgleeson.vibeyfin.domain.ServerRepository
import com.liamgleeson.vibeyfin.domain.ServerResolver
import com.liamgleeson.vibeyfin.testing.FakeDeviceInfo
import com.liamgleeson.vibeyfin.testing.FakeLanDiscovery
import com.liamgleeson.vibeyfin.testing.FakeTokenVault
import com.liamgleeson.vibeyfin.testing.InMemoryPreferencesDataStore
import com.liamgleeson.vibeyfin.ui.addserver.AddServerViewModel
import com.liamgleeson.vibeyfin.ui.servers.ServersViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class AppModuleTest {
    /** Stands in for [platformModule]: same bindings, but in memory. */
    private fun fakePlatformModule() = module {
        single<RoomDatabase.Builder<VibeyFinDatabase>> { Room.inMemoryDatabaseBuilder<VibeyFinDatabase>() }
        single<DataStore<Preferences>> { InMemoryPreferencesDataStore() }
        single<TokenVault> { FakeTokenVault() }
        single<DeviceInfo> { FakeDeviceInfo() }
        single<LanDiscovery> { FakeLanDiscovery() }
    }

    @Test
    fun appModuleResolvesEveryViewModel() {
        val koin = koinApplication { modules(fakePlatformModule(), appModule()) }.koin

        koin.get<ServersViewModel>()
        koin.get<AddServerViewModel>()

        koin.get<VibeyFinDatabase>().close()
    }

    @Test
    fun theStorageGraphResolvesAndWorksEndToEnd() = runTest {
        val koin = koinApplication { modules(fakePlatformModule(), appModule()) }.koin

        val servers = koin.get<ServerRepository>()
        val accounts = koin.get<AccountRepository>()
        val preferences = koin.get<DevicePreferences>()

        servers.save("srv", "Home", "10.10.7", "http://nas:8096")
        accounts.saveSession("srv", "u1", "Alex", null, "token-1")

        assertEquals("token-1", accounts.sessionToken("srv", "u1"))
        assertEquals("Home", servers.servers.first().single().name)
        assertTrue(preferences.deviceId().isNotBlank())

        koin.get<VibeyFinDatabase>().close()
    }

    @Test
    fun theApiGraphResolvesAndBuildsTheClientInfoFromTheDevice() = runTest {
        val koin = koinApplication { modules(fakePlatformModule(), appModule()) }.koin

        koin.get<ServerResolver>()
        val info = koin.get<ClientInfoProvider>().current()

        assertEquals("Test phone", info.deviceName)
        assertEquals("1.0.0", info.appVersion)
        assertEquals(koin.get<DevicePreferences>().deviceId(), info.deviceId)
        assertEquals("VibeyFin", info.clientName)
    }

    @Test
    fun eachKoinApplicationGetsItsOwnSingletons() {
        val first = koinApplication { modules(fakePlatformModule(), appModule()) }.koin
        val second = koinApplication { modules(fakePlatformModule(), appModule()) }.koin

        assertNotSame(first.get<VibeyFinDatabase>(), second.get<VibeyFinDatabase>())
        assertNotSame(first.get<ServerRepository>(), second.get<ServerRepository>())

        first.get<VibeyFinDatabase>().close()
        second.get<VibeyFinDatabase>().close()
    }
}
