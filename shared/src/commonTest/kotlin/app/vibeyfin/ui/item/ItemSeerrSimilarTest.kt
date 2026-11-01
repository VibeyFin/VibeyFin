package app.vibeyfin.ui.item

import app.vibeyfin.domain.SeerrConnectResult
import app.vibeyfin.domain.SeerrState
import app.vibeyfin.testing.FakeSeerr
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
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

// "More like this" on a library page, with Seerr's recommendations the library doesn't have mixed in.
@OptIn(ExperimentalCoroutinesApi::class)
class ItemSeerrSimilarTest {
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

    // m1 is TMDB's 603. The library finds three like it; Seerr recommends one the library has (jf), one available, two to request.
    private val movie = """{"Items":[{"Id":"m1","Name":"Harbour Bridge","Type":"Movie","ProviderIds":{"Tmdb":"603","Imdb":"tt1"}}],"TotalRecordCount":1}"""
    private val recommendations = """{"page":1,"totalPages":1,"totalResults":4,"results":[
        {"id":10,"mediaType":"movie","title":"In the library","mediaInfo":{"status":5,"jellyfinMediaId":"jf"}},
        {"id":11,"mediaType":"movie","title":"Low Tide"},
        {"id":12,"mediaType":"movie","title":"Available elsewhere","mediaInfo":{"status":5}},
        {"id":13,"mediaType":"movie","title":"Salt Road","mediaInfo":{"status":2,"requests":[]}}]}"""

    private fun viewModel() = viewModels.track(
        ItemViewModel("m1", app.library, app.connection, app.watch, app.watchChanges, app.downloadRepository, app.playback, app.seerrRepository, app.seerr),
    )

    private suspend fun ItemViewModel.await(predicate: (ItemUiState) -> Boolean) = withContext(Dispatchers.Default) { withTimeout(5.seconds) { uiState.first(predicate) } }

    @Test
    fun seerrsTitlesTheLibraryLacksAreMixedIntoMoreLikeThis() = runTest {
        val jellyfin = app.signedIn()
        jellyfin.on("GET", "/Items", Reply.json(movie))
        jellyfin.on("GET", "/Items/m1/Similar", Reply.json(itemsJson(itemJson("a"), itemJson("b"), itemJson("c"))))
        val seerr = FakeSeerr(app.jellyfin.server("http://seerr:5055"), jellyfin)
        seerr.onSignedIn("GET", "/api/v1/movie/603/recommendations") { Reply.json(recommendations) }
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { app.seerr.state.first { it == SeerrState.NotSet } } }
        assertEquals(SeerrConnectResult.Connected, app.seerr.connect("http://seerr:5055"))

        val state = viewModel().await { tiles -> tiles.similarTiles.any { it is SimilarTile.Seerr } && tiles.similarTiles.any { it is SimilarTile.Library } }

        assertEquals(listOf("jf-a", "jf-b", "seerr-Movie-11", "jf-c", "seerr-Movie-13"), state.similarTiles.map { it.key })
    }

    @Test
    fun withoutSeerrMoreLikeThisIsTheLibrarys() = runTest {
        val jellyfin = app.signedIn()
        jellyfin.on("GET", "/Items", Reply.json(movie))
        jellyfin.on("GET", "/Items/m1/Similar", Reply.json(itemsJson(itemJson("a"), itemJson("b"))))

        val state = viewModel().await { it.similar?.loading == false }

        assertEquals(listOf("jf-a", "jf-b"), state.similarTiles.map { it.key })
    }
}
