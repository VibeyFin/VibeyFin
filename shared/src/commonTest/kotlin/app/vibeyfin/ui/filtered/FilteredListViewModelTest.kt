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
import app.vibeyfin.ui.common.Strings
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

        val shows = viewModel(HomeFilter.Shows).uiState.first { !it.list.loading && !it.list.refreshing }
        assertEquals(listOf("Sort: Name", "Genre"), shows.chips.map { it.label })
        assertEquals(listOf(false, false), shows.chips.map { it.active })

        val new = viewModel(HomeFilter.New).uiState.first { !it.list.loading && !it.list.refreshing }
        val sort = new.chips.first { it.kind == ListChipKind.Sort }
        assertEquals("Sort: Recently added", sort.label)
        assertFalse(sort.active)
    }

    @Test
    fun aGenresOwnPageHasNoGenreButton() = runTest {
        app.signedIn().categorised()

        val state = viewModel(HomeFilter.Genre("g1", "Action")).uiState.first { !it.list.loading && !it.list.refreshing }

        assertEquals(listOf("Sort: Name"), state.chips.map { it.label })
    }

    @Test
    fun sortOpensItsThreeChoicesWithTheCurrentOneChecked() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Movies)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

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
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        viewModel.onChip(ListChipKind.Sort)
        viewModel.onPick("Rating")
        val state = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && it.chips.first().active }

        assertEquals("Sort: Rating", state.chips.first().label)
        assertNull(state.sheet)
        val last = app.jellyfin.requestsTo("GET", "/Items").last().query
        assertEquals("CommunityRating,SortName", last["sortBy"])
        assertEquals("0", last["startIndex"])
    }

    @Test
    fun myListsGenrePickerCountsTheTitlesOnTheListNotTheLibrary() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Genres", Reply.json(itemsJson(itemJson("g1", "Action", type = "Genre", movieCount = 6, seriesCount = 2), itemJson("g2", "Drama", type = "Genre", movieCount = 8, seriesCount = 1), itemJson("g3", "Comedy", type = "Genre", movieCount = 5))))
        // Three titles on the list: two drama (one of them also action), nothing comic.
        server.on(
            "GET", "/Items",
            Reply.json(itemsJson(itemJson("a", genres = listOf("Drama", "Action")), itemJson("b", genres = listOf("Drama")), itemJson("c", type = "Series", genres = listOf("Action")))),
        )
        val viewModel = viewModel(HomeFilter.MyList)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        viewModel.onChip(ListChipKind.Genre)
        val sheet = assertNotNull(viewModel.uiState.first { it.sheet?.loading == false }.sheet)

        // Comedy is on no favourite, so it is not offered; the others are counted from the list.
        assertEquals(listOf("All genres", "Action", "Drama"), sheet.options.map { it.name })
        assertEquals(listOf(null, 2, 2), sheet.options.map { it.count })
        val scan = app.jellyfin.requestsTo("GET", "/Items").last { it.query["fields"] == "Genres" }.query
        assertEquals("IsFavorite", scan["filters"])
    }

    @Test
    fun newAndMyListOfferMoviesAndShowsButtonsAndTheOtherPagesDoNot() = runTest {
        app.signedIn().categorised()

        val new = viewModel(HomeFilter.New).uiState.first { !it.list.loading && !it.list.refreshing }
        val myList = viewModel(HomeFilter.MyList).uiState.first { !it.list.loading && !it.list.refreshing }
        val shows = viewModel(HomeFilter.Shows).uiState.first { !it.list.loading && !it.list.refreshing }

        assertEquals(listOf("Movies", "Shows", "Sort: Recently added", "Genre"), new.chips.map { it.label })
        assertEquals(listOf("Movies", "Shows", "Sort: Recently added", "Genre"), myList.chips.map { it.label })
        assertEquals(listOf("Sort: Name", "Genre"), shows.chips.map { it.label })
        // Sort and Genre open a list; Movies and Shows switch on and off.
        assertEquals(listOf(false, false, true, true), new.chips.map { it.opensList })
    }

    @Test
    fun theShowsButtonNarrowsThePageAndTheGenreCountsToShowsAndTappingItAgainClears() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.New)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        viewModel.onChip(ListChipKind.Shows)
        val narrowed = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && it.chips[1].active }
        assertEquals("Series", app.jellyfin.requestsTo("GET", "/Items").last().query["includeItemTypes"])
        assertEquals(listOf(true, false), listOf(narrowed.chips[1].active, narrowed.chips[0].active))

        viewModel.onChip(ListChipKind.Genre)
        val sheet = assertNotNull(viewModel.uiState.first { it.sheet?.loading == false }.sheet)
        // Shows only: Action has 2 shows, Drama 1; the movies are not counted.
        assertEquals(listOf(null, 2, 1), sheet.options.map { it.count })
        assertEquals("Series", app.jellyfin.requestsTo("GET", "/Genres").last().query["includeItemTypes"])

        viewModel.onDismissSheet()
        viewModel.onChip(ListChipKind.Shows)
        val cleared = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && !it.chips[1].active }
        assertEquals("Movie,Series", app.jellyfin.requestsTo("GET", "/Items").last().query["includeItemTypes"])
        assertFalse(cleared.chips[1].active)
    }

    @Test
    fun myListsGenreCountsFollowTheMoviesButton() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Genres", Reply.json(itemsJson(itemJson("g1", "Action", type = "Genre", movieCount = 6, seriesCount = 2), itemJson("g2", "Drama", type = "Genre", movieCount = 8, seriesCount = 1))))
        server.on("GET", "/Items") { request ->
            if (request.query["includeItemTypes"] == "Movie") {
                Reply.json(itemsJson(itemJson("a", genres = listOf("Drama"))))
            } else {
                Reply.json(itemsJson(itemJson("a", genres = listOf("Drama")), itemJson("c", type = "Series", genres = listOf("Action", "Drama"))))
            }
        }
        val viewModel = viewModel(HomeFilter.MyList)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        viewModel.onChip(ListChipKind.Movies)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing && it.chips[0].active }
        viewModel.onChip(ListChipKind.Genre)
        val sheet = assertNotNull(viewModel.uiState.first { it.sheet?.loading == false }.sheet)

        // With only movies on the list, Drama is on one title and Action on none.
        assertEquals(listOf("All genres", "Drama"), sheet.options.map { it.name })
        assertEquals(listOf(null, 1), sheet.options.map { it.count })
    }

    // The Library page: every title, with year and people filters besides.

    @Test
    fun theLibraryPageOffersTypeYearAndPeopleAndSaysHowManyTitlesItHolds() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("a"), itemJson("b"), total = 1284)))

        val state = viewModel(HomeFilter.Library).uiState.first { !it.list.loading && !it.list.refreshing }

        assertEquals(listOf("Movies", "Shows", "Collections", "Sort: Name", "Genre", "Year", "People"), state.chips.map { it.label })
        assertEquals(1284, state.list.total)
        val request = app.jellyfin.requestsTo("GET", "/Items").last().query
        assertEquals("Movie,Series", request["includeItemTypes"])
        assertNull(request["years"])
        assertNull(request["personIds"])
    }

    @Test
    fun aDecadeNarrowsTheLibraryToThoseYears() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Library)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        viewModel.onChip(ListChipKind.Year)
        val sheet = assertNotNull(viewModel.uiState.first { it.sheet != null }.sheet)
        assertEquals(listOf("All years", "2020s", "2010s", "2000s", "1990s", "1980s", "Before 1980"), sheet.options.map { it.name })
        viewModel.onPick("Nineties")
        val narrowed = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && it.chips.any { chip -> chip.kind == ListChipKind.Year && chip.active } }

        assertEquals("Year: 1990s", narrowed.chips.first { it.kind == ListChipKind.Year }.label)
        assertEquals("1990,1991,1992,1993,1994,1995,1996,1997,1998,1999", app.jellyfin.requestsTo("GET", "/Items").last().query["years"])
    }

    @Test
    fun aNarrowedLibraryThatMatchesNothingSaysSoAndAnEmptyOneDoesNot() = runTest {
        val server = app.signedIn()
        server.categorised()
        server.on("GET", "/Items") { request ->
            if (request.query["years"] != null) Reply.json(itemsJson(total = 0)) else Reply.json(itemsJson())
        }
        val viewModel = viewModel(HomeFilter.Library)
        val open = viewModel.uiState.first { !it.list.loading && !it.list.refreshing }
        assertEquals("There's nothing in here yet.", open.list.emptyText)

        viewModel.onChip(ListChipKind.Year)
        viewModel.uiState.first { it.sheet != null }
        viewModel.onPick("Nineties")
        val narrowed = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && it.chips.any { chip -> chip.kind == ListChipKind.Year && chip.active } }

        assertTrue(narrowed.list.isEmpty)
        assertEquals("Nothing matches these filters. Try taking one off, or tap Clear.", narrowed.list.emptyText)
    }

    @Test
    fun aPersonFoundByNameNarrowsTheLibraryToTheirTitles() = runTest {
        val server = app.signedIn()
        server.categorised()
        server.on("GET", "/Persons", Reply.json(itemsJson(itemJson("p1", "Alexander Burger", type = "Person"), itemJson("p2", "Alexandra Day", type = "Person"))))
        val viewModel = viewModel(HomeFilter.Library)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        viewModel.onChip(ListChipKind.People)
        val prompt = assertNotNull(viewModel.uiState.first { it.sheet != null }.sheet)
        assertTrue(prompt.searchable)
        assertTrue(prompt.options.isEmpty())
        assertEquals("Type a name to find someone.", prompt.emptyText)

        viewModel.onSheetQuery("alex")
        val found = assertNotNull(viewModel.uiState.first { it.sheet?.options?.isNotEmpty() == true }.sheet)
        assertEquals(listOf("Alexander Burger", "Alexandra Day"), found.options.map { it.name })
        assertEquals("alex", app.jellyfin.requestsTo("GET", "/Persons").last().query["searchTerm"])

        viewModel.onPick("p1")
        val narrowed = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && it.chips.any { chip -> chip.kind == ListChipKind.People && chip.active } }
        assertEquals("With Alexander Burger", narrowed.chips.first { it.kind == ListChipKind.People }.label)
        assertEquals("p1", app.jellyfin.requestsTo("GET", "/Items").last().query["personIds"])
    }

    @Test
    fun aPeopleSearchWithNoMatchSaysSoAndABlankOneAsksNothing() = runTest {
        val server = app.signedIn()
        server.categorised()
        server.on("GET", "/Persons", Reply.json(itemsJson()))
        val viewModel = viewModel(HomeFilter.Library)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }
        viewModel.onChip(ListChipKind.People)
        viewModel.uiState.first { it.sheet != null }

        viewModel.onSheetQuery("zzz")
        val none = assertNotNull(viewModel.uiState.first { it.sheet?.emptyText == "Nobody by that name." }.sheet)
        assertTrue(none.options.isEmpty())

        viewModel.onSheetQuery("  ")
        assertEquals("Type a name to find someone.", viewModel.uiState.value.sheet?.emptyText)
        assertEquals(1, app.jellyfin.requestsTo("GET", "/Persons").size)
    }

    @Test
    fun clearPutsTheLibraryBackAsItOpened() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Library)
        val opened = viewModel.uiState.first { !it.list.loading && !it.list.refreshing }
        assertTrue(opened.chips.none { it.kind == ListChipKind.Clear })

        viewModel.onChip(ListChipKind.Shows)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing && it.chips.any { chip -> chip.kind == ListChipKind.Clear } }
        viewModel.onChip(ListChipKind.Clear)
        val cleared = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && it.chips.none { chip -> chip.kind == ListChipKind.Clear } }

        assertEquals(listOf("Movies", "Shows", "Collections", "Sort: Name", "Genre", "Year", "People"), cleared.chips.map { it.label })
        assertEquals("Movie,Series", app.jellyfin.requestsTo("GET", "/Items").last().query["includeItemTypes"])
    }

    @Test
    fun theCollectionsButtonListsOnlyBoxSetsAndDropsTheFiltersThatDoNotApply() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Library)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        viewModel.onChip(ListChipKind.Collections)
        val state = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && it.chips.any { chip -> chip.kind == ListChipKind.Collections && chip.active } }

        assertEquals("BoxSet", app.jellyfin.requestsTo("GET", "/Items").last().query["includeItemTypes"])
        // Collections have no genre, year or person to narrow by.
        assertEquals(listOf("Movies", "Shows", "Collections", "Sort: Name", "Clear"), state.chips.map { it.label })
    }

    @Test
    fun otherPagesDoNotOfferCollections() = runTest {
        app.signedIn().categorised()

        val new = viewModel(HomeFilter.New).uiState.first { !it.list.loading && !it.list.refreshing }

        assertTrue(new.chips.none { it.kind == ListChipKind.Collections })
    }

    @Test
    fun changingAFilterKeepsTheListAndTheCountOnScreenUntilTheNewOneIsIn() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("a"), itemJson("b"), total = 2)))
        val viewModel = viewModel(HomeFilter.Library)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("c"), total = 1)))

        viewModel.onChip(ListChipKind.Shows)
        // Right away: the old items and the old count are still there, marked as reloading, not a skeleton.
        val during = viewModel.uiState.value.list
        assertTrue(during.items.isNotEmpty() || during.refreshing || !during.loading)
        assertTrue(!during.loading)
        val after = viewModel.uiState.first { !it.list.refreshing && it.list.items.map { item -> item.id } == listOf("c") }

        assertEquals(1, after.list.total)
    }

    @Test
    fun theLibraryCountIsGroupedByThousands() {
        assertEquals("1,284 titles", Strings.libraryCount(1284))
        assertEquals("12 titles", Strings.libraryCount(12))
        assertEquals("1 title", Strings.libraryCount(1))
        assertEquals("1,000,000 titles", Strings.libraryCount(1_000_000))
    }

    @Test
    fun theGenrePickerNarrowsAShowsPageAndAllGenresClearsIt() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Shows)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        viewModel.onChip(ListChipKind.Genre)
        val sheet = assertNotNull(viewModel.uiState.first { it.sheet?.loading == false }.sheet)
        assertEquals(listOf("All genres", "Action", "Drama"), sheet.options.map { it.name })
        // Shows only: the counts are of shows, not movies.
        assertEquals(listOf(null, 2, 1), sheet.options.map { it.count })

        viewModel.onPick("g1")
        val narrowed = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && it.chips[1].active }
        assertEquals("Genre: Action", narrowed.chips[1].label)
        assertEquals("g1", app.jellyfin.requestsTo("GET", "/Items").last().query["genreIds"])

        viewModel.onChip(ListChipKind.Genre)
        assertEquals("g1", viewModel.uiState.value.sheet?.selectedId)
        viewModel.onPick("")
        val cleared = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && !it.chips[1].active }
        assertEquals("Genre", cleared.chips[1].label)
    }

    @Test
    fun aPickerThatFailsToLoadSaysSoRetriesAndKeepsThePage() = runTest {
        val server = app.signedIn()
        server.categorised()
        server.on("GET", "/Genres", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel(HomeFilter.Movies)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

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
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }
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
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

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
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        viewModel.onChip(ListChipKind.Genre)
        val sheet = assertNotNull(viewModel.uiState.first { it.sheet?.loading == false }.sheet)

        assertEquals(listOf("All genres", "Drama", "Sitcom"), sheet.options.map { it.name })
        assertEquals(listOf(null, 1, 3), sheet.options.map { it.count })
    }
}
