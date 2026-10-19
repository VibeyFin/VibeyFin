package com.liamgleeson.vibeyfin.ui.whoswatching

import com.liamgleeson.vibeyfin.domain.ConnectionState
import com.liamgleeson.vibeyfin.testing.FakeServer
import com.liamgleeson.vibeyfin.testing.Reply
import com.liamgleeson.vibeyfin.testing.TestApp
import com.liamgleeson.vibeyfin.testing.authJson
import com.liamgleeson.vibeyfin.testing.publicUsersJson
import androidx.lifecycle.viewModelScope
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
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

@OptIn(ExperimentalCoroutinesApi::class)
class WhoIsWatchingViewModelTest {
    private val app = TestApp()
    private val address = "http://nas:8096"
    private lateinit var server: FakeServer

    @BeforeTest
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = app.serverAt(address)
        server.on("GET", "/Users/Public", Reply.json(publicUsersJson(Triple("u1", "Alex", true), Triple("u2", "Kim", false), Triple("u3", "Sam", true))))
        server.on("GET", "/QuickConnect/Enabled", Reply.json("false"))
        // "right" is the password for everyone; Kim has none.
        server.on("POST", "/Users/AuthenticateByName") { request ->
            val name = Regex(""""Username":"([^"]*)"""").find(request.body)!!.groupValues[1]
            val password = Regex(""""Pw":"([^"]*)"""").find(request.body)!!.groupValues[1]
            val id = mapOf("alex" to "u1", "kim" to "u2", "sam" to "u3").getValue(name.lowercase())
            if (name.lowercase() == "kim" && password == "" || password == "right") {
                Reply.json(authJson(userId = id, name = name.replaceFirstChar { it.uppercase() }, token = "token-$id"))
            } else {
                Reply.status(HttpStatusCode.Unauthorized)
            }
        }
    }

    private val created = mutableListOf<WhoIsWatchingViewModel>()

    @AfterTest
    fun tearDown() = runTest {
        // Stop each ViewModel and wait for it to finish before the database and the main dispatcher
        // go; a job that is only cancelled still completes later and would fail in the next test.
        created.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() }
        Dispatchers.resetMain()
        app.close()
    }

    private fun viewModel() =
        WhoIsWatchingViewModel("srv-1", app.servers, app.accounts, app.auth, app.connection).also { created += it }

    private suspend fun WhoIsWatchingViewModel.loaded() = uiState.first { !it.loading }

    @Test
    fun showsTheServersUsersAsTiles() = runTest {
        val state = viewModel().loaded()

        assertEquals("Home", state.serverName)
        assertEquals(listOf("Alex", "Kim", "Sam"), state.profiles.map { it.name })
        assertEquals(listOf(true, false, true), state.profiles.map { it.needsPassword })
        assertTrue(state.serverReachable)
    }

    @Test
    fun savedProfilesComeFirst() = runTest {
        app.accounts.saveSession("srv-1", "u3", "Sam", null, "token-u3")

        assertEquals(listOf("Sam", "Alex", "Kim"), viewModel().loaded().profiles.map { it.name })
    }

    @Test
    fun anUnreachableServerStillShowsSavedProfiles() = runTest {
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-u1")
        server.refuseConnections()

        val state = viewModel().loaded()

        assertFalse(state.serverReachable)
        assertEquals(listOf("Alex"), state.profiles.map { it.name })
    }

