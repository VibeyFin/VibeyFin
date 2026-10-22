package app.vibeyfin.ui.filtered

import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.ui.children.ChildrenUiState
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
import kotlinx.coroutines.flow.map
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

/** The page a category button or dropdown entry opens. */
@OptIn(ExperimentalCoroutinesApi::class)
class FilteredListViewModelTest {
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

    private suspend fun FilteredListViewModel.list(until: (ChildrenUiState) -> Boolean) = uiState.map { it.list }.first(until)

    private fun viewModel(filter: HomeFilter) =
        viewModels.track(FilteredListViewModel(filter, app.library, app.connection))

    private fun FakeServer.listOf(all: List<String>, pageSize: Int) =
        on("GET", "/Items") { request ->
            val start = request.query["startIndex"]!!.toInt()
            val page = all.drop(start).take(pageSize)
            Reply.json(itemsJson(*page.map { itemJson(it) }.toTypedArray(), total = all.size, startIndex = start))
        }

    @Test
    fun theTitleIsTheButtonOrTheGenreName() = runTest {
        app.signedIn().listOf(emptyList(), 60)

        assertEquals("Shows", viewModel(HomeFilter.Shows).title)
        assertEquals("Movies", viewModel(HomeFilter.Movies).title)
        assertEquals("New", viewModel(HomeFilter.New).title)
        assertEquals("Action", viewModel(HomeFilter.Genre("g1", "Action")).title)
    }

    @Test
    fun aGenrePageShowsThatGenresMedia() = runTest {
        val server = app.signedIn()
        server.listOf(listOf("a", "b"), 60)

        val state = viewModel(HomeFilter.Genre("g-action", "Action")).list { !it.loading }

        assertEquals(listOf("a", "b"), state.items.map { it.id })
        assertNull(state.message)
        assertEquals("g-action", app.jellyfin.requestsTo("GET", "/Items").single().query["genreIds"])
    }

    @Test
    fun morePagesLoadAsTheEndComesIntoView() = runTest {
        val server = app.signedIn()
        server.listOf(listOf("a", "b", "c"), pageSize = 2)
        val viewModel = viewModel(HomeFilter.Movies)
        val first = viewModel.list { !it.loading }
        assertEquals(2, first.items.size)
        assertTrue(first.hasMore)

        viewModel.onLoadMore()
        val all = viewModel.list { it.items.size == 3 }

        assertFalse(all.hasMore)
        assertFalse(all.loadingMore)
    }

    @Test
    fun aPageWithNothingInItIsEmptyNotLoading() = runTest {
        app.signedIn().listOf(emptyList(), 60)

        val state = viewModel(HomeFilter.Genre("g-empty", "Western")).list { !it.loading }

        assertTrue(state.isEmpty)
    }

    @Test
    fun aFailedFirstPageShowsAMessageAndRetries() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel(HomeFilter.Shows)
        assertNotNull(viewModel.list { !it.loading }.message)

        server.listOf(listOf("a"), 60)
        viewModel.onRetry()
        val state = viewModel.list { it.items.isNotEmpty() }

