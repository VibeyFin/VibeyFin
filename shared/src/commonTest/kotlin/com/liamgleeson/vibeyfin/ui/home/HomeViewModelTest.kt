package com.liamgleeson.vibeyfin.ui.home

import com.liamgleeson.vibeyfin.testing.Reply
import com.liamgleeson.vibeyfin.testing.TestApp
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val app = TestApp()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        app.close()
    }

    @Test
    fun nothingIsShownBeforeSignIn() = runTest {
        assertEquals(HomeUiState(), HomeViewModel(app.connection).uiState.value)
    }

    @Test
    fun showsWhoIsSignedInOnWhichServer() = runTest {
        app.serverAt("http://nas:8096").on("GET", "/Users/Me", Reply.json("""{"Id":"u1"}"""))
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.connection.activate("srv-1", "u1")

        val state = HomeViewModel(app.connection).uiState.first { it.userName != null }

        assertEquals("Alex", state.userName)
        assertEquals("Home", state.serverName)
        assertEquals("srv-1", state.serverId)
        assertFalse(state.expired)
    }

    @Test
    fun anEndedSessionIsFlaggedSoTheScreenCanLeave() = runTest {
        app.serverAt("http://nas:8096").on("GET", "/Users/Me", Reply.status(HttpStatusCode.Unauthorized))
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.connection.activate("srv-1", "u1")

        val state = HomeViewModel(app.connection).uiState.first { it.expired }

        assertTrue(state.expired)
        assertEquals("srv-1", state.serverId)
    }
}
