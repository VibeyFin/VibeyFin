package com.liamgleeson.vibeyfin.ui.home

import com.liamgleeson.vibeyfin.domain.ConnectionState
import com.liamgleeson.vibeyfin.testing.FakeServer
import com.liamgleeson.vibeyfin.testing.Reply
import com.liamgleeson.vibeyfin.testing.TestApp
import com.liamgleeson.vibeyfin.testing.ViewModelTracker
import io.ktor.http.HttpStatusCode
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
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
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
    }

    private fun viewModel() = viewModels.track(HomeViewModel(app.connection))

    private suspend fun signedIn(): FakeServer {
        val server = app.serverAt("http://nas:8096")
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex"}"""))
        server.on("POST", "/Sessions/Logout", Reply.noContent())
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.connection.activate("srv-1", "u1")
        return server
    }

    @Test
    fun nothingIsShownBeforeSignIn() = runTest {
        assertEquals(HomeUiState(), viewModel().uiState.value)
    }

    @Test
    fun showsWhoIsSignedInOnWhichServer() = runTest {
        signedIn()

        val state = viewModel().uiState.first { it.userName != null }

        assertEquals("Alex", state.userName)
        assertEquals("Home", state.serverName)
        assertEquals("srv-1", state.serverId)
        assertNull(state.exit)
    }

    @Test
    fun anEndedSessionSendsTheUserBackToWhoIsWatching() = runTest {
        app.serverAt("http://nas:8096").on("GET", "/Users/Me", Reply.status(HttpStatusCode.Unauthorized))
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.connection.activate("srv-1", "u1")

        val state = viewModel().uiState.first { it.exit != null }

        assertEquals(HomeExit.WhoIsWatching("srv-1"), state.exit)
    }

    @Test
    fun switchingProfileLeavesTheSessionSignedInAndGoesToWhoIsWatching() = runTest {
        signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }

        viewModel.onSwitchProfile()

        assertEquals(HomeExit.WhoIsWatching("srv-1"), viewModel.uiState.first { it.exit != null }.exit)
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
        assertEquals("token-1", app.accounts.sessionToken("srv-1", "u1"))
        assertEquals(0, app.jellyfin.requestsTo("POST", "/Sessions/Logout").size)
    }

    @Test
    fun switchingServerGoesToTheServersList() = runTest {
        signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }

        viewModel.onSwitchServer()

        assertEquals(HomeExit.Servers, viewModel.uiState.first { it.exit != null }.exit)
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
    }

    @Test
    fun signingOutRevokesTheTokenLocksTheProfileAndGoesToWhoIsWatching() = runTest {
        signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }

        viewModel.onSignOut()

        assertEquals(HomeExit.WhoIsWatching("srv-1"), viewModel.uiState.first { it.exit != null }.exit)
        assertEquals(1, app.jellyfin.requestsTo("POST", "/Sessions/Logout").size)
        assertNull(app.accounts.sessionToken("srv-1", "u1"))
        assertIs<ConnectionState.Disconnected>(app.connection.state.value)
    }

    @Test
    fun theMenuDoesNothingWhenNobodyIsSignedIn() = runTest {
        val viewModel = viewModel()

        viewModel.onSwitchProfile()
        viewModel.onSignOut()

        assertNull(viewModel.uiState.value.exit)
    }
}
