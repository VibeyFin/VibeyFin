package app.vibeyfin.ui.seerr

import app.vibeyfin.domain.SeerrConnectResult
import app.vibeyfin.domain.SeerrState
import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.FakeSeerr
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
class SeerrListViewModelLanguageTest {
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

    private fun page(vararg results: String) = """{"page":1,"totalPages":1,"totalResults":${results.size},"results":[${results.joinToString(",")}]}"""

    private fun movie(id: Int, title: String) = """{"id":$id,"mediaType":"movie","title":"$title","releaseDate":"2025-01-01"}"""

    private suspend fun connected(): FakeSeerr {
        val jellyfin = app.signedIn()
        val seerr = FakeSeerr(app.jellyfin.server("http://seerr:5055"), jellyfin)
        seerr.onSignedIn("GET", "/api/v1/discover/trending") { Reply.json(page(movie(1, "Low Tide"))) }
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { app.seerr.state.first { it == SeerrState.NotSet } } }
        assertEquals(SeerrConnectResult.Connected, app.seerr.connect("http://seerr:5055"))
        return seerr
    }

    private suspend fun <T> Flow<T>.await(predicate: (T) -> Boolean): T = withContext(Dispatchers.Default) { withTimeout(5.seconds) { first(predicate) } }

    @Test
    fun theTitleOfTheListIsWrittenInTheLanguageSelectedAfterItWasShown() = runTest {
        TestLanguages.installGerman("seerr_trending" to "Angesagt")
        connected()
        val list = viewModels.track(SeerrListViewModel(SeerrListKind.Trending, app.seerrRepository))
        assertEquals("Trending", list.uiState.value.title)

        Texts.select("de")
        assertEquals("Angesagt", list.uiState.await { it.title == "Angesagt" }.title)
    }
}
