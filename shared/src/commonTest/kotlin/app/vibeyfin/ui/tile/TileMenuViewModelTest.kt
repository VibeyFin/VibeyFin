package app.vibeyfin.ui.tile

import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.item.Trailer
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TileMenuViewModelTest {
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

    private fun viewModel() = viewModels.track(TileMenuViewModel(app.library, app.watch, app.connection))

    /** The poster as a list gives it: no trailers on it. */
    private fun tile(favorite: Boolean = false) = MediaItem(
        id = "m1", title = "Heat", kind = ItemKind.Movie, isFolder = false, year = 1995, runtimeMinutes = null,
        genres = emptyList(), overview = null, rating = null, ageRating = null, seriesName = null, seasonNumber = null, episodeNumber = null,
        posterAspectRatio = 2f / 3f, posterUrl = null, backdropUrl = null, isFavorite = favorite,
    )

    private suspend fun server(detail: String) = app.signedIn().also { it.on("GET", "/Items", Reply.json(itemsJson(detail))) }

    @Test
    fun aTitleWithATrailerOnTheServerOffersItToPlayInTheApp() = runTest {
        val server = server(itemJson("m1", "Heat", localTrailerCount = 1))
        server.on("GET", "/Items/m1/LocalTrailers", Reply.json("[" + itemJson("t1", "Heat trailer", type = "Trailer") + "]"))
        val viewModel = viewModel()

        viewModel.onOpen(tile())
        val state = viewModel.uiState.first { it.item != null && !it.checkingTrailer }

        assertEquals(Trailer.Local("t1"), state.trailer)
    }

    @Test
    fun aTitleWithOnlyAWebTrailerOffersThePageToOpen() = runTest {
        server(itemJson("m1", "Heat", remoteTrailers = listOf("https://www.youtube.com/watch?v=abc")))
        val viewModel = viewModel()

        viewModel.onOpen(tile())
        val state = viewModel.uiState.first { !it.checkingTrailer }

        assertEquals(Trailer.Remote("https://www.youtube.com/watch?v=abc"), state.trailer)
    }

    @Test
    fun aTitleWithNoTrailerOffersNone() = runTest {
        server(itemJson("m1", "Heat"))
        val viewModel = viewModel()

        viewModel.onOpen(tile())
        val state = viewModel.uiState.first { !it.checkingTrailer }

        assertNull(state.trailer)
        assertFalse(state.checkingTrailer)
    }

    @Test
    fun theMenuOpensForThePosterAndClosingEmptiesIt() = runTest {
        server(itemJson("m1", "Heat", remoteTrailers = listOf("https://example.com/t")))
        val viewModel = viewModel()

        viewModel.onOpen(tile())
        assertEquals("m1", viewModel.uiState.first { it.item != null }.item?.id)
        viewModel.onClose()

        assertNull(viewModel.uiState.first { it.item == null }.item)
    }

    @Test
    fun myListInTheMenuIsShownAtOnceAndSaved() = runTest {
        val server = server(itemJson("m1", "Heat"))
        server.on("POST", "/UserFavoriteItems/m1", Reply.json("""{"IsFavorite":true}"""))
        val viewModel = viewModel()
        viewModel.onOpen(tile())

        viewModel.onToggleFavorite()

        assertTrue(viewModel.uiState.first { it.item?.isFavorite == true }.item?.isFavorite == true)
    }

    @Test
    fun myListThatCantBeSavedIsTakenBackWithAMessage() = runTest {
        val server = server(itemJson("m1", "Heat"))
        server.on("POST", "/UserFavoriteItems/m1", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel()
        viewModel.onOpen(tile())

        viewModel.onToggleFavorite()
        val failed = viewModel.uiState.first { it.actionMessage != null }

        assertEquals(false, failed.item?.isFavorite)
        assertEquals(LoadMessages.ADD_TO_MY_LIST_FAILED, failed.actionMessage)
    }

    @Test
    fun theServersWordOnMyListReplacesWhatAPosterWithoutItShows() = runTest {
        server(itemJson("m1", "Heat", favorite = true))
        val viewModel = viewModel()

        // A search result carries no My List state: it shows as off until the server has answered.
        viewModel.onOpen(tile(favorite = false))
        val state = viewModel.uiState.first { !it.checkingTrailer && it.item != null }

        assertEquals(true, state.item?.isFavorite)
    }
}
