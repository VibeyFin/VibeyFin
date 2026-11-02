package app.vibeyfin.ui.servers

import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.ui.common.Strings
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ServerDetailsViewModelTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()

    @BeforeTest
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app.servers.save("srv-1", "Home", "10.11.2", "http://192.168.1.20:8096", otherAddresses = listOf("https://jf.example.com"))
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "t1")
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        Dispatchers.resetMain()
        app.close()
    }

    private fun viewModel() = viewModels.track(ServerDetailsViewModel("srv-1", app.servers, app.accounts, app.serverRemoval))

    @Test
    fun showsTheAddressesBestFirstAndTheProfilesHere() = runTest {
        val state = viewModel().uiState.first { !it.loading }

        assertEquals("Home", state.name)
        assertEquals("10.11.2", state.version)
        assertEquals(listOf("http://192.168.1.20:8096", "https://jf.example.com"), state.addresses.map { it.address })
        assertEquals(listOf(true, false), state.addresses.map { it.lastWorked })
        assertEquals(1, state.profileCount)
        assertTrue(state.canRemoveAddress)
        assertEquals(Strings.removeServerTitle("Home"), state.removeTitle)
    }

    @Test
    fun removingAnAddressLeavesTheOthersAndTheLastOneStays() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }

        viewModel.onRemoveAddress("https://jf.example.com")

        val state = viewModel.uiState.first { it.addresses.size == 1 }
        assertFalse(state.canRemoveAddress)
        viewModel.onRemoveAddress("http://192.168.1.20:8096")
        assertEquals(listOf("http://192.168.1.20:8096"), app.servers.get("srv-1")!!.addresses)
    }

    @Test
    fun removingTheServerAsksFirstThenCloses() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }

        viewModel.onRemoveServer()
        assertTrue(viewModel.uiState.first { it.confirmRemove }.confirmRemove)
        viewModel.onConfirmRemove()

        assertTrue(viewModel.uiState.first { it.removed }.removed)
        assertNull(app.servers.get("srv-1"))
    }

    @Test
    fun saidNoKeepsTheServer() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }

        viewModel.onRemoveServer()
        viewModel.onDismissRemove()

        assertFalse(viewModel.uiState.first { !it.confirmRemove }.removed)
        assertEquals("Home", app.servers.get("srv-1")?.name)
    }
}
