package app.vibeyfin.ui.item

import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.ui.common.LoadMessages
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

@OptIn(ExperimentalCoroutinesApi::class)
class ItemViewModelLanguageTest {
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

    @Test
    fun theLoadMessageIsWordedAgainWhenTheLanguageChanges() = runTest {
        app.signedIn().on("GET", "/Items", Reply.status(HttpStatusCode.Forbidden))
        TestLanguages.installGerman("load_forbidden" to "Kein Zugriff auf diesen Titel.")
        val viewModel = viewModels.track(ItemViewModel("m1", app.library, app.connection, app.watch, app.watchChanges, app.downloadRepository, app.playback, app.seerrRepository, app.seerr, app.subtitles, app.profileSettings))

        assertEquals(LoadMessages.FORBIDDEN, viewModel.uiState.first { !it.loading }.message)

        Texts.select("de")
        assertEquals("Kein Zugriff auf diesen Titel.", viewModel.uiState.first { it.message == "Kein Zugriff auf diesen Titel." }.message)
    }
}
