package app.vibeyfin.ui.servers

import app.vibeyfin.domain.DiscoveredServer
import app.vibeyfin.testing.FakeLanDiscovery
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ServersViewModelTest {
    private val app = TestApp()
    private val servers = app.servers
    private val discovery = FakeLanDiscovery()

    private val home = DiscoveredServer("srv-1", "Home", "http://192.168.1.20:8096")
    private val office = DiscoveredServer("srv-2", "Office", "http://192.168.1.30:8096")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private val viewModels = ViewModelTracker()

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        Dispatchers.resetMain()
        app.close()
    }

    private fun viewModel() = viewModels.track(ServersViewModel(servers, app.accounts, discovery, app.serverRemoval))

    @Test
    fun startsLoadingSoTheEmptyStateDoesNotFlash() {
        assertTrue(viewModel().uiState.value.loading)
    }

    @Test
    fun aFreshInstallHasNoServers() = runTest {
        val state = viewModel().uiState.first { !it.loading && !it.searching }

        assertEquals(emptyList(), state.servers)
        assertEquals(emptyList(), state.discovered)
    }

    @Test
    fun listsSavedServersWithTheirBestAddressFirst() = runTest {
        servers.save("srv-1", "Home", "10.10.7", "https://nas", otherAddresses = listOf("http://nas:8096"))
        val viewModel = viewModel()

        val state = viewModel.uiState.first { !it.loading }

        assertEquals(listOf("Home"), state.servers.map { it.server.name })
        assertEquals(listOf("https://nas", "http://nas:8096"), state.servers.single().server.addresses)
    }

    @Test
    fun aServerSavedLaterAppears() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }

        servers.save("srv-1", "Home", "10.10.7", "http://nas:8096")

        val state = viewModel.uiState.first { it.servers.isNotEmpty() }
        assertEquals("Home", state.servers.single().server.name)
    }

    // Discovery

    @Test
    fun searchesAsSoonAsTheScreenOpens() = runTest {
        discovery.servers = listOf(home, office)

        val state = viewModel().uiState.first { !it.loading && !it.searching }

        assertEquals(1, discovery.searches)
        assertEquals(listOf(home, office), state.discovered)
        assertTrue(state.canSearch)
    }

    @Test
    fun showsServersAsTheyAnswerAndSearchingUntilTheSearchEnds() = runTest {
        val finish = CompletableDeferred<Unit>()
        discovery.servers = listOf(home)
        discovery.hold = finish

        val viewModel = viewModel()
        val during = viewModel.uiState.first { it.discovered.isNotEmpty() }
        assertTrue(during.searching)
        assertEquals(listOf(home), during.discovered)

        finish.complete(Unit)
        assertFalse(viewModel.uiState.first { !it.searching }.searching)
    }

    @Test
    fun aServerThatIsAlreadySavedIsNotOfferedAgain() = runTest {
        servers.save("srv-1", "Home", "10.10.7", "http://192.168.1.20:8096")
        discovery.servers = listOf(home, office)

        val state = viewModel().uiState.first { !it.loading && !it.searching }

        assertEquals(listOf(office), state.discovered)
        assertEquals(listOf("srv-1"), state.servers.map { it.server.id })
    }

    @Test
    fun aDiscoveredServerDisappearsOnceItIsSaved() = runTest {
        discovery.servers = listOf(home, office)
        val viewModel = viewModel()
        viewModel.uiState.first { it.discovered.size == 2 }

        servers.save("srv-1", "Home", "10.10.7", "http://192.168.1.20:8096")

        val state = viewModel.uiState.first { it.discovered.size == 1 }
        assertEquals(listOf(office), state.discovered)
    }

    @Test
    fun searchingAgainStartsOverWithFreshResults() = runTest {
        discovery.servers = listOf(home)
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading && !it.searching && it.discovered.isNotEmpty() }

        discovery.servers = listOf(office)
        viewModel.onSearchAgain()

        val state = viewModel.uiState.first { !it.searching && it.discovered == listOf(office) }
        assertEquals(2, discovery.searches)
        assertEquals(listOf(office), state.discovered)
    }

    @Test
    fun aServerAnsweringTwiceIsListedOnce() = runTest {
        discovery.servers = listOf(home, home)

        val state = viewModel().uiState.first { !it.loading && !it.searching }

        assertEquals(listOf(home), state.discovered)
    }

    @Test
    fun whereSearchingIsNotSupportedTheScreenNeverSearches() = runTest {
        val unsupported = FakeLanDiscovery(servers = listOf(home), isSupported = false)

        val state = viewModels.track(ServersViewModel(servers, app.accounts, unsupported, app.serverRemoval)).uiState.first { !it.loading }

        assertEquals(0, unsupported.searches)
        assertFalse(state.canSearch)
        assertFalse(state.searching)
        assertEquals(emptyList(), state.discovered)
    }

    @Test
    fun aSearchThatFailsJustEndsWithNothingFound() = runTest {
        discovery.failure = IllegalStateException("radio off")

        val state = viewModel().uiState.first { !it.loading && !it.searching }

        assertEquals(emptyList(), state.discovered)
    }

    // Saved servers and removing one

    @Test
    fun eachServerSaysHowManyAddressesAndProfilesItHas() = runTest {
        servers.save("srv-1", "Home", "10.11.2", "https://nas", otherAddresses = listOf("http://nas:8096"))
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "t1")
        app.accounts.saveSession("srv-1", "u2", "Kim", null, "t2")

        val row = viewModel().uiState.first { it.servers.isNotEmpty() }.servers.single()

        assertEquals(2, row.profileCount)
        assertEquals("Jellyfin 10.11.2 \u00b7 2 addresses \u00b7 2 profiles", row.meta)
    }

    @Test
    fun removingAServerAsksFirstThenForgetsItAndItsProfiles() = runTest {
        servers.save("srv-1", "Home", "10.11.2", "http://nas:8096")
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "t1")
        val viewModel = viewModel()
        viewModel.uiState.first { it.servers.isNotEmpty() }

        viewModel.onRemoveServer("srv-1")
        assertEquals("srv-1", viewModel.uiState.first { it.confirmRemove != null }.confirmRemove!!.server.id)
        viewModel.onConfirmRemove()

        val state = viewModel.uiState.first { it.servers.isEmpty() }
        assertEquals(null, state.confirmRemove)
        assertEquals(null, app.accounts.account("srv-1", "u1"))
    }

    @Test
    fun dismissingTheQuestionKeepsTheServer() = runTest {
        servers.save("srv-1", "Home", "10.11.2", "http://nas:8096")
        val viewModel = viewModel()
        viewModel.uiState.first { it.servers.isNotEmpty() }

        viewModel.onRemoveServer("srv-1")
        viewModel.onDismissRemove()

        assertEquals(null, viewModel.uiState.value.confirmRemove)
        assertEquals("Home", servers.get("srv-1")?.name)
    }
}
