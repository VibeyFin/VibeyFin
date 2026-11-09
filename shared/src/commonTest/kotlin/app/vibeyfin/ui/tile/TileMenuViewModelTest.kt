package app.vibeyfin.ui.tile

import app.vibeyfin.data.db.DOWNLOAD_PAUSED
import app.vibeyfin.data.db.DOWNLOAD_QUEUED
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import app.vibeyfin.testing.sagaServer
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.downloads.DownloadAction
import app.vibeyfin.ui.downloads.actions
import app.vibeyfin.ui.item.Trailer
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    private fun viewModel() = viewModels.track(TileMenuViewModel(app.library, app.watch, app.connection, app.downloadRepository, app.watchChanges))

    // The poster as a list gives it: no trailers on it.
    private fun tile(favorite: Boolean = false) = MediaItem(
        id = "m1", title = "Heat", kind = ItemKind.Movie, isFolder = false, year = 1995, runtimeMinutes = null,
        genres = emptyList(), overview = null, rating = null, ageRating = null, seriesName = null, seasonNumber = null, episodeNumber = null,
        posterAspectRatio = 2f / 3f, posterUrl = null, backdropUrl = null, isFavorite = favorite,
    )

    @Test
    fun `the line under the menu title is the year and what it is for a movie or a show`() {
        val movie = tile()
        assertEquals("1995  ·  Movie", movie.menuFacts)
        assertEquals("1995  ·  Show", movie.copy(kind = ItemKind.Series).menuFacts)
        assertEquals("Movie", movie.copy(year = null).menuFacts)
        assertEquals("1995", movie.copy(kind = ItemKind.Episode).menuFacts)
        assertEquals("", movie.copy(kind = ItemKind.Collection, year = null).menuFacts)
    }

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
    fun aCollectionIsNeverAskedForATrailer() = runTest {
        val server = server(itemJson("c1", "Neon Collection", type = "BoxSet", isFolder = true, localTrailerCount = 1, remoteTrailers = listOf("https://www.youtube.com/watch?v=abc")))
        val viewModel = viewModel()

        viewModel.onOpen(tile().copy(id = "c1", title = "Neon Collection", kind = ItemKind.Collection, isFolder = true))

        // No grey line waits for an answer that is not coming, and nothing about trailers goes to the server.
        assertFalse(viewModel.uiState.value.checkingTrailer)
        val state = viewModel.uiState.first { it.item != null }
        assertNull(state.trailer)
        assertEquals(0, app.jellyfin.requestsTo("GET", "/Items/c1/LocalTrailers").size)
        assertTrue(server.address.isNotEmpty())
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
    fun askingForThePosterAgainKeepsItsTrailerAndDoesNotCheckAgain() = runTest {
        server(itemJson("m1", "Heat", remoteTrailers = listOf("https://example.com/t")))
        val viewModel = viewModel()

        viewModel.onOpen(tile())
        viewModel.uiState.first { it.trailer != null }
        viewModel.onOpen(tile())
        val state = viewModel.uiState.value

        assertEquals(Trailer.Remote("https://example.com/t"), state.trailer)
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
    fun myListInTheMenuClosesItAndIsSaved() = runTest {
        val server = server(itemJson("m1", "Heat"))
        server.on("POST", "/UserFavoriteItems/m1", Reply.json("""{"IsFavorite":true}"""))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onOpen(tile())

        viewModel.onToggleFavorite()

        assertNull(viewModel.uiState.value.item)
        assertNull(viewModel.uiState.value.actionMessage)
        assertEquals(1, app.jellyfin.requestsTo("POST", "/UserFavoriteItems/m1").size)
    }

    @Test
    fun myListThatCantBeSavedSaysSoAfterTheMenuCloses() = runTest {
        val server = server(itemJson("m1", "Heat"))
        server.on("POST", "/UserFavoriteItems/m1", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel()
        viewModel.onOpen(tile())

        viewModel.onToggleFavorite()
        val failed = viewModel.uiState.first { it.actionMessage != null }

        assertNull(failed.item)
        assertEquals(LoadMessages.ADD_TO_MY_LIST_FAILED, failed.actionMessage)
    }

    @Test
    fun aTitlePutOnMyListHereOpensAsOnItNextTimeBeforeTheServerAnswers() = runTest {
        val server = server(itemJson("m1", "Heat"))
        server.on("POST", "/UserFavoriteItems/m1", Reply.json("""{"IsFavorite":true}"""))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onOpen(tile())
        viewModel.uiState.first { !it.checkingTrailer && it.item != null }
        viewModel.onToggleFavorite()
        server.hold("GET", "/Items")

        viewModel.onOpen(tile(favorite = false))

        assertEquals(true, viewModel.uiState.first { it.item != null }.item?.isFavorite)
    }

    @Test
    fun aTitlesMyListStateIsKnownTheMomentItIsTappedAndOnceTheServerHasSaidIt() = runTest {
        val server = server(itemJson("m1", "Heat", favorite = true))
        server.on("DELETE", "/UserFavoriteItems/m1", Reply.json("{}"))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        assertNull(viewModel.knownFavorite("m1"))
        viewModel.onOpen(tile(favorite = false))
        viewModel.uiState.first { !it.checkingTrailer && it.item?.isFavorite == true }
        assertEquals(true, viewModel.knownFavorite("m1"))

        viewModel.onToggleFavorite()

        assertEquals(false, viewModel.knownFavorite("m1"))
    }

    @Test
    fun aTitleTakenOffMyListElsewhereOpensAsOffItBeforeTheServerAnswers() = runTest {
        val server = server(itemJson("m1", "Heat", favorite = true))
        server.on("DELETE", "/UserFavoriteItems/m1", Reply.json("{}"))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        app.watch.setFavorite("m1", favorite = false)
        server.hold("GET", "/Items")

        viewModel.onOpen(tile(favorite = true))

        assertEquals(false, viewModel.uiState.first { it.item != null }.item?.isFavorite)
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

    private fun showTile() = tile().copy(id = "s1", title = "Saga", kind = ItemKind.Series, isFolder = true)

    @Test
    fun aMovieOffersDownloadRightInTheMenuAndTheTapQueuesItAndClosesTheMenu() = runTest {
        app.sagaServer()
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onOpen(tile())
        val offered = viewModel.uiState.first { it.item != null && it.offersDownload }
        assertEquals(Strings.download, offered.downloadLabel)

        viewModel.onDownload()

        assertNull(viewModel.uiState.value.item)
        viewModel.onOpen(tile())
        val state = viewModel.uiState.first { it.download != null }
        assertEquals("Heat", state.download!!.item.title)
        assertEquals(DOWNLOAD_QUEUED, app.downloads.find("srv-1", "u1", "m1")!!.state)
    }

    @Test
    fun aShowAsksHowManyEpisodesBeforeAnythingIsQueued() = runTest {
        app.sagaServer()
        val viewModel = viewModel()
        viewModel.onOpen(showTile())

        viewModel.onDownload()
        val prompt = viewModel.uiState.first { it.downloadPrompt?.plan != null }.downloadPrompt!!

        assertEquals(2, prompt.count)
        assertEquals(0, app.downloads.observeAll("srv-1", "u1").first().size)
        viewModel.onConfirmDownload()
        assertEquals(2, viewModel.uiState.first { it.downloads.size == 2 }.downloads.size)
    }

    @Test
    fun aDownloadThatIsThereShowsItsStateAndTheSameActionsAsThePage() = runTest {
        app.sagaServer()
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onOpen(tile())
        viewModel.uiState.first { it.offersDownload }
        viewModel.onDownload()
        viewModel.onOpen(tile())
        assertEquals(listOf(DownloadAction.Pause, DownloadAction.Remove), viewModel.uiState.first { it.download != null }.download!!.actions)

        viewModel.onDownloadAction(DownloadAction.Pause)

        assertNull(viewModel.uiState.value.item)
        viewModel.onOpen(tile())
        viewModel.uiState.first { it.download?.actions?.firstOrNull() == DownloadAction.Resume }
        assertEquals(DOWNLOAD_PAUSED, app.downloads.find("srv-1", "u1", "m1")!!.state)
    }

    @Test
    fun aUserWhoMayNotDownloadHasNoDownloadInTheMenu() = runTest {
        val server = app.sagaServer()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Policy":{"EnableContentDownloading":false}}"""))
        val viewModel = viewModel()

        viewModel.onOpen(tile())
        val state = viewModel.uiState.first { it.item != null && !it.canDownload }

        assertFalse(state.offersDownload)
    }

    @Test
    fun aPersonOrCollectionTileHasNothingToDownload() = runTest {
        app.sagaServer()
        val viewModel = viewModel()

        viewModel.onOpen(tile().copy(kind = ItemKind.Collection, isFolder = true))

        assertFalse(viewModel.uiState.first { it.item != null }.offersDownload)
    }
}
