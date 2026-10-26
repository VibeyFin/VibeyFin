package app.vibeyfin.ui.banner

import app.vibeyfin.domain.NetworkState
import app.vibeyfin.testing.ConnectionRefused
import app.vibeyfin.testing.Reply
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AppBannerViewModelTest {
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

    private fun viewModel() = viewModels.track(AppBannerViewModel(app.offline, app.reachability))

    @Test
    fun allWellNoBanner() = runTest {
        assertNull(viewModel().banner.first { true })
    }

    @Test
    fun noNetworkSaysSoWithNothingToPress() = runTest {
        val viewModel = viewModel()
        viewModel.banner.first()

        app.network.set(NetworkState.None)

        val banner = viewModel.banner.first { it != null }!!
        assertEquals(Strings.offlineBannerTitle, banner.title)
        assertNull(banner.action)
        assertNull(banner.actionLabel)
    }

    @Test
    fun theSwitchInSettingsSaysSoAndGoOnlineTurnsItOff() = runTest {
        val viewModel = viewModel()
        app.offline.setManual(true)

        val banner = viewModel.banner.first { it != null }!!
        assertEquals(Strings.offlineModeTitle, banner.title)
        assertEquals(Strings.goOnline, banner.actionLabel)

        viewModel.onAction()

        assertNull(viewModel.banner.first { it == null })
        assertEquals(false, app.offline.isOffline.value)
    }

    @Test
    fun aServerThatDoesNotAnswerOffersToUseOfflineAndDoesNotSwitchByItself() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json("""{"Items":[],"TotalRecordCount":0}"""))
        val viewModel = viewModel()
        viewModel.banner.first()
        server.refuseConnections()

        // A read that finds nothing there.
        app.library.item("m1")

        val banner = viewModel.banner.first { it != null }!!
        assertEquals(Strings.serverUnreachableBannerTitle, banner.title)
        assertEquals(Strings.useOffline, banner.actionLabel)
        assertEquals(false, app.offline.isOffline.value, "the app is still online: the user decides")

        viewModel.onAction()

        assertEquals(true, app.offline.isOffline.value)
        assertEquals(Strings.offlineModeTitle, viewModel.banner.first { it?.action == BannerAction.GoOnline }!!.title)
    }

    @Test
    fun theServerAnsweringAgainTakesTheBannerAway() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json("""{"Items":[],"TotalRecordCount":0}"""))
        val viewModel = viewModel()
        server.refuseConnections()
        app.library.item("m1")
        viewModel.banner.first { it != null }

        server.refuseConnections(false)
        app.library.item("m1")

        assertNull(viewModel.banner.first { it == null })
    }

    @Test
    fun aSingleMissIsNotEnough() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json("""{"Items":[],"TotalRecordCount":0}"""))
        val viewModel = viewModel()
        var calls = 0
        server.on("GET", "/Items") { if (++calls == 1) throw ConnectionRefused() else Reply.json("""{"Items":[],"TotalRecordCount":0}""") }

        app.library.item("m1")

        assertNull(viewModel.banner.first { true })
    }
}
