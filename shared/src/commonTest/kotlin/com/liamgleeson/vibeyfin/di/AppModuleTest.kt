package com.liamgleeson.vibeyfin.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.liamgleeson.vibeyfin.data.api.ClientInfoProvider
import com.liamgleeson.vibeyfin.data.db.VibeyFinDatabase
import com.liamgleeson.vibeyfin.data.platform.DeviceInfo
import com.liamgleeson.vibeyfin.data.vault.TokenVault
import com.liamgleeson.vibeyfin.domain.AccountRepository
import com.liamgleeson.vibeyfin.domain.AuthRepository
import com.liamgleeson.vibeyfin.domain.ConnectionManager
import com.liamgleeson.vibeyfin.domain.ConnectionState
import com.liamgleeson.vibeyfin.domain.DevicePreferences
import com.liamgleeson.vibeyfin.domain.LanDiscovery
import com.liamgleeson.vibeyfin.domain.PlaybackCapabilitiesSource
import com.liamgleeson.vibeyfin.domain.ServerRepository
import com.liamgleeson.vibeyfin.domain.ServerResolver
import com.liamgleeson.vibeyfin.testing.FakeDeviceInfo
import com.liamgleeson.vibeyfin.testing.FakeLanDiscovery
import com.liamgleeson.vibeyfin.testing.FakePlaybackCapabilities
import com.liamgleeson.vibeyfin.testing.FakeTokenVault
import com.liamgleeson.vibeyfin.testing.InMemoryPreferencesDataStore
import com.liamgleeson.vibeyfin.ui.addserver.AddServerViewModel
import com.liamgleeson.vibeyfin.ui.children.ChildrenViewModel
import com.liamgleeson.vibeyfin.ui.home.HomeViewModel
import com.liamgleeson.vibeyfin.ui.item.ItemViewModel
import com.liamgleeson.vibeyfin.ui.playback.PlaybackViewModel
import com.liamgleeson.vibeyfin.ui.servers.ServersViewModel
import com.liamgleeson.vibeyfin.ui.settings.SettingsViewModel
import com.liamgleeson.vibeyfin.ui.startup.StartupViewModel
import com.liamgleeson.vibeyfin.ui.whoswatching.WhoIsWatchingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppModuleTest {
    @BeforeTest
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMain() {
        Dispatchers.resetMain()
    }

    /** Stands in for [platformModule]: same bindings, but in memory. */
    private fun fakePlatformModule() = module {
        single<RoomDatabase.Builder<VibeyFinDatabase>> { Room.inMemoryDatabaseBuilder<VibeyFinDatabase>() }
        single<DataStore<Preferences>> { InMemoryPreferencesDataStore() }
        single<TokenVault> { FakeTokenVault() }
        single<DeviceInfo> { FakeDeviceInfo() }
        single<LanDiscovery> { FakeLanDiscovery() }
        single<PlaybackCapabilitiesSource> { FakePlaybackCapabilities() }
    }

    @Test
    fun appModuleResolvesEveryViewModel() = runTest {
        val koin = koinApplication { modules(fakePlatformModule(), appModule()) }.koin

        val viewModels = listOf<ViewModel>(
            koin.get<ServersViewModel>(),
            koin.get<AddServerViewModel>(),
            koin.get<WhoIsWatchingViewModel> { parametersOf("srv-1") },
            koin.get<HomeViewModel>(),
            koin.get<ChildrenViewModel> { parametersOf("folder-1") },
            koin.get<ItemViewModel> { parametersOf("item-1") },
            koin.get<PlaybackViewModel> { parametersOf("item-1") },
            koin.get<StartupViewModel>(),
            koin.get<SettingsViewModel>(),
        )

        // Some start loading as soon as they exist; stop them before the database goes.
        viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() }
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
        koin.get<AuthRepository>()
        assertEquals(ConnectionState.Disconnected, koin.get<ConnectionManager>().state.value)
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