        assertNull(state.message)
    }

    @Test
    fun aRejectedTokenLeavesThePage() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.status(HttpStatusCode.Unauthorized))

        val state = viewModel(HomeFilter.Movies).list { it.exit != null }

        assertNotNull(state.exit)
    }

    // The buttons above the list and the picker they open.

    private fun FakeServer.categorised() {
        on("GET", "/Genres", Reply.json(itemsJson(itemJson("g1", "Action", type = "Genre", movieCount = 6, seriesCount = 2), itemJson("g2", "Drama", type = "Genre", movieCount = 8, seriesCount = 1))))
        on("GET", "/Items", Reply.json(itemsJson(itemJson("a", year = 2026), itemJson("b", year = 2025), itemJson("c", year = 1999))))
    }

    @Test
    fun aPageStartsWithItsDefaultOrderAndNothingNarrowed() = runTest {
        app.signedIn().categorised()

        val shows = viewModel(HomeFilter.Shows).uiState.first { !it.list.loading }
        assertEquals(listOf("Sort: Name", "Genre"), shows.chips.map { it.label })
        assertEquals(listOf(false, false), shows.chips.map { it.active })

        val new = viewModel(HomeFilter.New).uiState.first { !it.list.loading }
        assertEquals("Sort: Recently added", new.chips.first().label)
        assertFalse(new.chips.first().active)
    }

    @Test
    fun aGenresOwnPageHasNoGenreButton() = runTest {
        app.signedIn().categorised()

        val state = viewModel(HomeFilter.Genre("g1", "Action")).uiState.first { !it.list.loading }

        assertEquals(listOf("Sort: Name"), state.chips.map { it.label })
    }

    @Test
    fun sortOpensItsThreeChoicesWithTheCurrentOneChecked() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Movies)
        viewModel.uiState.first { !it.list.loading }

        viewModel.onChip(ListChipKind.Sort)
        val sheet = assertNotNull(viewModel.uiState.value.sheet)

        assertEquals("Sort by", sheet.title)
        assertEquals(listOf("Name", "Recently added", "Rating"), sheet.options.map { it.name })
        assertEquals("Name", sheet.selectedId)
    }

    @Test
    fun pickingAnotherSortReloadsTheListInThatOrder() = runTest {
        val server = app.signedIn()
        server.categorised()
        val viewModel = viewModel(HomeFilter.Movies)
        viewModel.uiState.first { !it.list.loading }

        viewModel.onChip(ListChipKind.Sort)
        viewModel.onPick("Rating")
        val state = viewModel.uiState.first { !it.list.loading && it.chips.first().active }

        assertEquals("Sort: Rating", state.chips.first().label)
        assertNull(state.sheet)
        val last = app.jellyfin.requestsTo("GET", "/Items").last().query
        assertEquals("CommunityRating,SortName", last["sortBy"])
        assertEquals("0", last["startIndex"])
    }

    @Test
    fun theGenrePickerNarrowsAShowsPageAndAllGenresClearsIt() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Shows)
        viewModel.uiState.first { !it.list.loading }

        viewModel.onChip(ListChipKind.Genre)
        val sheet = assertNotNull(viewModel.uiState.first { it.sheet?.loading == false }.sheet)
        assertEquals(listOf("All genres", "Action", "Drama"), sheet.options.map { it.name })
        // Shows only: the counts are of shows, not movies.
        assertEquals(listOf(null, 2, 1), sheet.options.map { it.count })

        viewModel.onPick("g1")
        val narrowed = viewModel.uiState.first { !it.list.loading && it.chips[1].active }
        assertEquals("Genre: Action", narrowed.chips[1].label)
        assertEquals("g1", app.jellyfin.requestsTo("GET", "/Items").last().query["genreIds"])

        viewModel.onChip(ListChipKind.Genre)
        assertEquals("g1", viewModel.uiState.value.sheet?.selectedId)
        viewModel.onPick("")
        val cleared = viewModel.uiState.first { !it.list.loading && !it.chips[1].active }
        assertEquals("Genre", cleared.chips[1].label)
    }

    @Test
    fun aPickerThatFailsToLoadSaysSoRetriesAndKeepsThePage() = runTest {
        val server = app.signedIn()
        server.categorised()
        server.on("GET", "/Genres", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel(HomeFilter.Movies)
        viewModel.uiState.first { !it.list.loading }

        viewModel.onChip(ListChipKind.Genre)
        val failed = assertNotNull(viewModel.uiState.first { it.sheet?.loading == false }.sheet)
        assertNotNull(failed.message)
        assertEquals(3, viewModel.uiState.value.list.items.size)

        server.on("GET", "/Genres", Reply.json(itemsJson(itemJson("g1", "Action", type = "Genre", movieCount = 6))))
        viewModel.onRetrySheet()
        val retried = assertNotNull(viewModel.uiState.first { it.sheet?.options?.isNotEmpty() == true }.sheet)
        assertNull(retried.message)
    }

    @Test
    fun dismissingThePickerChangesNothing() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Movies)
        viewModel.uiState.first { !it.list.loading }
        val requests = app.jellyfin.requestsTo("GET", "/Items").size

        viewModel.onChip(ListChipKind.Sort)
        viewModel.onDismissSheet()
        viewModel.onChip(ListChipKind.Sort)
        viewModel.onPick("Name")

        assertNull(viewModel.uiState.value.sheet)
        assertEquals(requests, app.jellyfin.requestsTo("GET", "/Items").size)
    }

    // Genre counts follow the page.

    @Test
    fun theGenrePickerOnTheMoviesPageCountsOnlyMovies() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("a"))))
        server.on(
            "GET", "/Genres",
            Reply.json(
                itemsJson(
                    itemJson("g1", "Drama", type = "Genre", movieCount = 8, seriesCount = 1),
                    itemJson("g2", "Sitcom", type = "Genre", movieCount = 0, seriesCount = 3),
                ),
            ),
        )
        val viewModel = viewModel(HomeFilter.Movies)
        viewModel.uiState.first { !it.list.loading }

        viewModel.onChip(ListChipKind.Genre)
        val sheet = assertNotNull(viewModel.uiState.first { it.sheet?.loading == false }.sheet)

        assertEquals(listOf("All genres", "Drama"), sheet.options.map { it.name })
        assertEquals(listOf(null, 8), sheet.options.map { it.count })
        assertEquals("Movie", app.jellyfin.requestsTo("GET", "/Genres").single().query["includeItemTypes"])
    }

    @Test
    fun theGenrePickerOnTheShowsPageCountsOnlyShows() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("a"))))
        server.on(
            "GET", "/Genres",
            Reply.json(
                itemsJson(
                    itemJson("g1", "Drama", type = "Genre", movieCount = 8, seriesCount = 1),
                    itemJson("g2", "Sitcom", type = "Genre", movieCount = 0, seriesCount = 3),
                ),
            ),
        )
        val viewModel = viewModel(HomeFilter.Shows)
        viewModel.uiState.first { !it.list.loading }

        viewModel.onChip(ListChipKind.Genre)
        val sheet = assertNotNull(viewModel.uiState.first { it.sheet?.loading == false }.sheet)

        assertEquals(listOf("All genres", "Drama", "Sitcom"), sheet.options.map { it.name })
        assertEquals(listOf(null, 1, 3), sheet.options.map { it.count })
    }
}
