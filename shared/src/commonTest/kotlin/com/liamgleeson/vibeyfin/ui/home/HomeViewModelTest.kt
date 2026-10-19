package com.liamgleeson.vibeyfin.ui.home

import com.liamgleeson.vibeyfin.domain.ConnectionState
import com.liamgleeson.vibeyfin.testing.FakeServer
import com.liamgleeson.vibeyfin.testing.Reply
import com.liamgleeson.vibeyfin.testing.TestApp
import com.liamgleeson.vibeyfin.testing.ViewModelTracker
import com.liamgleeson.vibeyfin.testing.itemJson
import com.liamgleeson.vibeyfin.testing.itemsJson
import com.liamgleeson.vibeyfin.ui.whoswatching.SignInMessages
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
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

    private fun viewModel() = viewModels.track(HomeViewModel(app.connection, app.library))

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

    private fun FakeServer.libraries() = on(
        "GET", "/UserViews",
        Reply.json(
            itemsJson(
                itemJson("lib-1", "Movies", type = "CollectionFolder", isFolder = true),
                itemJson("lib-2", "Shows", type = "CollectionFolder", isFolder = true),
            ),
        ),
    )

    @Test
    fun listsTheLibrariesOnceSignedIn() = runTest {
        signedIn().libraries()

        val state = viewModel().uiState.first { it.libraries.isNotEmpty() }

        assertEquals(listOf("Movies", "Shows"), state.libraries.map { it.title })
        assertFalse(state.loadingLibraries)
        assertNull(state.librariesMessage)
        assertEquals("u1", app.jellyfin.requestsTo("GET", "/UserViews").single().query["userId"])
    }

    @Test
    fun asksForNothingBeforeSignIn() = runTest {
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.libraries.isEmpty())
        assertEquals(0, app.jellyfin.requestsTo("GET", "/UserViews").size)
    }

    @Test
    fun librariesThatCantBeLoadedSayWhyAndCanBeRetried() = runTest {
        val server = signedIn()
        server.libraries()
        server.refuseConnections()
        val viewModel = viewModel()

        val failed = viewModel.uiState.first { it.librariesMessage != null }
        assertEquals(SignInMessages.UNREACHABLE, failed.librariesMessage)
        assertTrue(failed.libraries.isEmpty())
        assertFalse(failed.loadingLibraries)
        // The rest of Home still works: the menu needs no server.
        assertEquals("Alex", failed.userName)

        server.refuseConnections(false)
        viewModel.onRetryLibraries()
        val loaded = viewModel.uiState.first { it.libraries.isNotEmpty() }

        assertNull(loaded.librariesMessage)
    }

    @Test
    fun aRejectedTokenWhileListingLibrariesSendsTheUserBack() = runTest {
        val server = signedIn()
        server.on("GET", "/UserViews", Reply.status(HttpStatusCode.Unauthorized))

        val state = viewModel().uiState.first { it.exit != null }

        assertEquals(HomeExit.WhoIsWatching("srv-1"), state.exit)
        assertTrue(state.libraries.isEmpty())
    }

    @Test
    fun neverShowsNoLibrariesWhileTheyAreOnTheirWay() = runTest {
        signedIn().libraries()
        val seen = mutableListOf<HomeUiState>()

        viewModel().uiState.takeWhile { it.libraries.isEmpty() }.toList(seen)

        // Before the list arrives, a signed-in Home is loading rather than telling the user it is empty.
        assertTrue(seen.filter { it.serverId != null }.all { it.loadingLibraries }, seen.toString())
    }

    @Test
    fun anAccountWithNoLibrariesEndsEmptyAndNotLoading() = runTest {
        signedIn().on("GET", "/UserViews", Reply.json(itemsJson()))

        val state = viewModel().uiState.first { it.serverId != null && !it.loadingLibraries }

        assertTrue(state.libraries.isEmpty())
        assertNull(state.librariesMessage)
    }
}
