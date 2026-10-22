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

    private fun FakeServer.years(vararg years: Int?) =
        on("GET", "/Items") { request ->
            val start = request.query["startIndex"]!!.toInt()
            val page = years.drop(start).take(3)
            Reply.json(
                itemsJson(
                    *page.mapIndexed { i, year -> itemJson("t${start + i}", year = year) }.toTypedArray(),
                    total = years.size,
                    startIndex = start,
                ),
            )
        }

    @Test
    fun theNewPageNamesEachReleaseYearAboveItsTitles() = runTest {
        app.signedIn().years(2026, 2026, 2025, 1999)
        val viewModel = viewModel(HomeFilter.New)

        val first = viewModel.list { !it.loading }
        assertEquals(mapOf("t0" to "2026", "t2" to "2025"), first.headings)

        viewModel.onLoadMore()
        val all = viewModel.list { it.items.size == 4 }

        assertEquals(mapOf("t0" to "2026", "t2" to "2025", "t3" to "1999"), all.headings)
    }

    @Test
    fun aYearThatContinuesOverAPageGetsNoSecondSubtitle() = runTest {
        app.signedIn().years(2026, 2026, 2026, 2026, 2025)
        val viewModel = viewModel(HomeFilter.New)
        viewModel.list { !it.loading }

        viewModel.onLoadMore()
        val all = viewModel.list { it.items.size == 5 }

        assertEquals(mapOf("t0" to "2026", "t4" to "2025"), all.headings)
    }

    @Test
    fun titlesWithNoYearAreGroupedAsUnknown() = runTest {
        app.signedIn().years(2020, null)

        val state = viewModel(HomeFilter.New).list { !it.loading }

        assertEquals(mapOf("t0" to "2020", "t1" to "Unknown year"), state.headings)
    }

    @Test
    fun theOtherPagesAreAPlainListWithNoSubtitles() = runTest {
        app.signedIn().years(2026, 2025)

        assertTrue(viewModel(HomeFilter.Movies).list { !it.loading }.headings.isEmpty())
        assertTrue(viewModel(HomeFilter.Genre("g1", "Drama")).list { !it.loading }.headings.isEmpty())
    }

    // The buttons above the list and the picker they open.

    private fun FakeServer.categorised() {
        on("GET", "/Years", Reply.json(itemsJson(itemJson("y1", "2026", type = "Year"), itemJson("y2", "2025", type = "Year"), itemJson("y3", "1999", type = "Year"))))
        on("GET", "/Genres", Reply.json(itemsJson(itemJson("g1", "Action", type = "Genre", movieCount = 6), itemJson("g2", "Drama", type = "Genre", movieCount = 8))))
        on("GET", "/Items", Reply.json(itemsJson(itemJson("a", year = 2026))))
    }

    @Test
    fun aPageStartsWithItsDefaultOrderAndNothingNarrowed() = runTest {
        app.signedIn().categorised()

        val shows = viewModel(HomeFilter.Shows).uiState.first { !it.list.loading }
        assertEquals(listOf("Sort: Name", "Genre"), shows.chips.map { it.label })
        assertEquals(listOf(false, false), shows.chips.map { it.active })

        val new = viewModel(HomeFilter.New).uiState.first { !it.list.loading }
        assertEquals("Sort: Release year", new.chips.first().label)
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
        assertEquals(listOf("Name", "Release year", "Rating"), sheet.options.map { it.name })
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
        assertEquals(listOf(null, 6, 8), sheet.options.map { it.count })

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
    fun aPageSortedByYearNamesTheYearsWhateverThePage() = runTest {
        val server = app.signedIn()
        server.categorised()
        val viewModel = viewModel(HomeFilter.Movies)
        assertTrue(viewModel.uiState.first { !it.list.loading }.list.headings.isEmpty())

        viewModel.onChip(ListChipKind.Sort)
        viewModel.onPick("Year")
        val byYear = viewModel.uiState.first { !it.list.loading && it.list.headings.isNotEmpty() }

        assertEquals(mapOf("a" to "2026"), byYear.list.headings)
    }

    @Test
    fun theNewPageSortedByNameDropsItsYearSubtitles() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.New)
        assertTrue(viewModel.uiState.first { !it.list.loading }.list.headings.isNotEmpty())

        viewModel.onChip(ListChipKind.Sort)
        viewModel.onPick("Name")
        val byName = viewModel.uiState.first { !it.list.loading && it.list.headings.isEmpty() }

        assertEquals("Sort: Name", byName.chips.first().label)
        assertTrue(byName.chips.first().active)
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
        assertEquals(1, viewModel.uiState.value.list.items.size)

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

    // The strip of every year, and jumping to one.

    @Test
    fun theNewPageListsEveryYearThereIsNotOnlyTheOnesLoaded() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Years", Reply.json(itemsJson(itemJson("y1", "2026", type = "Year"), itemJson("y2", "2025", type = "Year"), itemJson("y3", "1999", type = "Year"), itemJson("y4", "Unknown", type = "Year"))))
        server.years(2026, 2026, 2026, 2025, 1999)

        val state = viewModel(HomeFilter.New).uiState.first { !it.list.loading && it.years.isNotEmpty() }

        assertEquals(listOf("2026", "2025", "1999"), state.years)
        // Only the first page is loaded, so only 2026 has a subtitle yet.
        assertEquals(mapOf("t0" to "2026"), state.list.headings)
    }

    @Test
    fun aPageNotOrderedByYearHasNoYearStripUntilItIsSortedByYear() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Movies)
        assertTrue(viewModel.uiState.first { !it.list.loading }.years.isEmpty())

        viewModel.onChip(ListChipKind.Sort)
        viewModel.onPick("Year")
        val byYear = viewModel.uiState.first { !it.list.loading && it.years.isNotEmpty() }

        assertEquals(listOf("2026", "2025", "1999"), byYear.years)
    }

    @Test
    fun aLoadedYearIsJumpedToAtOnce() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Years", Reply.json(itemsJson(itemJson("y1", "2026", type = "Year"), itemJson("y2", "2025", type = "Year"))))
        server.years(2026, 2026, 2025, 2025)
        val viewModel = viewModel(HomeFilter.New)
        viewModel.uiState.first { !it.list.loading && it.years.isNotEmpty() }

        viewModel.onYear("2026")
        assertEquals("2026", viewModel.uiState.first { it.jumpTo != null }.jumpTo)

        viewModel.onJumped()
        assertNull(viewModel.uiState.value.jumpTo)
        assertEquals(1, app.jellyfin.requestsTo("GET", "/Items").size)
    }

    @Test
    fun aYearNotLoadedYetLoadsPagesDownToItAndThenIsJumpedTo() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Years", Reply.json(itemsJson(itemJson("y1", "2026", type = "Year"), itemJson("y2", "2010", type = "Year"))))
        // Three per page: 2026 x3, 2025 x3, 2010 x1.
        server.years(2026, 2026, 2026, 2025, 2025, 2025, 2010)
        val viewModel = viewModel(HomeFilter.New)
        viewModel.uiState.first { !it.list.loading && it.years.isNotEmpty() }
        assertEquals(mapOf("t0" to "2026"), viewModel.uiState.value.list.headings)

        viewModel.onYear("2010")
        val arrived = viewModel.uiState.first { it.list.headings.containsValue("2010") }

        assertEquals("2010", arrived.jumpTo)
        assertEquals(7, arrived.list.items.size)
        assertFalse(arrived.list.hasMore)
    }

    @Test
    fun aYearThatCannotBeReachedGivesUpWithoutJumping() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Years", Reply.json(itemsJson(itemJson("y1", "2026", type = "Year"), itemJson("y2", "1980", type = "Year"))))
        server.years(2026, 2026)
        val viewModel = viewModel(HomeFilter.New)
        viewModel.uiState.first { !it.list.loading && it.years.isNotEmpty() }

        viewModel.onYear("1980")

        assertNull(viewModel.uiState.value.jumpTo)
    }

    @Test
    fun theStripFallsBackToNothingExtraWhenTheYearsCannotBeLoaded() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Years", Reply.status(HttpStatusCode.ServiceUnavailable))
        server.years(2026, 2025)

        val state = viewModel(HomeFilter.New).uiState.first { !it.list.loading }

        assertTrue(state.years.isEmpty())
        // The page and its subtitles still work; the screen lists the years that are loaded.
        assertEquals(mapOf("t0" to "2026", "t1" to "2025"), state.list.headings)
    }
}
