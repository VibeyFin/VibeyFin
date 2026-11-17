package app.vibeyfin.ui.filtered

import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.domain.SortOrder
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TICKS_PER_MINUTE
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import app.vibeyfin.ui.children.ChildrenUiState
import app.vibeyfin.ui.common.Strings
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

    private fun viewModel(filter: HomeFilter) = viewModels.track(FilteredListViewModel(filter, app.library, app.connection, app.watchChanges))

    private fun FakeServer.listOf(all: List<String>, pageSize: Int) = on("GET", "/Items") { request ->
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
        filterValues()
    }

    // What the Filters list and the Year sheet pick from: one library of films with two years, two age ratings, a tag and a studio.
    private fun FakeServer.filterValues(libraries: List<String> = listOf(itemJson("lib-movies", "Movies", type = "CollectionFolder", isFolder = true, collectionType = "movies"))) {
        on("GET", "/UserViews", Reply.json(itemsJson(*libraries.toTypedArray())))
        on("GET", "/Items/Filters2", Reply.json("""{"Genres":[],"Tags":["dance"],"AudioLanguages":[{"Name":"German (ger)","Value":"ger"},{"Name":"Undetermined (und)","Value":"und"}],"SubtitleLanguages":[]}"""))
        on("GET", "/Items/Filters", Reply.json("""{"Genres":[],"Tags":[],"OfficialRatings":["R","PG-13"],"Years":[1999,2026]}"""))
        on("GET", "/Studios", Reply.json(itemsJson(itemJson("s1", "A24", type = "Studio", movieCount = 4, seriesCount = 1))))
        on("GET", "/Localization/ParentalRatings", Reply.json("""[{"Name":"G","Value":0},{"Name":"PG-13","Value":13},{"Name":"R","Value":17}]"""))
    }

    private fun FilteredListUiState.chip(kind: ListChipKind) = chips.first { it.kind == kind }

    // What a row of the open sheet says it is set to.
    private fun FilteredListUiState.detailOf(id: String) = sheet?.rows?.firstOrNull { it.id == id }?.detail

    private suspend fun FilteredListViewModel.openFromFilters(kind: ListSheetKind): ListSheet {
        onChip(ListChipKind.Filters)
        uiState.first { it.sheet?.kind == ListSheetKind.Filters && !it.sheet.loading }
        onPick(kind.name)
        return assertNotNull(uiState.first { it.sheet?.kind == kind && !it.sheet.loading }.sheet)
    }

    @Test
    fun aPageStartsWithItsDefaultOrderAndNothingNarrowed() = runTest {
        app.signedIn().categorised()

        val shows = viewModel(HomeFilter.Shows).uiState.first { !it.list.loading && !it.list.refreshing }
        assertEquals(listOf("Sort: Name", "Filters"), shows.chips.map { it.label })
        assertTrue(shows.chips.none { it.active })

        val new = viewModel(HomeFilter.New).uiState.first { !it.list.loading && !it.list.refreshing }
        val sort = new.chips.first { it.kind == ListChipKind.Sort }
        assertEquals("Sort: Recently added", sort.label)
        assertFalse(sort.active)
    }

    @Test
    fun aGenresOwnPageHasNoGenreFilter() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Genre("g1", "Action"))
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        val filters = viewModel.openFilters()

        assertTrue(filters.rows.none { it.id == "Genre" })
        assertEquals("Year", filters.rows.first().id)
    }

    @Test
    fun sortOpensEveryOrderWithTheCurrentOneChecked() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Movies)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        viewModel.onChip(ListChipKind.Sort)
        val sheet = assertNotNull(viewModel.uiState.value.sheet)

        assertEquals("Sort by", sheet.title)
        assertEquals(
            listOf("Name", "Recently added", "Rating", "Critic rating", "Release date", "Runtime", "Last played", "Times played", "Random"),
            sheet.rows.map { it.label },
        )
        // Only the order in use says which way it runs.
        val inUse = sheet.rows.single { it.selected }
        assertEquals("Name", inUse.id)
        assertEquals("A to Z", inUse.detail)
        assertEquals(SortOrder.Ascending, inUse.direction)
        assertEquals("Name, A to Z. Tap again to turn it round.", inUse.description)
        assertTrue(sheet.rows.filterNot { it.selected }.all { it.detail == null && it.direction == null })
    }

    @Test
    fun pickingAnotherSortReloadsTheListInThatOrder() = runTest {
        val server = app.signedIn()
        server.categorised()
        val viewModel = viewModel(HomeFilter.Movies)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        viewModel.onChip(ListChipKind.Sort)
        viewModel.onPick("Rating")
        val state = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && it.chip(ListChipKind.Sort).active }

        assertEquals("Sort: Rating", state.chip(ListChipKind.Sort).label)
        // The sheet stays, so the order can be turned round too; Rating runs best first.
        val inUse = state.sheet?.rows?.single { it.selected }
        assertEquals("Rating" to "Highest first", inUse?.id to inUse?.detail)
        val last = app.jellyfin.requestsTo("GET", "/Items").last().query
        assertEquals("CommunityRating,SortName", last["sortBy"])
        assertEquals("0", last["startIndex"])
    }

    @Test
    fun myListsGenrePickerCountsTheTitlesOnTheListNotTheLibrary() = runTest {
        val server = app.signedIn()
        server.filterValues()
        server.on("GET", "/Genres", Reply.json(itemsJson(itemJson("g1", "Action", type = "Genre", movieCount = 6, seriesCount = 2), itemJson("g2", "Drama", type = "Genre", movieCount = 8, seriesCount = 1), itemJson("g3", "Comedy", type = "Genre", movieCount = 5))))
        // Three titles on the list: two drama (one of them also action), nothing comic.
        server.on(
            "GET",
            "/Items",
            Reply.json(itemsJson(itemJson("a", genres = listOf("Drama", "Action")), itemJson("b", genres = listOf("Drama")), itemJson("c", type = "Series", genres = listOf("Action")))),
        )
        val viewModel = viewModel(HomeFilter.MyList)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        val sheet = viewModel.openFromFilters(ListSheetKind.Genre)

        // Comedy is on no favourite, so it is not offered; the others are counted from the list.
        assertEquals(listOf("All genres", "Action", "Drama"), sheet.rows.map { it.label })
        assertEquals(listOf(null, "2", "2"), sheet.rows.map { it.detail })
        val scan = app.jellyfin.requestsTo("GET", "/Items").last { it.query["fields"] == "Genres" }.query
        assertEquals("IsFavorite", scan["filters"])
    }

    @Test
    fun newAndMyListOfferMoviesAndShowsButtonsAndTheOtherPagesDoNot() = runTest {
        app.signedIn().categorised()

        val new = viewModel(HomeFilter.New).uiState.first { !it.list.loading && !it.list.refreshing }
        val myList = viewModel(HomeFilter.MyList).uiState.first { !it.list.loading && !it.list.refreshing }
        val shows = viewModel(HomeFilter.Shows).uiState.first { !it.list.loading && !it.list.refreshing }

        assertEquals(listOf("Movies", "Shows", "Sort: Recently added", "Filters"), new.chips.map { it.label })
        assertEquals(listOf("Movies", "Shows", "Sort: Recently added", "Filters"), myList.chips.map { it.label })
        assertEquals(listOf("Sort: Name", "Filters"), shows.chips.map { it.label })
        // Sort and Filters open a list; Movies and Shows switch.
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

        val sheet = viewModel.openFromFilters(ListSheetKind.Genre)
        // Shows only: Action has 2 shows, Drama 1; the movies are not counted.
        assertEquals(listOf(null, "2", "1"), sheet.rows.map { it.detail })
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
        server.filterValues()
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
        val sheet = viewModel.openFromFilters(ListSheetKind.Genre)

        // With only movies on the list, Drama is on one title and Action on none.
        assertEquals(listOf("All genres", "Drama"), sheet.rows.map { it.label })
        assertEquals(listOf(null, "1"), sheet.rows.map { it.detail })
    }

    // The Library page: every title, with year and people filters besides.

    @Test
    fun theLibraryPageOffersTypeYearAndPeopleAndSaysHowManyTitlesItHolds() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("a"), itemJson("b"), total = 1284)))

        val state = viewModel(HomeFilter.Library).uiState.first { !it.list.loading && !it.list.refreshing }

        assertEquals(listOf("Movies", "Shows", "Collections", "Sort: Name", "Filters", "People"), state.chips.map { it.label })
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

        val sheet = viewModel.openFromFilters(ListSheetKind.Year)
        // The decades, then each year the library holds, newest first.
        assertEquals(listOf("All years", "2020s", "2010s", "2000s", "1990s", "1980s", "Before 1980", "2026", "1999"), sheet.rows.map { it.label })
        viewModel.onPick("Nineties")
        val narrowed = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && it.chip(ListChipKind.Filters).active }

        assertEquals("1990s", narrowed.detailOf("Year"))
        assertEquals("Filters (1)", narrowed.chip(ListChipKind.Filters).label)
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

        viewModel.openFromFilters(ListSheetKind.Year)
        viewModel.onPick("Nineties")
        val narrowed = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && it.chip(ListChipKind.Filters).active }

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
        assertTrue(prompt.rows.isEmpty())
        assertEquals("Type a name to find someone.", prompt.emptyText)

        viewModel.onSheetQuery("alex")
        val found = assertNotNull(viewModel.uiState.first { it.sheet?.rows?.isNotEmpty() == true }.sheet)
        assertEquals(listOf("Alexander Burger", "Alexandra Day"), found.rows.map { it.label })
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
        assertTrue(none.rows.isEmpty())

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

        assertEquals(listOf("Movies", "Shows", "Collections", "Sort: Name", "Filters", "People"), cleared.chips.map { it.label })
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
    fun theGenrePickerNarrowsAShowsPageAndAllGenresClearsIt() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Shows)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        val sheet = viewModel.openFromFilters(ListSheetKind.Genre)
        assertEquals(listOf("All genres", "Action", "Drama"), sheet.rows.map { it.label })
        // Shows only: the counts are of shows, not movies.
        assertEquals(listOf(null, "2", "1"), sheet.rows.map { it.detail })

        viewModel.onPick("g1")
        val narrowed = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && it.chip(ListChipKind.Filters).active }
        assertEquals("g1", app.jellyfin.requestsTo("GET", "/Items").last().query["genreIds"])
        // Genres take several: the sheet stays open with the pick lit.
        assertEquals(listOf("g1"), narrowed.sheet?.rows?.filter { it.selected }?.map { it.id })

        viewModel.onPick("")
        val cleared = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && !it.chip(ListChipKind.Filters).active }
        // All genres clears them and goes back to the Filters list.
        assertEquals("Any", cleared.detailOf("Genre"))
    }

    @Test
    fun aPickerThatFailsToLoadSaysSoRetriesAndKeepsThePage() = runTest {
        val server = app.signedIn()
        server.categorised()
        server.on("GET", "/Genres", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel(HomeFilter.Movies)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        val failed = viewModel.openFromFilters(ListSheetKind.Genre)
        assertNotNull(failed.message)
        assertEquals(3, viewModel.uiState.value.list.items.size)

        server.on("GET", "/Genres", Reply.json(itemsJson(itemJson("g1", "Action", type = "Genre", movieCount = 6))))
        viewModel.onRetrySheet()
        val retried = assertNotNull(viewModel.uiState.first { it.sheet?.rows?.isNotEmpty() == true }.sheet)
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

        assertNull(viewModel.uiState.value.sheet)
        assertEquals(requests, app.jellyfin.requestsTo("GET", "/Items").size)
    }

    // Genre counts follow the page.

    @Test
    fun theGenrePickerOnTheMoviesPageCountsOnlyMovies() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("a"))))
        server.filterValues()
        server.on(
            "GET",
            "/Genres",
            Reply.json(
                itemsJson(
                    itemJson("g1", "Drama", type = "Genre", movieCount = 8, seriesCount = 1),
                    itemJson("g2", "Sitcom", type = "Genre", movieCount = 0, seriesCount = 3),
                ),
            ),
        )
        val viewModel = viewModel(HomeFilter.Movies)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        val sheet = viewModel.openFromFilters(ListSheetKind.Genre)

        assertEquals(listOf("All genres", "Drama"), sheet.rows.map { it.label })
        assertEquals(listOf(null, "8"), sheet.rows.map { it.detail })
        assertEquals("Movie", app.jellyfin.requestsTo("GET", "/Genres").single().query["includeItemTypes"])
    }

    @Test
    fun theGenrePickerOnTheShowsPageCountsOnlyShows() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("a"))))
        server.filterValues()
        server.on(
            "GET",
            "/Genres",
            Reply.json(
                itemsJson(
                    itemJson("g1", "Drama", type = "Genre", movieCount = 8, seriesCount = 1),
                    itemJson("g2", "Sitcom", type = "Genre", movieCount = 0, seriesCount = 3),
                ),
            ),
        )
        val viewModel = viewModel(HomeFilter.Shows)
        viewModel.uiState.first { !it.list.loading && !it.list.refreshing }

        val sheet = viewModel.openFromFilters(ListSheetKind.Genre)

        assertEquals(listOf("All genres", "Drama", "Sitcom"), sheet.rows.map { it.label })
        assertEquals(listOf(null, "1", "3"), sheet.rows.map { it.detail })
    }

    // My List follows favourite changes made anywhere (a poster's menu, a title's page).

    @Test
    fun aTitleTakenOffMyListLeavesThePageAtOnce() = runTest {
        val server = app.signedIn()
        server.listOf(listOf("a", "b", "c"), 60)
        server.on("DELETE", "/UserFavoriteItems/b", Reply.json("{}"))
        val viewModel = viewModel(HomeFilter.MyList)
        viewModel.list { !it.loading }

        app.watch.setFavorite("b", favorite = false)
        val state = viewModel.list { it.items.size == 2 }

        assertEquals(listOf("a", "c"), state.items.map { it.id })
        assertEquals(2, state.total)
    }

    @Test
    fun takingTheLastTitleOffMyListShowsTheEmptyPage() = runTest {
        val server = app.signedIn()
        server.listOf(listOf("a"), 60)
        server.on("DELETE", "/UserFavoriteItems/a", Reply.json("{}"))
        val viewModel = viewModel(HomeFilter.MyList)
        viewModel.list { !it.loading }

        app.watch.setFavorite("a", favorite = false)
        val state = viewModel.list { it.items.isEmpty() }

        assertTrue(state.isEmpty)
        assertEquals(Strings.emptyFolder, state.emptyText)
    }

    @Test
    fun aTitlePutBackOnMyListShowsOnThePageAgain() = runTest {
        val server = app.signedIn()
        server.listOf(listOf("a"), 60)
        server.on("POST", "/UserFavoriteItems/b", Reply.json("{}"))
        val viewModel = viewModel(HomeFilter.MyList)
        viewModel.list { !it.loading }

        server.listOf(listOf("b", "a"), 60)
        app.watch.setFavorite("b", favorite = true)
        val state = viewModel.list { it.items.size == 2 && !it.refreshing }

        assertEquals(listOf("b", "a"), state.items.map { it.id })
    }

    @Test
    fun aFavoriteThatCouldNotBeSavedLeavesMyListAsItWas() = runTest {
        val server = app.signedIn()
        server.listOf(listOf("a", "b"), 60)
        server.on("DELETE", "/UserFavoriteItems/b", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel(HomeFilter.MyList)
        viewModel.list { !it.loading }

        app.watch.setFavorite("b", favorite = false)

        assertEquals(listOf("a", "b"), viewModel.uiState.value.list.items.map { it.id })
    }

    @Test
    fun anotherPageIgnoresMyListChanges() = runTest {
        val server = app.signedIn()
        server.listOf(listOf("a", "b"), 60)
        server.on("DELETE", "/UserFavoriteItems/b", Reply.json("{}"))
        val viewModel = viewModel(HomeFilter.Movies)
        viewModel.list { !it.loading }

        app.watch.setFavorite("b", favorite = false)

        assertEquals(listOf("a", "b"), viewModel.uiState.value.list.items.map { it.id })
    }

    @Test
    fun aTitleTakenOffWhileThePageReloadsDoesNotComeBack() = runTest {
        val server = app.signedIn()
        server.listOf(listOf("a", "b"), 60)
        server.on("DELETE", "/UserFavoriteItems/b", Reply.json("{}"))
        val viewModel = viewModel(HomeFilter.MyList)
        viewModel.list { !it.loading }
        val reload = server.hold("GET", "/Items")
        viewModel.onChip(ListChipKind.Sort)
        viewModel.onPick("Rating")

        app.watch.setFavorite("b", favorite = false)
        reload.open()
        val state = viewModel.list { !it.refreshing }

        assertEquals(listOf("a"), state.items.map { it.id })
    }

    // The Filters list: every other filter, each opening its own sheet.

    private suspend fun FilteredListViewModel.settled() = uiState.first { !it.list.loading && !it.list.refreshing }

    private suspend fun FilteredListViewModel.openFilters(): ListSheet {
        onChip(ListChipKind.Filters)
        return assertNotNull(uiState.first { it.sheet?.kind == ListSheetKind.Filters && !it.sheet.loading }.sheet)
    }

    @Test
    fun theFiltersListOffersOnlyWhatThisLibraryCanBeNarrowedBy() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Library)
        viewModel.settled()

        val sheet = viewModel.openFilters()

        // One library, so no Library row; no subtitle languages, so none to pick; "und" is not a language.
        assertEquals(
            listOf(
                "Genre", "Release year", "Watched", "Only My List", "From year", "To year", "Rating", "Critic rating", "Age rating", "Studio and network",
                "Tags", "Runtime", "Date added", "Resolution and format", "Audio language",
            ),
            sheet.rows.map { it.label },
        )
        assertTrue(sheet.rows.none { it.selected })
        assertEquals("Any", sheet.rows.first().detail)
    }

    @Test
    fun aShowsPageHasNoPictureOrSoundFiltersAndMyListNoMyListSwitch() = runTest {
        app.signedIn().categorised()
        val shows = viewModel(HomeFilter.Shows)
        shows.settled()
        val myList = viewModel(HomeFilter.MyList)
        myList.settled()

        val showRows = shows.openFilters().rows.map { it.id }
        val myListRows = myList.openFilters().rows.map { it.id }

        assertTrue("Quality" !in showRows && "AudioLanguage" !in showRows)
        assertTrue("Favourites" !in myListRows)
    }

    @Test
    fun aPickFromTheFiltersListNarrowsThePageAndGoesBackToTheList() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Library)
        viewModel.settled()
        viewModel.openFilters()

        viewModel.onPick("Watched")
        val watched = assertNotNull(viewModel.uiState.value.sheet)
        assertEquals(listOf("Any", "Unwatched", "Watched", "In progress"), watched.rows.map { it.label })
        viewModel.onPick("Unwatched")
        val state = viewModel.uiState.first { !it.list.loading && !it.list.refreshing && it.chip(ListChipKind.Filters).active }

        assertEquals(ListSheetKind.Filters, state.sheet?.kind)
        assertEquals("Unwatched", state.sheet?.rows?.first { it.id == "Watched" }?.detail)
        assertEquals("Filters (1)", state.chip(ListChipKind.Filters).label)
        assertEquals("IsUnplayed", app.jellyfin.requestsTo("GET", "/Items").last().query["filters"])
    }

    @Test
    fun onlyMyListSwitchesWhereItIs() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Library)
        viewModel.settled()
        viewModel.openFilters()

        viewModel.onPick("Favourites")
        val state = viewModel.uiState.first { !it.list.refreshing && it.sheet?.rows?.first { row -> row.id == "Favourites" }?.selected == true }

        assertEquals("On", state.sheet?.rows?.first { it.id == "Favourites" }?.detail)
        assertEquals("IsFavorite", app.jellyfin.requestsTo("GET", "/Items").last().query["filters"])
    }

    @Test
    fun filtersCombineAcrossTheChipsAndTheFiltersList() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Library)
        viewModel.settled()
        viewModel.openFromFilters(ListSheetKind.Genre)
        viewModel.onPick("g2")
        viewModel.onDismissSheet()
        viewModel.openFromFilters(ListSheetKind.Year)
        viewModel.onPick("Nineties")
        viewModel.openFilters()
        viewModel.onPick("CommunityRating")
        viewModel.onPick("7")
        viewModel.uiState.first { !it.list.refreshing && it.chip(ListChipKind.Filters).active }
        viewModel.onPick("Watched")
        viewModel.onPick("Unwatched")
        viewModel.settled()

        val query = app.jellyfin.requestsTo("GET", "/Items").last().query
        assertEquals("g2", query["genreIds"])
        assertEquals("IsUnplayed", query["filters"])
        assertEquals("1990,1991,1992,1993,1994,1995,1996,1997,1998,1999", query["years"])
        assertEquals("7", query["minCommunityRating"])
    }

    @Test
    fun anotherOrderStartsTheWayItNaturallyRuns() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Movies)
        viewModel.settled()
        viewModel.onChip(ListChipKind.Sort)
        viewModel.onPick("Name")
        viewModel.uiState.first { !it.list.refreshing && it.chip(ListChipKind.Sort).active }

        viewModel.onPick("Added")
        val state = viewModel.uiState.first { !it.list.refreshing && it.sheet?.rows?.single { row -> row.selected }?.id == "Added" }

        assertEquals("Newest first", state.sheet?.rows?.single { it.selected }?.detail)
        assertEquals("DateCreated" to "Descending", app.jellyfin.requestsTo("GET", "/Items").last().query.let { it["sortBy"] to it["sortOrder"] })
    }

    @Test
    fun theSortSheetTurnsTheOrderRoundAndBack() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Movies)
        viewModel.settled()
        viewModel.onChip(ListChipKind.Sort)

        viewModel.onPick("Name")
        val turned = viewModel.uiState.first { !it.list.refreshing && it.chip(ListChipKind.Sort).active }
        assertEquals("Descending", app.jellyfin.requestsTo("GET", "/Items").last().query["sortOrder"])
        assertEquals("Z to A", turned.sheet?.rows?.single { it.selected }?.detail)
        assertEquals(SortOrder.Descending, turned.sheet?.rows?.single { it.selected }?.direction)

        viewModel.onPick("Name")
        val back = viewModel.uiState.first { !it.list.refreshing && !it.chip(ListChipKind.Sort).active }
        assertEquals("Ascending", app.jellyfin.requestsTo("GET", "/Items").last().query["sortOrder"])
        assertEquals("A to Z", back.sheet?.rows?.single { it.selected }?.detail)
    }

    @Test
    fun aRandomOrderOffersNoDirection() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Movies)
        viewModel.settled()

        viewModel.onChip(ListChipKind.Sort)
        viewModel.onPick("Random")
        val state = viewModel.uiState.first { !it.list.refreshing && it.chip(ListChipKind.Sort).active }

        assertTrue(state.sheet?.rows.orEmpty().none { it.direction != null })

        // Tapping Random again shuffles nothing round: it stays as it is.
        val requests = app.jellyfin.requestsTo("GET", "/Items").size
        viewModel.onPick("Random")
        assertEquals(requests, app.jellyfin.requestsTo("GET", "/Items").size)
    }

    @Test
    fun severalGenresCanBeAskedToMatchAllOfThem() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Library)
        viewModel.settled()
        viewModel.openFromFilters(ListSheetKind.Genre)
        viewModel.onPick("g1")
        viewModel.onPick("g2")
        val both = viewModel.uiState.first { !it.list.refreshing && it.sheet?.rows?.count { row -> row.selected } == 2 }
        assertEquals("g1,g2", app.jellyfin.requestsTo("GET", "/Items").last().query["genreIds"])
        assertEquals(listOf("g1", "g2"), both.sheet?.rows?.filter { it.selected }?.map { it.id })
        viewModel.onDismissSheet()

        val filters = viewModel.openFilters()
        assertEquals("Any of them", filters.rows.first { it.id == "GenreMatch" }.detail)
        viewModel.onPick("GenreMatch")
        viewModel.uiState.first { !it.list.refreshing && it.sheet?.rows?.first { row -> row.id == "GenreMatch" }?.detail == "All of them" }

        val query = app.jellyfin.requestsTo("GET", "/Items").last().query
        assertEquals("g1", query["genreIds"])
        assertEquals("Genres", query["fields"])
    }

    @Test
    fun aYearRangeIsSetFromTheFiltersList() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Library)
        viewModel.settled()
        viewModel.openFilters()

        viewModel.onPick("YearFrom")
        assertEquals(listOf("Any", "2026", "1999"), viewModel.uiState.value.sheet?.rows?.map { it.label })
        viewModel.onPick("1999")
        val from = viewModel.uiState.first { !it.list.refreshing && it.chip(ListChipKind.Filters).active }
        // An open end runs to the newest year there is.
        assertEquals("1999 to 2026", from.detailOf("Year"))

        viewModel.onPick("YearTo")
        viewModel.onPick("1999")
        val one = viewModel.uiState.first { !it.list.refreshing && it.detailOf("Year") == "1999" }
        assertEquals("1999", app.jellyfin.requestsTo("GET", "/Items").last().query["years"])
        assertEquals("1999", one.sheet?.rows?.first { it.id == "YearTo" }?.detail)
    }

    @Test
    fun ageRatingsAreListedYoungestFirstAsAnUpperLimit() = runTest {
        app.signedIn().categorised()
        val viewModel = viewModel(HomeFilter.Library)
        viewModel.settled()
        viewModel.openFilters()

        viewModel.onPick("AgeRating")

        assertEquals(listOf("Any", "PG-13 and under", "R and under"), viewModel.uiState.value.sheet?.rows?.map { it.label })
    }

    @Test
    fun severalLibrariesOfferALibraryFilter() = runTest {
        val server = app.signedIn()
        server.categorised()
        server.filterValues(
            libraries = listOf(
                itemJson("lib-movies", "Movies", type = "CollectionFolder", isFolder = true, collectionType = "movies"),
                itemJson("lib-shows", "Shows", type = "CollectionFolder", isFolder = true, collectionType = "tvshows"),
                itemJson("lib-music", "Music", type = "CollectionFolder", isFolder = true, collectionType = "music"),
            ),
        )
        val viewModel = viewModel(HomeFilter.Library)
        viewModel.settled()
        viewModel.openFilters()

        viewModel.onPick("Library")
        assertEquals(listOf("All libraries", "Movies", "Shows"), viewModel.uiState.value.sheet?.rows?.map { it.label })
        viewModel.onPick("lib-shows")
        viewModel.uiState.first { !it.list.refreshing && it.chip(ListChipKind.Filters).active }

        assertEquals("lib-shows", app.jellyfin.requestsTo("GET", "/Items").last().query["parentId"])
    }

    @Test
    fun aFilterTheServerCannotApplyShowsNoCountUntilTheWholeListIsRead() = runTest {
        val server = app.signedIn()
        server.categorised()
        server.on("GET", "/Items") { request ->
            val start = request.query["startIndex"]!!.toInt()
            val all = (1..300).map { itemJson("t$it", runtimeTicks = 20 * TICKS_PER_MINUTE) }
            Reply.json(itemsJson(*all.drop(start).take(request.query["limit"]!!.toInt()).toTypedArray(), total = all.size, startIndex = start))
        }
        val viewModel = viewModel(HomeFilter.Library)
        viewModel.settled()
        viewModel.openFilters()

        viewModel.onPick("Runtime")
        viewModel.onPick("ShorterThan:30")
        val state = viewModel.uiState.first { !it.list.refreshing && it.chip(ListChipKind.Filters).active }

        assertNull(state.list.total)
        assertEquals(60, state.list.items.size)
        assertTrue(state.list.hasMore)
    }
}
