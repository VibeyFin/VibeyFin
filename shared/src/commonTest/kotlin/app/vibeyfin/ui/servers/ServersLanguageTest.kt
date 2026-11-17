package app.vibeyfin.ui.servers

import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.FakeLanDiscovery
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ServersLanguageTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        Dispatchers.resetMain()
        app.close()
        TestLanguages.reset()
    }

    @Test
    fun theServerLineFollowsTheLanguage() = runTest {
        TestLanguages.installGerman("server_meta_addresses" to "{{count}} Adressen")
        app.servers.save("srv-1", "Home", "10.11.2", "https://nas", otherAddresses = listOf("http://nas:8096"))
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "t1")
        app.accounts.saveSession("srv-1", "u2", "Kim", null, "t2")
        val viewModel = viewModels.track(ServersViewModel(app.servers, app.accounts, FakeLanDiscovery(), app.serverRemoval))
        val before = viewModel.uiState.first { it.servers.isNotEmpty() }
        assertEquals("Jellyfin 10.11.2 · 2 addresses · 2 profiles", before.servers.single().meta)

        Texts.select("de")

        val after = viewModel.uiState.first { it != before }
        assertEquals("Jellyfin 10.11.2 · 2 Adressen · 2 profiles", after.servers.single().meta)
    }
}
