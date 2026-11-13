package app.vibeyfin.ui.banner

import app.vibeyfin.i18n.Texts
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
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class AppBannerViewModelLanguageTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        TestLanguages.reset()
        viewModels.stopAll()
        Dispatchers.resetMain()
        app.close()
    }

    private fun viewModel() = viewModels.track(AppBannerViewModel(app.offline, app.reachability))

    @Test
    fun theBannerTitleFollowsTheLanguage() = runTest {
        TestLanguages.installGerman("server_unreachable_banner_title" to "Server nicht erreichbar")
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json("""{"Items":[],"TotalRecordCount":0}"""))
        val viewModel = viewModel()
        viewModel.banner.first()
        server.refuseConnections()
        app.library.item("m1")
        assertEquals(Strings.serverUnreachableBannerTitle, assertNotNull(viewModel.banner.first { it != null }).title)

        Texts.select("de")

        assertEquals("Server nicht erreichbar", viewModel.banner.first { it?.title == "Server nicht erreichbar" }?.title)
    }
}
