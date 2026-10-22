package app.vibeyfin.ui.home

import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import app.vibeyfin.ui.common.Strings
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Home's rows between Continue watching and My media: Recently added per library, then a few genres. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeRowsViewModelTest {
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

    private val libraries = listOf(
        itemJson("lib-m", "Movies", type = "CollectionFolder", isFolder = true, collectionType = "movies"),
        itemJson("lib-s", "Shows", type = "CollectionFolder", isFolder = true, collectionType = "tvshows"),
        itemJson("lib-x", "Music", type = "CollectionFolder", isFolder = true, collectionType = "music"),
    )

    private val genres = itemsJson(
        itemJson("g-drama", "Drama", type = "Genre", movieCount = 8, seriesCount = 1),
        itemJson("g-action", "Action", type = "Genre", movieCount = 6),
        itemJson("g-comedy", "Comedy", type = "Genre", movieCount = 5),
        itemJson("g-thriller", "Thriller", type = "Genre", movieCount = 7),
        itemJson("g-scifi", "Science Fiction", type = "Genre", movieCount = 1),
    )

    /** A signed-in server whose Latest answers by library and whose genre rows answer by genre. */
    private suspend fun server(
        libs: List<String> = libraries,
        latest: (String) -> Reply = { library -> Reply.json("[${itemJson("new-$library", "New in $library")}]") },
        genreItems: (String?) -> Reply = { genre -> Reply.json(itemsJson(itemJson("top-$genre", "Top $genre"))) },
        /** What the user has on My List; nothing by default, which leaves the row out. */
        myList: Reply = Reply.json(itemsJson()),
    ): FakeServer {
        val server = app.signedIn()
        server.on("GET", "/UserViews", Reply.json(itemsJson(*libs.toTypedArray())))
        server.on("GET", "/UserItems/Resume", Reply.json(itemsJson()))
        server.on("GET", "/Shows/NextUp", Reply.json(itemsJson()))
        server.on("GET", "/Genres", Reply.json(genres))
        server.on("GET", "/Items/Latest") { request -> latest(request.query["parentId"].orEmpty()) }
        server.on("GET", "/Items") { request ->
            if (request.query["filters"] == "IsFavorite") myList else genreItems(request.query["genreIds"])
        }
        return server
    }

    private suspend fun HomeViewModel.rowsLoaded(count: Int) =
        uiState.first { it.rows.size == count && it.rows.none { row -> row.loading } }.rows

    @Test
    fun rowsAreRecentlyAddedPerVideoLibraryThenTheThreeBiggestGenres() = runTest {
        server()

        val rows = viewModel().rowsLoaded(5)

        // Music holds no video, so it has no row; Drama (9), Thriller (7) and Action (6) are the biggest genres.
        assertEquals(
            listOf("Recently added in Movies", "Recently added in Shows", "Drama", "Thriller", "Action"),
            rows.map { it.title },
        )
        assertEquals(listOf("New in lib-m"), rows[0].items.map { it.title })
        assertEquals(listOf("Top g-drama"), rows[2].items.map { it.title })
    }

    @Test
    fun myListIsTheFirstRowWhenSomethingIsOnIt() = runTest {
        server(myList = Reply.json(itemsJson(itemJson("fav-1", "Heat"), itemJson("fav-2", "Ronin"))))

        val rows = viewModel().rowsLoaded(6)

        assertEquals("My List", rows.first().title)
        assertEquals(listOf("fav-1", "fav-2"), rows.first().items.map { it.id })
        assertEquals(SeeAll.MyList, rows.first().seeAll)
        val request = app.jellyfin.requestsTo("GET", "/Items").map { it.query }.first { it["filters"] == "IsFavorite" }
        assertEquals("20", request["limit"])
        assertEquals("DateCreated", request["sortBy"])
        assertEquals("Movie,Series", request["includeItemTypes"])
    }

    @Test
    fun myListIsLeftOutWhileItIsEmpty() = runTest {
        server()

        val rows = viewModel().rowsLoaded(5)

        assertTrue(rows.none { it.id == "my-list" })
    }

    @Test
    fun aFavouriteChangedElsewhereUpdatesTheRow() = runTest {
        val server = server()
        val viewModel = viewModel()
        viewModel.rowsLoaded(5)

        server.on("GET", "/Items") { request ->
            if (request.query["filters"] == "IsFavorite") Reply.json(itemsJson(itemJson("fav-1", "Heat"))) else Reply.json(itemsJson(itemJson("x", "X")))
        }
        app.watchChanges.changed()

        val rows = viewModel.uiState.first { state -> state.rows.any { it.id == "my-list" && it.items.isNotEmpty() } }.rows
        assertEquals("My List", rows.first().title)
    }

    @Test
    fun aRowsSeeAllOpensItsLibraryOrItsGenre() = runTest {
        server()

        val rows = viewModel().rowsLoaded(5)

        val library = assertIs<SeeAll.Library>(rows[0].seeAll)
        assertEquals("lib-m", library.library.id)
        val genre = assertIs<SeeAll.Genre>(rows[2].seeAll)
        assertEquals(HomeFilter.Genre("g-drama", "Drama"), genre.filter)
    }

    @Test
    fun latestAsksForTwentyOfTheLibraryAndAGenreRowForTwentyByRating() = runTest {
        val server = server()

        viewModel().rowsLoaded(5)

        val latest = app.jellyfin.requestsTo("GET", "/Items/Latest").map { it.query }
        // The libraries are asked at the same time, so in no fixed order.
        assertEquals(listOf("lib-m", "lib-s"), latest.map { it["parentId"] }.sortedBy { it })
        assertEquals("20", latest.first()["limit"])
        val genre = app.jellyfin.requestsTo("GET", "/Items").map { it.query }.first { it["genreIds"] == "g-drama" }
        assertEquals("20", genre["limit"])
        assertEquals("CommunityRating,SortName", genre["sortBy"])
        assertTrue(server.address.isNotEmpty())
    }

    @Test
    fun aRowThatComesBackEmptyIsNotShown() = runTest {
        server(latest = { library -> if (library == "lib-s") Reply.json("[]") else Reply.json("[${itemJson("n", "New")}]") })

        val rows = viewModel().rowsLoaded(4)

        assertEquals(listOf("Recently added in Movies", "Drama", "Thriller", "Action"), rows.map { it.title })
    }

    @Test
    fun aRowThatFailsShowsWhyWhileTheOthersLoad() = runTest {
        server(latest = { library -> if (library == "lib-m") Reply.status(HttpStatusCode.ServiceUnavailable) else Reply.json("[${itemJson("n", "New")}]") })

        val rows = viewModel().rowsLoaded(5)

        val failed = rows.first { it.title == "Recently added in Movies" }
        assertNotNull(failed.message)
        assertTrue(failed.items.isEmpty())
        assertTrue(rows.filter { it.message == null }.all { it.items.isNotEmpty() })
    }

    @Test
    fun tryAgainReloadsOnlyThatRow() = runTest {
        val server = server(latest = { library -> if (library == "lib-m") Reply.status(HttpStatusCode.ServiceUnavailable) else Reply.json("[${itemJson("n", "New")}]") })
        val viewModel = viewModel()
        viewModel.rowsLoaded(5)
        val latestBefore = app.jellyfin.requestsTo("GET", "/Items/Latest").size

        server.on("GET", "/Items/Latest", Reply.json("[${itemJson("fixed", "Fixed")}]"))
        viewModel.onRetryRow("latest:lib-m")
        val rows = viewModel.uiState.first { state -> state.rows.firstOrNull { it.id == "latest:lib-m" }?.items?.isNotEmpty() == true }.rows

        assertNull(rows.first { it.id == "latest:lib-m" }.message)
        // Only the one library was asked again.
        assertEquals(latestBefore + 1, app.jellyfin.requestsTo("GET", "/Items/Latest").size)
    }

    @Test
    fun aServerWithNoGenresStillShowsTheLibraryRows() = runTest {
        val server = server()
        server.on("GET", "/Genres", Reply.json(itemsJson()))

        val rows = viewModel().rowsLoaded(2)

        assertEquals(listOf("Recently added in Movies", "Recently added in Shows"), rows.map { it.title })
    }

    @Test
    fun genresThatCannotBeListedLeaveNoRowAndNoError() = runTest {
        val server = server()
        server.on("GET", "/Genres", Reply.status(HttpStatusCode.ServiceUnavailable))

        val rows = viewModel().rowsLoaded(2)

        assertTrue(rows.none { it.message != null })
        assertEquals(2, rows.size)
    }

    @Test
    fun eightVideoLibrariesGetSixRowsAndNoMoreThanTenRowsInAll() = runTest {
        val many = (1..8).map { itemJson("lib-$it", "Library $it", type = "CollectionFolder", isFolder = true, collectionType = "movies") }
        server(libs = many)

        val rows = viewModel().rowsLoaded(9)

        assertEquals(6, rows.count { it.id.startsWith("latest:") })
        assertEquals(3, rows.count { it.id.startsWith("genre:") })
        assertTrue(rows.size <= 10)
    }

    @Test
    fun aMixedLibraryWithNoTypeGetsARowToo() = runTest {
        server(libs = listOf(itemJson("lib-mix", "Everything", type = "CollectionFolder", isFolder = true)))

        val rows = viewModel().rowsLoaded(4)

        assertEquals("Recently added in Everything", rows.first().title)
    }

    @Test
    fun myMediaIsStillEveryLibraryIncludingMusic() = runTest {
        server()

        val state = viewModel().uiState.first { it.libraries.isNotEmpty() }

        assertEquals(listOf("Movies", "Shows", "Music"), state.libraries.map { it.title })
        assertEquals("My media", Strings.libraries)
    }

    @Test
    fun aRefreshKeepsTheRowsOnScreenAndAFailingRefreshKeepsTheirItems() = runTest {
        val server = server()
        val viewModel = viewModel()
        viewModel.rowsLoaded(5)

        server.on("GET", "/Items/Latest", Reply.status(HttpStatusCode.ServiceUnavailable))
        viewModel.onRefresh()
        val state = viewModel.uiState.first { !it.refreshing }

        val movies = state.rows.first { it.id == "latest:lib-m" }
        assertEquals(listOf("New in lib-m"), movies.items.map { it.title })
        assertNull(movies.message)
        assertFalse(movies.loading)
    }

    // The rows come in from top to bottom.

    private fun row(id: String, loading: Boolean = false, items: Int = 1) =
        MediaRow(id, id, items = List(items) { n -> MediaItem(id = "$id-$n", title = "t", kind = ItemKind.Movie, isFolder = false, year = null, runtimeMinutes = null, genres = emptyList(), overview = null, rating = null, ageRating = null, seriesName = null, seasonNumber = null, episodeNumber = null, posterAspectRatio = 2f / 3f, posterUrl = null, backdropUrl = null) }, loading = loading)

    @Test
    fun aRowThatFinishedEarlyStaysGreyUntilTheRowsAboveHaveFinished() {
        val rows = listOf(row("a", loading = true, items = 0), row("b"), row("c", loading = true, items = 0), row("d"))

        val shown = revealInOrder(rows, aboveLoading = false)

        assertEquals(listOf(true, true, true, true), shown.map { it.loading })
        assertTrue(shown.all { it.items.isEmpty() })
    }

    @Test
    fun rowsAreShownAsSoonAsEveryRowAboveThemIsDone() {
        val rows = listOf(row("a"), row("b"), row("c", loading = true, items = 0), row("d"))

        val shown = revealInOrder(rows, aboveLoading = false)

        assertEquals(listOf(false, false, true, true), shown.map { it.loading })
        assertEquals(1, shown[0].items.size)
        assertEquals(1, shown[1].items.size)
    }

    @Test
    fun nothingIsShownUntilContinueWatchingHasAnswered() {
        val shown = revealInOrder(listOf(row("a"), row("b")), aboveLoading = true)

        assertEquals(listOf(true, true), shown.map { it.loading })
    }

    @Test
    fun aRowThatTurnsOutEmptyLeavesOnlyOnceItIsReached() {
        val rows = listOf(row("a", loading = true, items = 0), row("empty", items = 0), row("b"))

        // While "a" loads, the empty row is a grey row like the rest, so nothing below it moves yet.
        val waiting = revealInOrder(rows, aboveLoading = false).filter { it.isVisible }
        assertEquals(listOf("a", "empty", "b"), waiting.map { it.id })

        // Once "a" is done the empty row is reached and goes.
        val done = revealInOrder(listOf(row("a"), row("empty", items = 0), row("b")), aboveLoading = false).filter { it.isVisible }
        assertEquals(listOf("a", "b"), done.map { it.id })
    }

    @Test
    fun myMediaComesInLastAfterContinueWatchingAndEveryRow() = runTest {
        server()
        val viewModel = viewModel()

        val done = viewModel.uiState.first { it.rows.size == 5 && it.rows.none { row -> row.loading } && !it.loadingMyMedia }

        assertFalse(done.loadingMyMedia)
        assertEquals(3, done.libraries.size)
    }
}
