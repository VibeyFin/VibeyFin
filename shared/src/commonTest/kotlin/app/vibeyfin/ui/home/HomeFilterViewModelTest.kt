package app.vibeyfin.ui.home

import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
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
import kotlin.time.Duration.Companion.days

/** Home's category buttons: one filter at a time, the sheets, and what each does to the rows. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeFilterViewModelTest {
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

    private fun viewModel() = viewModels.track(HomeViewModel(app.connection, app.library, app.watch, app.watchChanges, app.clock))

    private fun added(daysAgo: Int) = (app.clock.now() - daysAgo.days).toString()

    private val show = itemJson("sh1", "Severance", type = "Series", dateCreated = added(1))
    private val film = itemJson("mv1", "Heat", type = "Movie", dateCreated = added(2))

    /** A signed-in server whose recent items depend on the type asked for, like the real one. */
    private suspend fun server(
        studios: Reply = Reply.json(itemsJson(itemJson("st2", "Neon", type = "Studio"), itemJson("st1", "A24", type = "Studio"))),
        genres: Reply = Reply.json(itemsJson(itemJson("g1", "Drama", type = "Genre"))),
    ): FakeServer {
        val server = app.signedIn()
        server.on("GET", "/UserViews", Reply.json(itemsJson(itemJson("lib", "Movies", type = "CollectionFolder", isFolder = true))))
        server.on("GET", "/UserItems/Resume", Reply.json(itemsJson()))
        server.on("GET", "/Shows/NextUp", Reply.json(itemsJson()))
        server.on("GET", "/Studios", studios)
        server.on("GET", "/Genres", genres)
        server.on("GET", "/Items") { request ->
            val types = request.query["includeItemTypes"].orEmpty()
            Reply.json(
                when (types) {
                    "Series" -> itemsJson(show)
                    "Movie" -> itemsJson(film)
                    else -> itemsJson(show, film)
                },
            )
        }
        return server
    }

    private suspend fun HomeViewModel.loaded() = uiState.first { it.serverId != null && !it.loadingLibraries && !it.continueRow.loading }

    @Test
    fun homeOpensUnfilteredWithTheFiveButtonsInOrder() = runTest {
        server()

        val state = viewModel().loaded()

        assertNull(state.filter)
        assertNull(state.recentRow)
        assertEquals(listOf("Shows", "Movies", "New", "Studios", "Categories"), state.buttons.map { it.label })
        assertTrue(state.buttons.none { it.selected })
        assertEquals(listOf(false, false, false, true, true), state.buttons.map { it.opensSheet })
    }

    @Test
    fun showsLimitsHomeToShowsAndASecondTapClearsIt() = runTest {
        server()
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onCategory(CategoryKind.Shows)
        val filtered = viewModel.uiState.first { it.filter == HomeFilter.Shows && it.recentRow?.loading == false }

        assertEquals(listOf("Severance"), filtered.recentRow?.items?.map { it.title })
        assertEquals(listOf(true, false, false, false, false), filtered.buttons.map { it.selected })

        viewModel.onCategory(CategoryKind.Shows)
        val cleared = viewModel.uiState.first { it.filter == null }

        assertNull(cleared.recentRow)
        assertTrue(cleared.buttons.none { it.selected })
    }

    @Test
    fun theLastTapWinsAndOnlyOneButtonIsSelected() = runTest {
        server()
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onCategory(CategoryKind.Shows)
        viewModel.onCategory(CategoryKind.Movies)
        val state = viewModel.uiState.first { it.filter == HomeFilter.Movies && it.recentRow?.loading == false }

        assertEquals(listOf("Heat"), state.recentRow?.items?.map { it.title })
        assertEquals(listOf(false, true, false, false, false), state.buttons.map { it.selected })
    }

    @Test
    fun newKeepsTitlesFromTheLast30Days() = runTest {
        val server = server()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("a", "Fresh", dateCreated = added(3)), itemJson("b", "Stale", dateCreated = added(45)))))
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onCategory(CategoryKind.New)
        val state = viewModel.uiState.first { it.filter == HomeFilter.New && it.recentRow?.loading == false }

        assertEquals(listOf("Fresh"), state.recentRow?.items?.map { it.title })
    }

    @Test
    fun theStudiosSheetListsStudiosByNameAndPickingOneFiltersHome() = runTest {
        val server = server()
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onCategory(CategoryKind.Studios)
        val sheet = assertNotNull(viewModel.uiState.first { it.sheet?.loading == false }.sheet)

        assertEquals("Studios", sheet.title)
        assertEquals(listOf("A24", "Neon"), sheet.options.map { it.name })

        viewModel.onPickOption("st1")
        val state = viewModel.uiState.first { it.filter != null && it.recentRow?.loading == false }

        assertEquals(HomeFilter.Studio("st1", "A24"), state.filter)
        assertNull(state.sheet)
        assertEquals("Studios: A24", state.buttons[3].label)
        assertTrue(state.buttons[3].selected)
        assertEquals("st1", server.requestsOf("/Items").last()["studioIds"])
    }

    @Test
    fun theCategoriesSheetFiltersByGenreAndMarksThePickedOneWhenReopened() = runTest {
        val server = server()
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.onCategory(CategoryKind.Categories)
        viewModel.uiState.first { it.sheet?.loading == false }
        viewModel.onPickOption("g1")
        viewModel.uiState.first { it.filter != null && it.recentRow?.loading == false }

        viewModel.onCategory(CategoryKind.Categories)

        val sheet = assertNotNull(viewModel.uiState.first { it.sheet != null }.sheet)
        assertEquals("g1", sheet.pickedId)
        assertEquals("Categories: Drama", viewModel.uiState.value.buttons[4].label)
        assertEquals("g1", server.requestsOf("/Items").last()["genreIds"])
        // Opened again, the list comes from memory: the server is asked once.
        assertEquals(1, server.requestsOf("/Genres").size)
    }

    @Test
    fun aSheetThatFailsToLoadKeepsTheFilterAndRetries() = runTest {
        val server = server(genres = Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.onCategory(CategoryKind.Shows)
        viewModel.uiState.first { it.recentRow?.loading == false }

        viewModel.onCategory(CategoryKind.Categories)
        val failed = assertNotNull(viewModel.uiState.first { it.sheet?.loading == false }.sheet)

        assertNotNull(failed.message)
        assertEquals(HomeFilter.Shows, viewModel.uiState.value.filter)

        server.on("GET", "/Genres", Reply.json(itemsJson(itemJson("g1", "Drama", type = "Genre"))))
        viewModel.onRetrySheet()
        val retried = assertNotNull(viewModel.uiState.first { it.sheet?.options?.isNotEmpty() == true }.sheet)

        assertNull(retried.message)
        assertEquals(listOf("Drama"), retried.options.map { it.name })
    }

    @Test
    fun anEmptyStudiosListSaysSo() = runTest {
        server(studios = Reply.json(itemsJson()))
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onCategory(CategoryKind.Studios)
        val sheet = assertNotNull(viewModel.uiState.first { it.sheet?.loading == false }.sheet)

        assertTrue(sheet.isEmpty)
        assertEquals("This server has no studios.", sheet.emptyText)
    }

    @Test
    fun dismissingTheSheetLeavesTheFilterAlone() = runTest {
        server()
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.onCategory(CategoryKind.Movies)
        viewModel.uiState.first { it.recentRow?.loading == false }

        viewModel.onCategory(CategoryKind.Studios)
        viewModel.onDismissSheet()

        assertNull(viewModel.uiState.value.sheet)
        assertEquals(HomeFilter.Movies, viewModel.uiState.value.filter)
    }

    @Test
    fun theCrossOnAnActiveStudioClearsItAndRestoresTheLibraries() = runTest {
        server()
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.onCategory(CategoryKind.Studios)
        viewModel.uiState.first { it.sheet?.loading == false }
        viewModel.onPickOption("st1")
        viewModel.uiState.first { it.filter != null && it.recentRow?.loading == false }

        viewModel.onClearFilter()
        val state = viewModel.uiState.first { it.filter == null }

        assertNull(state.recentRow)
        assertEquals("Studios", state.buttons[3].label)
        assertEquals(1, state.libraries.size)
    }

    @Test
    fun nothingMatchingTheFilterIsSaidOnceBothRowsAreEmpty() = runTest {
        val server = server()
        server.on("GET", "/Items", Reply.json(itemsJson()))
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onCategory(CategoryKind.Movies)
        val state = viewModel.uiState.first { it.filter != null && it.recentRow?.loading == false && !it.continueRow.loading }

        assertTrue(state.nothingMatchesFilter)
        assertFalse(state.continueRow.isVisible)
    }

    @Test
    fun aFailedRecentRowShowsItsMessageAndRetries() = runTest {
        val server = server()
        val viewModel = viewModel()
        viewModel.loaded()
        server.on("GET", "/Items", Reply.status(HttpStatusCode.ServiceUnavailable))

        viewModel.onCategory(CategoryKind.Shows)
        val failed = viewModel.uiState.first { it.recentRow?.message != null }

        assertTrue(failed.recentRow!!.items.isEmpty())

        server.on("GET", "/Items", Reply.json(itemsJson(show)))
        viewModel.onRetryRecent()
        val retried = viewModel.uiState.first { it.recentRow?.items?.isNotEmpty() == true }

        assertNull(retried.recentRow?.message)
    }

    @Test
    fun continueWatchingFollowsTheFilter() = runTest {
        val server = server()
        val movie = itemJson("m9", "Alien", type = "Movie", positionTicks = 600_000_000L, runtimeTicks = 6_000_000_000L)
        val episode = itemJson("e9", "Pilot", type = "Episode", seriesId = "sh1", seriesName = "Severance", positionTicks = 600_000_000L, runtimeTicks = 6_000_000_000L)
        server.on("GET", "/UserItems/Resume", Reply.json(itemsJson(movie, episode)))
        val viewModel = viewModel()
        assertEquals(2, viewModel.loaded().continueRow.cards.size)

        viewModel.onCategory(CategoryKind.Movies)
        val movies = viewModel.uiState.first { it.filter == HomeFilter.Movies && it.continueRow.cards.size == 1 }

        assertEquals(listOf("m9"), movies.continueRow.cards.map { it.id })

        viewModel.onClearFilter()
        assertEquals(2, viewModel.uiState.first { it.filter == null && it.continueRow.cards.size == 2 }.continueRow.cards.size)
    }

    private fun FakeServer.requestsOf(path: String) = app.jellyfin.requestsTo("GET", path).map { it.query }
}
