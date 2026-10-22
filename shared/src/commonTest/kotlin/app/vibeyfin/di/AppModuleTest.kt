package app.vibeyfin.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room3.Room
import androidx.room3.RoomDatabase
import app.vibeyfin.data.api.ClientInfoProvider
import app.vibeyfin.data.db.VibeyFinDatabase
import app.vibeyfin.data.platform.DeviceInfo
import app.vibeyfin.data.vault.TokenVault
import app.vibeyfin.domain.AccountRepository
import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.DevicePreferences
import app.vibeyfin.domain.LanDiscovery
import app.vibeyfin.domain.PlaybackCapabilitiesSource
import app.vibeyfin.domain.ServerRepository
import app.vibeyfin.domain.ServerResolver
import app.vibeyfin.testing.FakeDeviceInfo
import app.vibeyfin.testing.FakeLanDiscovery
import app.vibeyfin.testing.FakePlaybackCapabilities
import app.vibeyfin.testing.FakeTokenVault
import app.vibeyfin.testing.InMemoryPreferencesDataStore
import app.vibeyfin.ui.addserver.AddServerViewModel
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.ui.children.ChildrenViewModel
import app.vibeyfin.ui.filtered.FilteredListViewModel
import app.vibeyfin.ui.home.HomeViewModel
import app.vibeyfin.ui.item.ItemViewModel
import app.vibeyfin.ui.playback.PlaybackViewModel
import app.vibeyfin.ui.servers.ServersViewModel
import app.vibeyfin.ui.settings.SettingsViewModel
import app.vibeyfin.ui.startup.StartupViewModel
import app.vibeyfin.ui.whoswatching.WhoIsWatchingViewModel
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
            koin.get<FilteredListViewModel> { parametersOf(HomeFilter.Movies) },
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
