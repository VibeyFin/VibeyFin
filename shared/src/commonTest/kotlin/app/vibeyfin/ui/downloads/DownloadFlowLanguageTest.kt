package app.vibeyfin.ui.downloads

import app.vibeyfin.domain.DownloadTarget
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
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
class DownloadFlowLanguageTest {
    private val app = TestApp()
    private val alex = ProfileRef("srv-1", "u1")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        TestLanguages.reset()
        Dispatchers.resetMain()
        app.close()
    }

    @Test
    fun theMessageForADownloadThatCouldNotStartFollowsTheLanguage() = runTest {
        TestLanguages.installGerman("sign_in_unreachable" to "Keine Verbindung zum Server")
        val server = app.signedIn()
        server.refuseConnections()
        val flow = DownloadFlow(backgroundScope, app.downloadRepository) { alex }
        flow.start(DownloadTarget.Item("m1"), "Heat", ask = false)
        assertNotNull(flow.message.first { it != null })

        Texts.select("de")

        assertEquals("Keine Verbindung zum Server", flow.message.first { it == "Keine Verbindung zum Server" })
    }
}
