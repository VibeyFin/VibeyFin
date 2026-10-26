package app.vibeyfin.ui.notifications

import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationOpenViewModelTest {
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

    private fun viewModel() = viewModels.track(NotificationOpenViewModel(app.connection, app.pins))

    /** Waits for a route on the real dispatcher, since the gate check reads the database. */
    private suspend fun NotificationOpenViewModel.nextRoute() =
        withContext(Dispatchers.Default) { withTimeout(5_000) { route.first { it != null } } }

    /** Alex (u1) is signed in; Sam (u2) is another saved profile on the same server. */
    private suspend fun twoProfiles() {
        val server = app.signedIn()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex"}"""))
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
    }

    @Test
    fun `a tap for the signed in profile opens the item`() = runTest {
        twoProfiles()
        val viewModel = viewModel()
        viewModel.onInApp(true)

        viewModel.onNotificationTapped("srv-1", "u1", "ep-2")

        assertEquals(NotificationRoute.Item("ep-2"), viewModel.nextRoute())
        viewModel.onRouted()
        assertNull(viewModel.route.value)
    }

    @Test
    fun `nothing happens until the app is past startup`() = runTest {
        twoProfiles()
        val viewModel = viewModel()

        viewModel.onNotificationTapped("srv-1", "u1", "ep-2")
        assertNull(viewModel.route.value)

        viewModel.onInApp(true)
        assertEquals(NotificationRoute.Item("ep-2"), viewModel.nextRoute())
    }

    @Test
    fun `a tap for another profile goes to who's watching and opens the item once that profile is picked`() = runTest {
        twoProfiles()
        val viewModel = viewModel()
        viewModel.onInApp(true)

        viewModel.onNotificationTapped("srv-1", "u2", "ep-2")

        assertEquals(NotificationRoute.WhoIsWatching("srv-1"), viewModel.nextRoute())
        assertIs<ConnectionState.Disconnected>(app.connection.state.value)
        viewModel.onRouted()
        viewModel.onInApp(false)

        app.connection.resume("srv-1", "u2")
        viewModel.onInApp(true)
        assertEquals(NotificationRoute.Item("ep-2"), viewModel.nextRoute())
    }

    @Test
    fun `picking another profile on who's watching drops the tap`() = runTest {
        twoProfiles()
        val viewModel = viewModel()
        viewModel.onInApp(true)
        viewModel.onNotificationTapped("srv-1", "u2", "ep-2")
        viewModel.nextRoute()
        viewModel.onRouted()
        viewModel.onInApp(false)

        app.connection.resume("srv-1", "u1")
        viewModel.onInApp(true)

        assertIs<ConnectionState.Connected>(app.connection.state.value)
        assertNull(viewModel.route.value)
    }

    @Test
    fun `a profile whose PIN guards switching is not left for a tap`() = runTest {
        twoProfiles()
        app.setPin("u1", "1234")
        val viewModel = viewModel()
        viewModel.onInApp(true)

        viewModel.onNotificationTapped("srv-1", "u2", "ep-2")
        // Gives the gate check on the database time to run.
        withContext(Dispatchers.Default) { delay(300) }

        assertNull(viewModel.route.value)
        val account = (app.connection.state.value as ConnectionState.Connected).account
        assertEquals("u1", account.userId)
    }
}
