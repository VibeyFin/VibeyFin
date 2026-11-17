package app.vibeyfin.ui.subtitles

import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.ConnectionRefused
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.ui.common.LoadMessages
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
class SubtitleSearchViewModelLanguageTest {
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
    fun theProblemIsWordedAgainWhenTheLanguageChanges() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex","Configuration":{"SubtitleLanguagePreference":"ita"}}"""))
        server.on(
            "GET",
            "/Localization/Cultures",
            Reply.json("""[{"DisplayName":"Italian","TwoLetterISOLanguageName":"it","ThreeLetterISOLanguageName":"ita"}]"""),
        )
        server.on("GET", "/Items/m1/RemoteSearch/Subtitles/ita") { throw ConnectionRefused() }
        TestLanguages.installGerman("load_subtitles_unreachable_title" to "Server nicht erreichbar")
        val viewModel = viewModels.track(SubtitleSearchViewModel(app.subtitles, app.languages, app.connection))
        viewModel.onOpen("m1")

        assertEquals(LoadMessages.SUBTITLES_UNREACHABLE_TITLE, viewModel.uiState.first { !it.loading }.problem?.title)

        Texts.select("de")
        assertEquals("Server nicht erreichbar", viewModel.uiState.first { it.problem?.title == "Server nicht erreichbar" }.problem?.title)
    }
}
