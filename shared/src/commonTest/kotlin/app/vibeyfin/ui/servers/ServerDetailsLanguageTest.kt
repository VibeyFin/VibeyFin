package app.vibeyfin.ui.servers

import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
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
class ServerDetailsLanguageTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()

    @BeforeTest
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app.servers.save("srv-1", "Home", "10.11.2", "http://192.168.1.20:8096")
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "t1")
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        Dispatchers.resetMain()
        app.close()
        TestLanguages.reset()
    }

    @Test
    fun theRemoveTitleFollowsTheLanguage() = runTest {
        TestLanguages.installGerman("remove_server_title" to "{{name}} entfernen?")
        val viewModel = viewModels.track(ServerDetailsViewModel("srv-1", app.servers, app.accounts, app.serverRemoval))
        val before = viewModel.uiState.first { !it.loading }
        assertEquals("Remove Home?", before.removeTitle)

        Texts.select("de")

        val after = viewModel.uiState.first { it != before }
        assertEquals("Home entfernen?", after.removeTitle)
    }
}
