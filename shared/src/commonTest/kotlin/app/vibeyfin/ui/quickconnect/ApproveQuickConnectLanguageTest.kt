package app.vibeyfin.ui.quickconnect

import app.vibeyfin.domain.ActivateResult
import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
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

@OptIn(ExperimentalCoroutinesApi::class)
class ApproveQuickConnectLanguageTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()
    private lateinit var server: FakeServer

    @BeforeTest
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = app.serverAt("http://nas:8096")
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex"}"""))
        server.on("GET", "/QuickConnect/Enabled", Reply.json("true"))
        server.on("POST", "/QuickConnect/Authorize") { Reply.json("true") }
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-u1")
        assertEquals(ActivateResult.Activated, app.connection.activate("srv-1", "u1"))
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        Dispatchers.resetMain()
        app.close()
        TestLanguages.reset()
    }

    @Test
    fun theApprovedLineFollowsTheLanguage() = runTest {
        TestLanguages.installGerman("quick_connect_approved_for" to "Genehmigt. Das andere Gerät meldet sich als {{name}} an.")
        val viewModel = viewModels.track(ApproveQuickConnectViewModel(app.connection))
        viewModel.onCodeChange("123456")
        viewModel.onApprove()
        val before = viewModel.uiState.first { it.done != null }
        assertEquals(Strings.quickConnectApprovedFor("Alex"), before.done)

        Texts.select("de")

        val after = viewModel.uiState.first { it != before }
        assertEquals("Genehmigt. Das andere Gerät meldet sich als Alex an.", after.done)
    }
}
