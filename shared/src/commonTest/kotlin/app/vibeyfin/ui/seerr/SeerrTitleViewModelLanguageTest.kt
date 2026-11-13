package app.vibeyfin.ui.seerr

import app.vibeyfin.domain.SeerrConnectResult
import app.vibeyfin.domain.SeerrState
import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.FakeSeerr
import app.vibeyfin.testing.Reply
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SeerrTitleViewModelLanguageTest {
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

    private val movieJson = """{"id":603,"title":"Harbour Bridge","releaseDate":"2025-03-01","runtime":96,"overview":"A bridge.",
        "genres":[{"id":1,"name":"Drama"}],"releases":{"results":[]},"relatedVideos":[],"credits":{"cast":[]}}"""

    private val noRecommendations = """{"page":1,"totalPages":1,"totalResults":0,"results":[]}"""

    private suspend fun connected(): FakeSeerr {
        val jellyfin = app.signedIn()
        val seerr = FakeSeerr(app.jellyfin.server("http://seerr:5055"), jellyfin)
        seerr.onSignedIn("GET", "/api/v1/movie/603") { Reply.json(movieJson) }
        seerr.onSignedIn("GET", "/api/v1/movie/603/recommendations") { Reply.json(noRecommendations) }
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { app.seerr.state.first { it == SeerrState.NotSet } } }
        assertEquals(SeerrConnectResult.Connected, app.seerr.connect("http://seerr:5055"))
        return seerr
    }

    private fun viewModel(type: SeerrMediaType, id: Int) = viewModels.track(SeerrTitleViewModel(type, id, app.seerrRepository, app.seerr, app.clock))

    private suspend fun SeerrTitleViewModel.await(predicate: (SeerrTitleUiState) -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5.seconds) { uiState.first(predicate) }
    }

    @Test
    fun thePrimaryButtonIsWrittenInTheLanguageSelectedAfterThePageWasShown() = runTest {
        TestLanguages.installGerman("seerr_request" to "Anfragen")
        connected()
        val page = viewModel(SeerrMediaType.Movie, 603)

        assertEquals("Request", page.await { !it.loading }.primary?.label)

        Texts.select("de")
        assertEquals("Anfragen", page.await { it.primary?.label == "Anfragen" }.primary?.label)
    }
}
