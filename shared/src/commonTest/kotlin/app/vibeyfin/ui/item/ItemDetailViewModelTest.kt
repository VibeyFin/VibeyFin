package app.vibeyfin.ui.item

import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TICKS_PER_MINUTE
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The parts of an item's page beyond the item: more like this, and for a show its seasons, episodes and what Play starts. */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemDetailViewModelTest {
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

    private fun viewModel(id: String) = viewModels.track(ItemViewModel(id, app.library, app.connection, app.watch, app.watchChanges))

    private fun episode(id: String, season: Int, number: Int, title: String = "Episode $number", positionMin: Int? = null, played: Boolean? = null) = itemJson(
        id, title, type = "Episode", seriesId = "show", seriesName = "Show", seasonNumber = season, episodeNumber = number,
        runtimeTicks = 40 * TICKS_PER_MINUTE, positionTicks = positionMin?.let { it * TICKS_PER_MINUTE }, played = played,
    )

    private val seasons = itemsJson(
        itemJson("sp", "Specials", type = "Season", isFolder = true, episodeNumber = 0, seriesId = "show"),
        itemJson("s1", "Season 1", type = "Season", isFolder = true, episodeNumber = 1, seriesId = "show"),
        itemJson("s2", "Season 2", type = "Season", isFolder = true, episodeNumber = 2, seriesId = "show"),
    )

    /** A signed-in server with a show of three seasons (Specials, 1 and 2) and a movie; episodes come by season. */
    private suspend fun server(nextUp: String = itemsJson(), similar: String = itemsJson(itemJson("like-1", "Alike"))): FakeServer {
        val server = app.signedIn()
        server.on("GET", "/Items") { request ->
            when (request.query["ids"]) {
                "show" -> Reply.json(itemsJson(itemJson("show", "Show", type = "Series", isFolder = true, year = 2022, childCount = 2, communityRating = 8.0)))
                else -> Reply.json(itemsJson(itemJson("m1", "Heat", year = 1995, runtimeTicks = 170 * TICKS_PER_MINUTE)))
            }
        }
        server.on("GET", "/Items/show/Similar", Reply.json(similar))
        server.on("GET", "/Items/m1/Similar", Reply.json(similar))
        server.on("GET", "/Shows/show/Seasons", Reply.json(seasons))
        server.on("GET", "/Shows/NextUp", Reply.json(nextUp))
        server.on("GET", "/Shows/show/Episodes") { request ->
            when (request.query["seasonId"]) {
                "s1" -> Reply.json(itemsJson(episode("e11", 1, 1), episode("e12", 1, 2)))
                "s2" -> Reply.json(itemsJson(episode("e21", 2, 1), episode("e22", 2, 2), episode("e23", 2, 3), episode("e24", 2, 4, positionMin = 10)))
                else -> Reply.json(itemsJson(episode("sp1", 0, 1, "Behind the scenes")))
            }
        }
        return server
    }

    private suspend fun ItemViewModel.shown() = uiState.first { !it.loading && it.similar?.loading == false && !it.seasons.loading && !it.seasons.episodes.loading }

    @Test
    fun aMovieHasMoreLikeThisAndNoSeasons() = runTest {
        server()

        val state = viewModel("m1").shown()

        assertEquals("More like this", state.similar?.title)
        assertEquals(listOf("Alike"), state.similar?.items?.map { it.title })
        assertTrue(state.seasons.seasons.isEmpty())
        assertEquals("Play", state.playLabel)
        assertEquals("m1", state.playTarget?.id)
    }

    @Test
    fun aMovieWithNothingLikeItHidesTheRow() = runTest {
        server(similar = itemsJson())

        val state = viewModel("m1").shown()

        assertFalse(state.similar!!.isVisible)
    }

    @Test
    fun moreLikeThisFailingSaysWhyAndRetriesAlone() = runTest {
        val server = server()
        server.on("GET", "/Items/m1/Similar", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel("m1")
        val failed = viewModel.shown()
        assertNotNull(failed.similar?.message)
        // The page itself is fine.
        assertEquals("Heat", failed.item?.title)

        server.on("GET", "/Items/m1/Similar", Reply.json(itemsJson(itemJson("x", "Back"))))
        viewModel.onRetrySimilar()
        val retried = viewModel.uiState.first { it.similar?.items?.isNotEmpty() == true }

        assertNull(retried.similar?.message)
    }

    @Test
    fun aShowNeverStartedOpensOnItsFirstRealSeasonAndPlaysItsFirstEpisode() = runTest {
        server()

        val state = viewModel("show").shown()

        // Specials are listed first but the page opens on Season 1.
        assertEquals(listOf("Specials", "Season 1", "Season 2"), state.seasons.seasons.map { it.title })
        assertEquals("s1", state.seasons.selectedId)
        assertEquals(listOf("e11", "e12"), state.seasons.episodes.items.map { it.id })
        assertEquals("e11", state.playTarget?.id)
        assertEquals("Play S1 E1", state.playLabel)
        assertFalse(state.canStartOver)
    }

    @Test
    fun aShowHalfWatchedOpensOnTheSeasonOfTheEpisodeItWillResume() = runTest {
        server(nextUp = itemsJson(episode("e24", 2, 4, positionMin = 10)))

        val state = viewModel("show").shown()

        assertEquals("s2", state.seasons.selectedId)
        assertEquals("e24", state.playTarget?.id)
        assertEquals("Resume S2 E4 from 10:00", state.playLabel)
        assertTrue(state.canStartOver)
        assertEquals(listOf("e21", "e22", "e23", "e24"), state.seasons.episodes.items.map { it.id })
    }

    @Test
    fun aShowWithTheNextEpisodeUnstartedSaysPlayItsNumber() = runTest {
        server(nextUp = itemsJson(episode("e23", 2, 3)))

        val state = viewModel("show").shown()

        assertEquals("Play S2 E3", state.playLabel)
        assertEquals("e23", state.playTarget?.id)
    }

    @Test
    fun aShowsFactsHaveItsSeasonsInsteadOfARuntime() = runTest {
        server()

        val state = viewModel("show").shown()

        assertEquals(listOf("2022", "2 seasons", "★ 8.0"), state.facts)
    }

    @Test
    fun aShowIsNotMarkedWatchedAsAWhole() = runTest {
        server()

        val state = viewModel("show").shown()

        assertFalse(state.canMark)
    }

    @Test
    fun anotherSeasonSwapsTheListAndKeepsTheOldOneUntilItArrives() = runTest {
        val server = server()
        val viewModel = viewModel("show")
        viewModel.shown()

        viewModel.onSelectSeason("s2")
        val swapped = viewModel.uiState.first { it.seasons.selectedId == "s2" && !it.seasons.episodes.loading }

        assertEquals(listOf("e21", "e22", "e23", "e24"), swapped.seasons.episodes.items.map { it.id })
        // What Play starts is the show's own next episode, not whichever season was tapped.
        assertEquals("e11", swapped.playTarget?.id)
        assertTrue(server.address.isNotEmpty())
    }

    @Test
    fun tappingTheSelectedSeasonAgainAsksNothing() = runTest {
        server()
        val viewModel = viewModel("show")
        viewModel.shown()
        val asked = app.jellyfin.requestsTo("GET", "/Shows/show/Episodes").size

        viewModel.onSelectSeason("s1")

        assertEquals(asked, app.jellyfin.requestsTo("GET", "/Shows/show/Episodes").size)
    }

    @Test
    fun theLastSeasonTappedWins() = runTest {
        server()
        val viewModel = viewModel("show")
        viewModel.shown()

        viewModel.onSelectSeason("s2")
        viewModel.onSelectSeason("sp")
        val state = viewModel.uiState.first { it.seasons.selectedId == "sp" && !it.seasons.episodes.loading }

        assertEquals(listOf("sp1"), state.seasons.episodes.items.map { it.id })
    }

    @Test
    fun seasonsThatFailToLoadSayWhyAndRetry() = runTest {
        val server = server()
        server.on("GET", "/Shows/show/Seasons", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel("show")
        val failed = viewModel.uiState.first { !it.loading && it.seasons.message != null }
        assertTrue(failed.seasons.seasons.isEmpty())
        // The hero and the cast are untouched.
        assertEquals("Show", failed.item?.title)

        server.on("GET", "/Shows/show/Seasons", Reply.json(seasons))
        viewModel.onRetrySeasons()
        val retried = viewModel.uiState.first { it.seasons.seasons.isNotEmpty() && !it.seasons.episodes.loading }

        assertNull(retried.seasons.message)
        assertEquals("s1", retried.seasons.selectedId)
    }

    @Test
    fun episodesThatFailToLoadSayWhyAndRetryWithoutLosingTheSeasons() = runTest {
        val server = server()
        server.on("GET", "/Shows/show/Episodes", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel("show")
        val failed = viewModel.uiState.first { it.seasons.episodes.message != null }
        assertEquals(3, failed.seasons.seasons.size)

        server.on("GET", "/Shows/show/Episodes", Reply.json(itemsJson(episode("e11", 1, 1))))
        viewModel.onRetryEpisodes()
        val retried = viewModel.uiState.first { it.seasons.episodes.items.isNotEmpty() }

        assertNull(retried.seasons.episodes.message)
    }

    @Test
    fun anEpisodesWatchedMarkIsShownAtOnceAndTakenBackIfItCantBeSaved() = runTest {
        val server = server()
        val viewModel = viewModel("show")
        viewModel.shown()
        server.on("POST", "/UserPlayedItems/e11", Reply.status(HttpStatusCode.ServiceUnavailable))

        viewModel.onToggleEpisodePlayed("e11")
        val after = viewModel.uiState.first { it.actionMessage != null }

        assertFalse(after.seasons.episodes.items.first { it.id == "e11" }.played)
    }

    @Test
    fun anEpisodeMarkedWatchedStaysMarked() = runTest {
        val server = server()
        server.on("POST", "/UserPlayedItems/e11", Reply.noContent())
        val viewModel = viewModel("show")
        viewModel.shown()

        viewModel.onToggleEpisodePlayed("e11")

        assertTrue(viewModel.uiState.value.seasons.episodes.items.first { it.id == "e11" }.played)
        assertNull(viewModel.uiState.value.actionMessage)
    }

    @Test
    fun aShowWithNoSeasonsSaysNothingIsThereAndStillHasAPage() = runTest {
        val server = server()
        server.on("GET", "/Shows/show/Seasons", Reply.json(itemsJson()))

        val state = viewModel("show").shown()

        assertTrue(state.seasons.seasons.isEmpty())
        assertNull(state.seasons.message)
        assertNull(state.playTarget)
        assertFalse(state.canPlay)
    }

    // My List, the trailer, and the season buttons' names.

    private suspend fun movieServer(item: String): FakeServer {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(item)))
        server.on("GET", "/Items/m1/Similar", Reply.json(itemsJson()))
        server.on("GET", "/Items/m1/Collections", Reply.json(itemsJson()))
        return server
    }

    @Test
    fun myListIsShownAtOnceAndKeptWhenTheServerSavesIt() = runTest {
        val server = movieServer(itemJson("m1", "Heat", favorite = false))
        server.on("POST", "/UserFavoriteItems/m1", Reply.json("""{"IsFavorite":true}"""))
        val viewModel = viewModel("m1")
        assertFalse(viewModel.uiState.first { !it.loading }.isFavorite)

        viewModel.onToggleFavorite()

        assertTrue(viewModel.uiState.value.isFavorite)
        assertEquals("u1", app.jellyfin.requestsTo("POST", "/UserFavoriteItems/m1").single().query["userId"])
    }

    @Test
    fun takingItOffMyListUsesDelete() = runTest {
        val server = movieServer(itemJson("m1", "Heat", favorite = true))
        server.on("DELETE", "/UserFavoriteItems/m1", Reply.json("""{"IsFavorite":false}"""))
        val viewModel = viewModel("m1")
        assertTrue(viewModel.uiState.first { !it.loading }.isFavorite)

        viewModel.onToggleFavorite()

        assertFalse(viewModel.uiState.value.isFavorite)
        assertEquals(1, app.jellyfin.requestsTo("DELETE", "/UserFavoriteItems/m1").size)
    }

    @Test
    fun myListThatCantBeSavedIsTakenBackWithAMessageAndRetriesTheSameChange() = runTest {
        val server = movieServer(itemJson("m1", "Heat", favorite = false))
        server.on("POST", "/UserFavoriteItems/m1", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel("m1")
        viewModel.uiState.first { !it.loading }

        viewModel.onToggleFavorite()
        val failed = viewModel.uiState.first { it.actionMessage != null }

        assertFalse(failed.isFavorite)
        assertEquals("Couldn't add this to My List. Check your connection and try again.", failed.actionMessage)

        server.on("POST", "/UserFavoriteItems/m1", Reply.json("""{"IsFavorite":true}"""))
        viewModel.onRetryAction()
        assertTrue(viewModel.uiState.value.isFavorite)
        assertNull(viewModel.uiState.value.actionMessage)
    }

    @Test
    fun aTrailerOnTheWebIsOfferedAndOneWithoutAnAddressIsNot() = runTest {
        movieServer(itemJson("m1", "Heat", remoteTrailers = listOf("https://www.youtube.com/watch?v=abc")))

        val state = viewModel("m1").uiState.first { !it.loading }

        assertEquals(Trailer.Remote("https://www.youtube.com/watch?v=abc"), state.trailer)
    }

    @Test
    fun aPageWithNoTrailerHasNoTrailerButton() = runTest {
        movieServer(itemJson("m1", "Heat"))

        assertNull(viewModel("m1").uiState.first { !it.loading }.trailer)
    }

    @Test
    fun aTrailerTheServerHoldsPlaysInTheAppAndWinsOverOneOnTheWeb() = runTest {
        val server = movieServer(itemJson("m1", "Heat", localTrailerCount = 1, remoteTrailers = listOf("https://www.youtube.com/watch?v=abc")))
        server.on("GET", "/Items/m1/LocalTrailers", Reply.json("[${itemJson("trailer-1", "Heat trailer", type = "Trailer")}]"))

        val state = viewModel("m1").uiState.first { it.trailer is Trailer.Local }

        assertEquals(Trailer.Local("trailer-1"), state.trailer)
        assertEquals("u1", app.jellyfin.requestsTo("GET", "/Items/m1/LocalTrailers").single().query["userId"])
    }

    @Test
    fun aTrailerThatCantBeFetchedLeavesTheWebOneInPlace() = runTest {
        val server = movieServer(itemJson("m1", "Heat", localTrailerCount = 1, remoteTrailers = listOf("https://www.youtube.com/watch?v=abc")))
        server.on("GET", "/Items/m1/LocalTrailers", Reply.status(HttpStatusCode.ServiceUnavailable))

        val state = viewModel("m1").uiState.first { !it.loading }
        app.jellyfin.awaitRequests("GET", "/Items/m1/LocalTrailers")

        assertEquals(Trailer.Remote("https://www.youtube.com/watch?v=abc"), viewModel("m1").uiState.first { !it.loading }.trailer ?: state.trailer)
    }

    @Test
    fun everySeasonButtonSaysSeasonWhateverTheServerCallsIt() = runTest {
        val server = server()
        server.on(
            "GET", "/Shows/show/Seasons",
            Reply.json(
                itemsJson(
                    itemJson("sp", "Special Features", type = "Season", isFolder = true, episodeNumber = 0, seriesId = "show"),
                    itemJson("s1", "Series 1", type = "Season", isFolder = true, episodeNumber = 1, seriesId = "show"),
                    itemJson("s2", "Season 2", type = "Season", isFolder = true, episodeNumber = 2, seriesId = "show"),
                ),
            ),
        )

        val state = viewModel("show").shown()

        assertEquals(listOf("Specials", "Season 1", "Season 2"), state.seasons.seasons.map { it.seasonLabel })
    }
}
