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
class SeerrEpisodeNamesLanguageTest {
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
        TestLanguages.reset()
    }

    private val showJson = """{"id":19885,"name":"Storm Harbour","firstAirDate":"2010-07-25","seasons":[
        {"seasonNumber":1,"name":"Series 1","episodeCount":2}],
        "mediaInfo":{"status":4,"seasons":[{"seasonNumber":1,"status":5}],"requests":[]}}"""

    private val noRecommendations = """{"page":1,"totalPages":1,"totalResults":0,"results":[]}"""

    private suspend fun connected(): FakeSeerr {
        val jellyfin = app.signedIn()
        val seerr = FakeSeerr(app.jellyfin.server("http://seerr:5055"), jellyfin)
        seerr.onSignedIn("GET", "/api/v1/tv/19885") { Reply.json(showJson) }
        seerr.onSignedIn("GET", "/api/v1/tv/19885/recommendations") { Reply.json(noRecommendations) }
        seerr.onSignedIn("GET", "/api/v1/tv/19885/season/1") {
            Reply.json("""{"seasonNumber":1,"episodes":[{"episodeNumber":1,"name":""},{"episodeNumber":2,"name":"Two"}]}""")
        }
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { app.seerr.state.first { it == SeerrState.NotSet } } }
        assertEquals(SeerrConnectResult.Connected, app.seerr.connect("http://seerr:5055"))
        return seerr
    }

    @Test
    fun `an unnamed episode is called by its number in the language after a switch`() = runTest {
        TestLanguages.installGerman("episode_number" to "Folge {{number}}")
        connected()
        val viewModel = viewModels.track(SeerrTitleViewModel(SeerrMediaType.Show, 19885, app.seerrRepository, app.seerr, app.clock))
        val before = viewModel.uiState.first { it.episodes?.loading == false }.episodes!!
        assertEquals(listOf("Episode 1", "Two"), before.episodes.map { it.title })

        Texts.select("de")

        val after = viewModel.uiState.first { it.episodes?.episodes?.firstOrNull()?.title == "Folge 1" }.episodes!!
        assertEquals(listOf("Folge 1", "Two"), after.episodes.map { it.title })
    }
}
