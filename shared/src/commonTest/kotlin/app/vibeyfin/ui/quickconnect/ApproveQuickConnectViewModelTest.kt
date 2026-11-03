package app.vibeyfin.ui.quickconnect

import app.vibeyfin.domain.ActivateResult
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.whoswatching.SignInMessages
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ApproveQuickConnectViewModelTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()
    private lateinit var server: FakeServer

    @BeforeTest
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = app.serverAt("http://nas:8096")
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex"}"""))
        server.on("GET", "/QuickConnect/Enabled", Reply.json("true"))
        server.on("POST", "/QuickConnect/Authorize") { request ->
            if (request.query["code"] == "123456") Reply.json("true") else Reply.status(HttpStatusCode.NotFound)
        }
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-u1")
        assertEquals(ActivateResult.Activated, app.connection.activate("srv-1", "u1"))
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        Dispatchers.resetMain()
        app.close()
    }

    private fun viewModel() = viewModels.track(ApproveQuickConnectViewModel(app.connection))

    @Test
    fun keepsOnlySixDigitsAndApprovesOnceThereAreSix() = runTest {
        val viewModel = viewModel()

        viewModel.onCodeChange("12a 34")
        assertFalse(viewModel.uiState.first { it.code == "1234" }.canApprove)

        viewModel.onCodeChange("123 4567")
        assertTrue(viewModel.uiState.first { it.code == "123456" }.canApprove)
    }

    @Test
    fun approvesTheCodeAsTheSignedInProfile() = runTest {
        val viewModel = viewModel()
        viewModel.onCodeChange("123456")

        viewModel.onApprove()

        val state = viewModel.uiState.first { it.done != null }
        assertEquals(Strings.quickConnectApprovedFor("Alex"), state.done)
        assertEquals("", state.code)
        assertNull(state.error)
        val request = app.jellyfin.requestsTo("POST", "/QuickConnect/Authorize").single()
        assertEquals("u1", request.query["userId"])
    }

    @Test
    fun aCodeTheServerDidNotGiveOutSaysSoAndKeepsTheCode() = runTest {
        val viewModel = viewModel()
        viewModel.onCodeChange("999999")

        viewModel.onApprove()

        val state = viewModel.uiState.first { it.error != null }
        assertEquals(SignInMessages.QUICK_CONNECT_CODE_UNKNOWN, state.error)
        assertEquals("999999", state.code)
        assertFalse(state.approving)
    }

    @Test
    fun quickConnectOffIsSaidWithoutAskingToApprove() = runTest {
        // Jellyfin answers an approval with 401 while Quick Connect is off, which would end the session
        server.on("GET", "/QuickConnect/Enabled", Reply.json("false"))
        val viewModel = viewModel()
        viewModel.onCodeChange("123456")

        viewModel.onApprove()

        assertEquals(SignInMessages.QUICK_CONNECT_APPROVE_OFF, viewModel.uiState.first { it.error != null }.error)
        assertTrue(app.jellyfin.requestsTo("POST", "/QuickConnect/Authorize").isEmpty())
        assertIs<ConnectionState.Connected>(app.connection.state.value)
    }

    @Test
    fun typingAgainClearsWhatWasSaid() = runTest {
        val viewModel = viewModel()
        viewModel.onCodeChange("999999")
        viewModel.onApprove()
        viewModel.uiState.first { it.error != null }

        viewModel.onCodeChange("99999")

        assertNull(viewModel.uiState.first { it.code == "99999" }.error)
    }
}