    @Test
    fun tappingAProfileWithASavedSessionSignsInWithoutAPassword() = runTest {
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-u1")
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex"}"""))
        val viewModel = viewModel()
        val alex = viewModel.loaded().profiles.first { it.name == "Alex" }

        viewModel.onProfileTapped(alex)

        val state = viewModel.uiState.first { it.signedIn }
        assertNull(state.sheet)
        assertIs<ConnectionState.Connected>(app.connection.state.value)
        assertEquals(0, app.jellyfin.requestsTo("POST", "/Users/AuthenticateByName").size)
    }

    @Test
    fun aSavedSessionTheServerRejectsFallsBackToAskingForThePassword() = runTest {
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "stale-token")
        server.on("GET", "/Users/Me", Reply.status(HttpStatusCode.Unauthorized))
        val viewModel = viewModel()

        viewModel.onProfileTapped(viewModel.loaded().profiles.first { it.name == "Alex" })

        // The tile locks a moment after the sheet opens, once the saved list reports the cleared token.
        val state = viewModel.uiState.first { it.sheet != null && it.profiles.first { p -> p.name == "Alex" }.locked }
        assertEquals("Alex", assertIs<SignInSheet.Password>(state.sheet).profile.name)
        assertEquals(SignInMessages.SESSION_ENDED, state.error)
        assertFalse(state.signedIn)
    }

    @Test
    fun aUserWithoutAPasswordSignsInWithAnEmptyOne() = runTest {
        val viewModel = viewModel()

        viewModel.onProfileTapped(viewModel.loaded().profiles.first { it.name == "Kim" })

        assertTrue(viewModel.uiState.first { it.signedIn }.signedIn)
        assertTrue(app.jellyfin.requestsTo("POST", "/Users/AuthenticateByName").single().body.contains(""""Pw":"""""))
        assertEquals("token-u2", app.accounts.sessionToken("srv-1", "u2"))
    }

    @Test
    fun aLockedProfileOpensThePasswordSheet() = runTest {
        val viewModel = viewModel()

        viewModel.onProfileTapped(viewModel.loaded().profiles.first { it.name == "Sam" })

        val sheet = assertIs<SignInSheet.Password>(viewModel.uiState.value.sheet)
        assertEquals("Sam", sheet.profile.name)
        assertEquals(0, app.jellyfin.requestsTo("POST", "/Users/AuthenticateByName").size)
    }

    @Test
    fun aWrongPasswordShowsAnErrorAndKeepsTheSheetOpen() = runTest {
        val viewModel = viewModel()
        viewModel.onProfileTapped(viewModel.loaded().profiles.first { it.name == "Sam" })

        viewModel.onSubmitPassword("wrong")

        val state = viewModel.uiState.first { !it.signingIn && it.error != null }
        assertEquals(SignInMessages.WRONG_PASSWORD, state.error)
        assertIs<SignInSheet.Password>(state.sheet)
        assertFalse(state.signedIn)
        assertNull(app.accounts.account("srv-1", "u3"))
    }

    @Test
    fun theRightPasswordAfterAWrongOneSignsInAndClosesTheSheet() = runTest {
        val viewModel = viewModel()
        viewModel.onProfileTapped(viewModel.loaded().profiles.first { it.name == "Sam" })
        viewModel.onSubmitPassword("wrong")
        viewModel.uiState.first { !it.signingIn && it.error != null }

        viewModel.onSubmitPassword("right")

        val state = viewModel.uiState.first { it.signedIn }
        assertNull(state.sheet)
        assertNull(state.error)
        assertEquals("token-u3", app.accounts.sessionToken("srv-1", "u3"))
    }

    @Test
    fun anotherUserSignsInWithAUsernameAndPassword() = runTest {
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onOtherUserTapped()
        assertIs<SignInSheet.OtherUser>(viewModel.uiState.value.sheet)
        viewModel.onSubmitOtherUser("  alex ", "right")

        assertTrue(viewModel.uiState.first { it.signedIn }.signedIn)
        assertTrue(app.jellyfin.requestsTo("POST", "/Users/AuthenticateByName").single().body.contains(""""Username":"alex""""))
    }

    @Test
    fun aWrongUsernameOrPasswordForAnotherUserSaysSoAndKeepsTheSheet() = runTest {
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.onOtherUserTapped()

        viewModel.onSubmitOtherUser("alex", "wrong")

        val state = viewModel.uiState.first { !it.signingIn && it.error != null }
        assertEquals(SignInMessages.WRONG_CREDENTIALS, state.error)
        assertIs<SignInSheet.OtherUser>(state.sheet)
    }

    @Test
    fun aBlankUsernameIsNotSent() = runTest {
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.onOtherUserTapped()

        viewModel.onSubmitOtherUser("   ", "right")

        assertEquals(0, app.jellyfin.requestsTo("POST", "/Users/AuthenticateByName").size)
    }

    @Test
    fun aSavedSessionWhenTheServerIsDownShowsAnErrorInsteadOfASheet() = runTest {
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-u1")
        val viewModel = viewModel()
        val alex = viewModel.loaded().profiles.first { it.name == "Alex" }
        server.refuseConnections()

        viewModel.onProfileTapped(alex)

        val state = viewModel.uiState.first { !it.signingIn && it.error != null }
        assertEquals(SignInMessages.UNREACHABLE, state.error)
        assertNull(state.sheet)
        assertEquals("token-u1", app.accounts.sessionToken("srv-1", "u1"))
    }

    @Test
    fun closingTheSheetClearsItsError() = runTest {
        val viewModel = viewModel()
        viewModel.onProfileTapped(viewModel.loaded().profiles.first { it.name == "Sam" })
        viewModel.onSubmitPassword("wrong")
        viewModel.uiState.first { it.error != null }

        viewModel.onDismissSheet()

        assertNull(viewModel.uiState.value.sheet)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun theServerCanBeAskedAgainAfterItWasUnreachable() = runTest {
        server.refuseConnections()
        val viewModel = viewModel()
        assertFalse(viewModel.loaded().serverReachable)

        server.refuseConnections(false)
        viewModel.refresh()

        val state = viewModel.uiState.first { it.serverReachable }
        assertEquals(listOf("Alex", "Kim", "Sam"), state.profiles.map { it.name })
    }
}
