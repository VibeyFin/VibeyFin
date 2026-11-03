package app.vibeyfin.ui.profile

import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.ui.common.PinReason
import app.vibeyfin.ui.home.HomeExit
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileMenuViewModelTest {
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

    private fun viewModel() = viewModels.track(ProfileMenuViewModel(app.connection, app.profileSettings, app.searchHistory, app.pins, app.auth, app.downloadRepository))

    private suspend fun signedIn() {
        val server = app.serverAt("http://nas:8096")
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex"}"""))
        server.on("POST", "/Sessions/Logout", Reply.noContent())
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.connection.activate("srv-1", "u1")
    }

    @Test
    fun `shows nobody before sign in`() = runTest {
        assertEquals(ProfileMenuUiState(), viewModel().uiState.value)
    }

    @Test
    fun `shows who is signed in`() = runTest {
        signedIn()

        assertEquals("Alex", viewModel().uiState.first { it.userName != null }.userName)
    }

    @Test
    fun `switching profile keeps the session and goes to who's watching`() = runTest {
        signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }

        viewModel.onSwitchProfile()

        assertEquals(HomeExit.WhoIsWatching("srv-1"), viewModel.uiState.first { it.exit != null }.exit)
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
        assertEquals("token-1", app.accounts.sessionToken("srv-1", "u1"))
    }

    @Test
    fun `the button shows the profile's Jellyfin picture when it has one`() = runTest {
        signedIn()
        assertNull(viewModel().uiState.first { it.userName != null }.avatarUrl)

        app.accounts.saveSession("srv-1", "u1", "Alex", "tag-1", "token-1")
        app.connection.activate("srv-1", "u1")

        assertEquals("http://nas:8096/Users/u1/Images/Primary?tag=tag-1&maxWidth=256", viewModel().uiState.first { it.avatarUrl != null }.avatarUrl)
    }

    @Test
    fun `Quick Connect opens straight away without a PIN to open Settings`() = runTest {
        signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }

        viewModel.onQuickConnect()
        viewModel.uiState.first { it.openQuickConnect }

        viewModel.onQuickConnectOpened()
        assertFalse(viewModel.uiState.first { !it.openQuickConnect }.openQuickConnect)
    }

    @Test
    fun `Quick Connect asks for the PIN to open Settings when that is on`() = runTest {
        signedIn()
        app.setPin("u1", "1234")
        app.database.accountDao().setPinOpenSettings("srv-1", "u1", true)
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }

        viewModel.onQuickConnect()
        val asked = viewModel.uiState.first { it.pinPrompt != null }
        assertEquals(PinReason.OpenSettings, asked.pinPrompt!!.reason)
        assertFalse(asked.openQuickConnect)

        viewModel.onSubmitPin("1234")
        viewModel.uiState.first { it.openQuickConnect }
    }

    @Test
    fun `a kids profile gets no Quick Connect`() = runTest {
        signedIn()
        app.profileSettings.setKidsProfile(true)
        val viewModel = viewModel()
        viewModel.uiState.first { it.kidsProfile }

        viewModel.onQuickConnect()

        assertFalse(viewModel.uiState.value.openQuickConnect)
    }

    @Test
    fun `switching profile asks for the PIN first when there is one`() = runTest {
        signedIn()
        app.setPin("u1", "1234")
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }

        viewModel.onSwitchProfile()
        assertEquals(PinReason.SwitchProfile, viewModel.uiState.first { it.pinPrompt != null }.pinPrompt!!.reason)
        assertIs<ConnectionState.Connected>(app.connection.state.value)

        viewModel.onSubmitPin("1234")
        assertEquals(HomeExit.WhoIsWatching("srv-1"), viewModel.uiState.first { it.exit != null }.exit)
    }

    @Test
    fun `closing the PIN sheet stays on the profile`() = runTest {
        signedIn()
        app.setPin("u1", "1234")
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }

        viewModel.onSwitchProfile()
        viewModel.uiState.first { it.pinPrompt != null }
        viewModel.onDismissPin()

        val state = viewModel.uiState.first { it.pinPrompt == null }
        assertNull(state.exit)
        assertIs<ConnectionState.Connected>(app.connection.state.value)
    }

    @Test
    fun `switching server goes to the servers list`() = runTest {
        signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }

        viewModel.onSwitchServer()

        assertEquals(HomeExit.Servers, viewModel.uiState.first { it.exit != null }.exit)
    }

    @Test
    fun `signing out revokes the token and deletes the profile's downloads`() = runTest {
        signedIn()
        app.files.open("srv-1/u1/m1/video.mkv", append = false).also { it.write("x".encodeToByteArray(), 1) }.close()
        app.files.open("srv-1/u2/m9/video.mkv", append = false).also { it.write("x".encodeToByteArray(), 1) }.close()
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }

        viewModel.onSignOut()

        assertEquals(HomeExit.WhoIsWatching("srv-1"), viewModel.uiState.first { it.exit != null }.exit)
        assertNull(app.accounts.sessionToken("srv-1", "u1"))
        assertEquals(setOf("srv-1/u2/m9/video.mkv"), app.files.contents.keys, "only the signed-out profile's files go")
    }

    @Test
    fun `a kids profile cannot sign out`() = runTest {
        signedIn()
        app.profileSettings.setKidsProfile(true)
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null && it.kidsProfile }

        viewModel.onSignOut()

        assertEquals("token-1", app.accounts.sessionToken("srv-1", "u1"))
    }
}
