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
class MyRequestsViewModelLanguageTest {
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

    private fun request(id: Int, status: Int, tmdbId: Int) = """{"id":$id,"status":$status,"type":"movie","media":{"tmdbId":$tmdbId,"mediaType":"movie","status":1},"seasons":[],"requestedBy":{"id":7},"updatedAt":"2023-11-13T22:13:20Z"}"""

    private suspend fun connected(): FakeSeerr {
        val jellyfin = app.signedIn()
        val seerr = FakeSeerr(app.jellyfin.server("http://seerr:5055"), jellyfin)
        seerr.onSignedIn("GET", "/api/v1/movie/10") { Reply.json("""{"id":10,"title":"Waiting One","releaseDate":"2022-01-01"}""") }
        seerr.onSignedIn("GET", "/api/v1/request") {
            Reply.json("""{"pageInfo":{"pages":1,"page":1,"results":1},"results":[${request(1, 1, 10)}]}""")
        }
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { app.seerr.state.first { it == SeerrState.NotSet } } }
        assertEquals(SeerrConnectResult.Connected, app.seerr.connect("http://seerr:5055"))
        return seerr
    }

    private suspend fun <T> Flow<T>.await(predicate: (T) -> Boolean): T = withContext(Dispatchers.Default) { withTimeout(5.seconds) { first(predicate) } }

    @Test
    fun theStateOfEachRequestIsWrittenInTheLanguageSelectedAfterItWasShown() = runTest {
        TestLanguages.installGerman("seerr_state_label_requested" to "Angefragt")
        connected()
        val page = viewModels.track(MyRequestsViewModel(app.seerrRepository, app.clock))

        assertEquals("Requested", page.uiState.await { it.items.isNotEmpty() }.items.first().stateLabel)

        Texts.select("de")
        assertEquals("Angefragt", page.uiState.await { state -> state.items.any { it.stateLabel == "Angefragt" } }.items.first().stateLabel)
    }
}
