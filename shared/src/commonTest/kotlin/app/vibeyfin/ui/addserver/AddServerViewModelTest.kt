package app.vibeyfin.ui.addserver

import app.vibeyfin.data.db.VibeyFinDatabase
import app.vibeyfin.domain.ConnectionIssue
import app.vibeyfin.domain.ServerRepository
import app.vibeyfin.testing.FakeClock
import app.vibeyfin.testing.FakeTokenVault
import app.vibeyfin.testing.MockJellyfinNetwork
import app.vibeyfin.testing.Route
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.inMemoryDatabase
import app.vibeyfin.testing.jellyfin
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class AddServerViewModelTest {
    private lateinit var database: VibeyFinDatabase
    private lateinit var clock: FakeClock
    private lateinit var servers: ServerRepository
    private var network = MockJellyfinNetwork()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = inMemoryDatabase()
        clock = FakeClock()
        servers = ServerRepository(database.serverDao(), database.accountDao(), FakeTokenVault(), clock)
    }

    private val viewModels = ViewModelTracker()

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        Dispatchers.resetMain()
        database.close()
    }

    private fun viewModel(vararg routes: Pair<String, Route>, initialAddress: String? = null): AddServerViewModel {
        network = MockJellyfinNetwork(*routes)
        return viewModels.track(AddServerViewModel(initialAddress, network.resolver(), servers))
    }

    // Types [address], taps Connect, and waits for the check to finish.
    private suspend fun AddServerViewModel.connect(address: String): AddServerStep {
        onAddressChange(address)
        onConnect()
        return uiState.first { it.step !is AddServerStep.Checking }.step
    }

    @Test
    fun connectIsOnlyAvailableOnceThereIsAnAddress() = runTest {
        val viewModel = viewModel()

        assertFalse(viewModel.uiState.value.canConnect)
        viewModel.onAddressChange("   ")
        assertFalse(viewModel.uiState.value.canConnect)
        viewModel.onAddressChange("nas")
        assertTrue(viewModel.uiState.value.canConnect)
    }

    @Test
    fun connectingShowsTheServerThatWasFound() = runTest {
        val viewModel = viewModel("https://nas" to jellyfin(id = "srv-1", name = "Home", version = "10.10.7"))

        val step = assertIs<AddServerStep.Found>(viewModel.connect("nas"))

        assertEquals("Home", step.server.name)
        assertEquals("10.10.7", step.server.version)
        assertEquals("https://nas", step.server.address)
        assertFalse(step.alreadySaved)
    }

    @Test
    fun aWrongAddressShowsTheSpecificIssue() = runTest {
        val unreachable = viewModel().connect("nas")
        val invalid = viewModel().connect("ftp://nas")

        assertEquals(AddServerStep.Failed(ConnectionIssue.Unreachable), unreachable)
        assertEquals(AddServerStep.Failed(ConnectionIssue.InvalidAddress), invalid)
    }

    @Test
    fun nothingIsRequestedForABlankAddress() = runTest {
        val viewModel = viewModel("https://nas" to jellyfin())

        viewModel.onAddressChange("  ")
        viewModel.onConnect()

        assertEquals(AddServerStep.Idle, viewModel.uiState.value.step)
        assertTrue(network.requested.isEmpty())
    }

    @Test
    fun editingTheAddressDiscardsTheResult() = runTest {
        val viewModel = viewModel("https://nas" to jellyfin())
        viewModel.connect("nas")

        viewModel.onAddressChange("nas2")

        assertEquals(AddServerStep.Idle, viewModel.uiState.value.step)
        assertEquals("nas2", viewModel.uiState.value.address)
    }

    @Test
    fun confirmingSavesTheServerWithEveryAddressThatWorked() = runTest {
        val viewModel = viewModel(
            "https://nas" to jellyfin(id = "srv-1", name = "Home"),
            "http://nas:8096" to jellyfin(id = "srv-1", name = "Home"),
        )
        viewModel.connect("nas")

        viewModel.onConfirm()
        viewModel.uiState.first { it.saved }

        val saved = servers.get("srv-1")!!
        assertEquals("Home", saved.name)
        assertEquals(listOf("https://nas", "http://nas:8096"), saved.addresses)
    }

    @Test
    fun confirmingWithoutAResultSavesNothing() = runTest {
        val viewModel = viewModel("https://nas" to jellyfin())

        viewModel.onConfirm()

        assertFalse(viewModel.uiState.value.saved)
        assertNull(servers.get("srv-1"))
    }

    @Test
    fun aServerThatIsAlreadySavedIsMergedNotDuplicated() = runTest {
        servers.save("srv-1", "Home", "10.10.6", "http://192.168.1.20:8096")
        clock.advance(1.minutes)
        val viewModel = viewModel("https://jelly.example.com" to jellyfin(id = "srv-1", name = "Home", version = "10.11.0"))

        val step = assertIs<AddServerStep.Found>(viewModel.connect("https://jelly.example.com:443"))
        assertTrue(step.alreadySaved)
        assertTrue(step.addressIsNew)

        viewModel.onConfirm()
        viewModel.uiState.first { it.saved }

        val all = servers.servers.first()
        assertEquals(1, all.size)
        assertEquals(listOf("https://jelly.example.com", "http://192.168.1.20:8096"), all.single().addresses)
        assertEquals("10.11.0", all.single().version)
    }

    @Test
    fun theSameAddressAgainIsRecognisedAsNotNew() = runTest {
        servers.save("srv-1", "Home", "10.10.7", "http://nas:8096")
        val viewModel = viewModel("http://nas:8096" to jellyfin(id = "srv-1"))

        val step = assertIs<AddServerStep.Found>(viewModel.connect("http://nas:8096"))

        assertTrue(step.alreadySaved)
        assertFalse(step.addressIsNew)
    }

    @Test
    fun anInitialAddressIsCheckedStraightAway() = runTest {
        val viewModel = viewModel("http://192.168.1.20:8096" to jellyfin(name = "Home"), initialAddress = "http://192.168.1.20:8096")

        val state = viewModel.uiState.first { it.step is AddServerStep.Found }

        assertEquals("http://192.168.1.20:8096", state.address)
        assertEquals("Home", (state.step as AddServerStep.Found).server.name)
    }

    @Test
    fun aFailedInitialAddressShowsTheIssueAndStaysEditable() = runTest {
        val viewModel = viewModel(initialAddress = "http://192.168.1.20:8096")

        val state = viewModel.uiState.first { it.step is AddServerStep.Failed }

        assertEquals(AddServerStep.Failed(ConnectionIssue.Unreachable), state.step)
        assertEquals("http://192.168.1.20:8096", state.address)
        viewModel.onAddressChange("http://192.168.1.21:8096")
        assertEquals(AddServerStep.Idle, viewModel.uiState.value.step)
    }

    @Test
    fun withoutAnInitialAddressNothingIsCheckedUntilAsked() = runTest {
        val viewModel = viewModel("http://nas:8096" to jellyfin())

        assertEquals(AddServerUiState(), viewModel.uiState.value)
        assertTrue(network.requested.isEmpty())
    }

    @Test
    fun aDifferentServerAtANewAddressIsASecondEntry() = runTest {
        servers.save("srv-1", "Home", "10.10.7", "http://nas:8096")
        val viewModel = viewModel("http://other:8096" to jellyfin(id = "srv-2", name = "Other"))

        viewModel.connect("http://other:8096")
        viewModel.onConfirm()
        viewModel.uiState.first { it.saved }

        assertEquals(listOf("srv-1", "srv-2"), servers.servers.first().map { it.id })
    }
}
