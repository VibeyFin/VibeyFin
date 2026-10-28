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
import app.vibeyfin.ui.common.LoadMessages
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
import kotlinx.coroutines.launch
import app.vibeyfin.ui.home.HomeUiState
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

    private fun viewModel() = viewModels.track(HomeViewModel(app.connection, app.library, app.watch, app.watchChanges, app.clock, app.profileSettings, app.searchHistory, app.pins, app.auth, app.downloadRepository))

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
    fun rowsAreRecentlyAddedPerVideoLibraryThenEveryGenreBiggestFirst() = runTest {
        server()

        val rows = viewModel().rowsLoaded(7)

        // Music holds no video, so it has no row; every genre has one, Drama (9) first, then by how many titles each holds.
        assertEquals(
            listOf("Recently added in Movies", "Recently added in Shows", "Drama", "Thriller", "Action", "Comedy", "Science Fiction"),
            rows.map { it.title },
        )
        assertEquals(listOf("New in lib-m"), rows[0].items.map { it.title })
        assertEquals(listOf("Top g-drama"), rows[2].items.map { it.title })
    }

    @Test
    fun myListIsTheFirstRowWhenSomethingIsOnIt() = runTest {
        server(myList = Reply.json(itemsJson(itemJson("fav-1", "Heat"), itemJson("fav-2", "Ronin"))))

        val rows = viewModel().rowsLoaded(8)

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

        val rows = viewModel().rowsLoaded(7)

        assertTrue(rows.none { it.id == "my-list" })
    }

    @Test
    fun aFavouriteChangedElsewhereUpdatesTheRow() = runTest {
        val server = server()
        val viewModel = viewModel()
        viewModel.rowsLoaded(7)

        server.on("GET", "/Items") { request ->
            if (request.query["filters"] == "IsFavorite") Reply.json(itemsJson(itemJson("fav-1", "Heat"))) else Reply.json(itemsJson(itemJson("x", "X")))
        }
        app.watchChanges.changed()

        val rows = viewModel.uiState.first { state -> state.rows.any { it.id == "my-list" && it.items.isNotEmpty() } }.rows
        assertEquals("My List", rows.first().title)
    }

    @Test
    fun theProfileCanLimitTheGenreRowsToTheBiggestFew() = runTest {
        server()
        app.profileSettings.setHomeGenreRows(3)

        val rows = viewModel().rowsLoaded(5)

        assertEquals(listOf("Drama", "Thriller", "Action"), rows.filter { it.id.startsWith("genre:") }.map { it.title })
    }

    @Test
    fun theProfileCanTurnTheGenreRowsOff() = runTest {
        server()
        app.profileSettings.setHomeGenreRows(-1)

        val rows = viewModel().rowsLoaded(2)

        assertEquals(listOf("Recently added in Movies", "Recently added in Shows"), rows.map { it.title })
    }

    @Test
    fun changingTheSettingWhileHomeIsOpenAddsOrDropsTheGenreRows() = runTest {
        server()
        val viewModel = viewModel()
        viewModel.rowsLoaded(7)

        app.profileSettings.setHomeGenreRows(3)
        val fewer = viewModel.uiState.first { state -> state.rows.count { it.id.startsWith("genre:") } == 3 && state.rows.none { it.loading } }
        assertEquals(listOf("Drama", "Thriller", "Action"), fewer.rows.filter { it.id.startsWith("genre:") }.map { it.title })

        app.profileSettings.setHomeGenreRows(0)
        viewModel.uiState.first { state -> state.rows.count { it.id.startsWith("genre:") } == 5 && state.rows.none { it.loading } }
    }

    @Test
    fun homeFeaturesOneRecentlyAddedItemThatHasABackdropAndALogo() = runTest {
        server(
            genreItems = { genre ->
                // The "New" request has no genre; the genre rows carry plain items.
                if (genre == null) Reply.json(itemsJson(itemJson("plain", "Plain"), itemJson("star", "Star", backdropTag = "b", logoTag = "l"))) else Reply.json(itemsJson(itemJson("top-$genre", "Top $genre")))
            },
        )

        val hero = viewModel().uiState.first { it.heroes.isNotEmpty() }.heroes.firstOrNull()

        assertEquals("star", hero?.id)
    }

    private suspend fun heroServer() = server(
        genreItems = { genre ->
            if (genre == null) Reply.json(itemsJson(itemJson("star", "Star", backdropTag = "b", logoTag = "l"))) else Reply.json(itemsJson(itemJson("top-$genre", "Top $genre")))
        },
    )

    @Test
    fun theHeroCandidatesAreAskedForWithTheirGenres() = runTest {
        heroServer()

        viewModel().uiState.first { it.heroes.isNotEmpty() }

        assertTrue(app.jellyfin.requestsTo("GET", "/Items").any { it.query["fields"] == "Genres" })
    }

    @Test
    fun myListOnTheHeroIsShownAtOnceAndTakenBackWithAMessageWhenItCantBeSaved() = runTest {
        val server = heroServer()
        server.on("POST", "/UserFavoriteItems/star", Reply.status(HttpStatusCode.ServiceUnavailable))
        val inFlight = server.hold("POST", "/UserFavoriteItems/star")
        val viewModel = viewModel()
        viewModel.uiState.first { it.heroes.isNotEmpty() }

        viewModel.onHeroMyList("star")
        // The write has not been answered: the heart is already filled.
        assertTrue(viewModel.uiState.value.heroes.single().isFavorite == true)
        inFlight.open()
        val failed = viewModel.uiState.first { it.actionMessage != null }

        assertEquals(false, failed.heroes.single().isFavorite)
        assertEquals(LoadMessages.ADD_TO_MY_LIST_FAILED, failed.actionMessage)
    }

    @Test
    fun myListChangedElsewhereFillsOrEmptiesTheHerosHeart() = runTest {
        val server = heroServer()
        val viewModel = viewModel()
        assertEquals(false, viewModel.uiState.first { it.heroes.isNotEmpty() }.heroes.single().isFavorite)

        // Put on My List from the poster menu (a long press on the hero opens it): Home reads My List again.
        server.on("GET", "/Items") { request ->
            if (request.query["filters"] == "IsFavorite") Reply.json(itemsJson(itemJson("star", "Star", backdropTag = "b", logoTag = "l"))) else Reply.json(itemsJson(itemJson("x", "X")))
        }
        app.watchChanges.changed()
        assertTrue(viewModel.uiState.first { it.heroes.single().isFavorite }.heroes.single().isFavorite)

        // Taken off again; the row holds the whole (now empty) list, so the heart empties.
        server.on("GET", "/Items") { request ->
            if (request.query["filters"] == "IsFavorite") Reply.json(itemsJson()) else Reply.json(itemsJson(itemJson("x", "X")))
        }
        app.watchChanges.changed()
        assertFalse(viewModel.uiState.first { !it.heroes.single().isFavorite }.heroes.single().isFavorite)
    }

    private suspend fun showHeroServer(): app.vibeyfin.testing.FakeServer = server(
        genreItems = { genre ->
            if (genre == null) {
                Reply.json(itemsJson(itemJson("show", "Show", type = "Series", isFolder = true, backdropTag = "b", logoTag = "l")))
            } else {
                Reply.json(itemsJson(itemJson("top-$genre", "Top $genre")))
            }
        },
    )

    @Test
    fun playOnAMovieHeroPlaysIt() = runTest {
        heroServer()
        val viewModel = viewModel()
        viewModel.uiState.first { it.heroes.isNotEmpty() }

        viewModel.onHeroPlay("star")

        assertEquals(HeroNav.Play("star"), viewModel.uiState.first { it.heroNav != null }.heroNav)
        viewModel.onHeroNavHandled()
        assertNull(viewModel.uiState.first { it.heroNav == null }.heroNav)
    }

    @Test
    fun playOnAShowHeroPlaysTheEpisodeItIsUpTo() = runTest {
        val server = showHeroServer()
        server.on("GET", "/Shows/NextUp", Reply.json(itemsJson(itemJson("e7", "Seven", type = "Episode", seasonNumber = 2, episodeNumber = 3))))
        val viewModel = viewModel()
        viewModel.uiState.first { it.heroes.isNotEmpty() }

        viewModel.onHeroPlay("show")

        assertEquals(HeroNav.Play("e7"), viewModel.uiState.first { it.heroNav != null }.heroNav)
    }

    @Test
    fun playOnAShowNeverStartedPlaysItsFirstEpisodeNotSpecials() = runTest {
        val server = showHeroServer()
        server.on("GET", "/Shows/show/Seasons", Reply.json(itemsJson(itemJson("s0", "Specials", type = "Season", isFolder = true, episodeNumber = 0), itemJson("s1", "Season 1", type = "Season", isFolder = true, episodeNumber = 1))))
        server.on("GET", "/Shows/show/Episodes") { request ->
            if (request.query["seasonId"] == "s1") Reply.json(itemsJson(itemJson("e11", "One", type = "Episode"), itemJson("e12", "Two", type = "Episode"))) else Reply.json(itemsJson(itemJson("sp", "Special", type = "Episode")))
        }
        val viewModel = viewModel()
        viewModel.uiState.first { it.heroes.isNotEmpty() }

        viewModel.onHeroPlay("show")

        assertEquals(HeroNav.Play("e11"), viewModel.uiState.first { it.heroNav != null }.heroNav)
    }

    @Test
    fun playOnAShowWithNoEpisodeFoundOpensItsPage() = runTest {
        val server = showHeroServer()
        server.on("GET", "/Shows/show/Seasons", Reply.json(itemsJson()))
        val viewModel = viewModel()
        viewModel.uiState.first { it.heroes.isNotEmpty() }

        viewModel.onHeroPlay("show")

        assertEquals(HeroNav.Details("show"), viewModel.uiState.first { it.heroNav != null }.heroNav)
    }

    @Test
    fun theHeroShowsAGreyCardWhileItIsPickedAndNotWhenItIsOffOrFound() = runTest {
        heroServer()
        val viewModel = viewModel()

        // Once picked there is a hero and no grey card left (before that the grey card held its place).
        val picked = viewModel.uiState.first { it.heroes.isNotEmpty() }

        assertFalse(picked.heroLoading)
    }

    @Test
    fun nothingToFeatureLeavesNoGreyCardBehind() = runTest {
        server()
        val viewModel = viewModel()

        viewModel.rowsLoaded(7)
        val decided = viewModel.uiState.first { !it.heroLoading }

        assertTrue(decided.heroes.isEmpty())
    }

    @Test
    fun aProfileWithTheHeroOffNeverShowsAGreyCard() = runTest {
        heroServer()
        app.profileSettings.setHomeHeroItems(0)
        val viewModel = viewModel()
        val states = mutableListOf<HomeUiState>()
        backgroundScope.launch { viewModel.uiState.collect { states += it } }

        viewModel.rowsLoaded(7)

        assertTrue(states.none { it.heroLoading })
    }

    @Test
    fun aProfileWithTheHeroTurnedOffGetsNoneAndAsksForNoCandidates() = runTest {
        heroServer()
        app.profileSettings.setHomeHeroItems(0)
        val viewModel = viewModel()

        viewModel.rowsLoaded(7)

        assertTrue(viewModel.uiState.value.heroes.isEmpty())
        // Only the genre rows and My List asked for /Items: nobody asked for the hero's recently added candidates.
        assertTrue(app.jellyfin.requestsTo("GET", "/Items").none { it.query["sortBy"] == "DateCreated" && it.query["filters"] == null })
    }

    @Test
    fun turningTheHeroOnOrOffWhileHomeIsOpenShowsOrRemovesIt() = runTest {
        heroServer()
        val viewModel = viewModel()
        viewModel.uiState.first { it.heroes.isNotEmpty() }

        app.profileSettings.setHomeHeroItems(0)
        assertNull(viewModel.uiState.first { it.heroes.isEmpty() }.heroes.firstOrNull())

        app.profileSettings.setHomeHeroItems(1)
        assertEquals("star", viewModel.uiState.first { it.heroes.isNotEmpty() }.heroes.firstOrNull()?.id)
    }

    private suspend fun threeHeroServer(myList: Reply = Reply.json(itemsJson())): app.vibeyfin.testing.FakeServer = server(
        myList = myList,
        genreItems = { genre ->
            if (genre == null) {
                Reply.json(
                    itemsJson(
                        itemJson("a", "A", backdropTag = "b", logoTag = "l"),
                        itemJson("b", "B", backdropTag = "b", logoTag = "l"),
                        itemJson("c", "C", backdropTag = "b", logoTag = "l"),
                        itemJson("plain", "Plain"),
                    ),
                )
            } else {
                Reply.json(itemsJson(itemJson("top-$genre", "Top $genre")))
            }
        },
    )

    @Test
    fun aProfileThatFeaturesSeveralGetsThatManyDifferentOnes() = runTest {
        threeHeroServer()
        app.profileSettings.setHomeHeroItems(2)
        val viewModel = viewModel()

        val state = viewModel.uiState.first { it.heroes.isNotEmpty() }

        assertEquals(2, state.heroSlots)
        assertEquals(2, state.heroes.map { it.id }.toSet().size)
        assertTrue(state.heroes.all { it.id in setOf("a", "b", "c") })
    }

    @Test
    fun askingForMoreThanQualifyShowsOnlyThoseThatDo() = runTest {
        threeHeroServer()
        app.profileSettings.setHomeHeroItems(5)
        val viewModel = viewModel()

        val state = viewModel.uiState.first { it.heroes.isNotEmpty() }

        assertEquals(5, state.heroSlots)
        assertEquals(setOf("a", "b", "c"), state.heroes.map { it.id }.toSet())
    }

    @Test
    fun theGreyCardKnowsHowManyAreComingFromTheFirstState() = runTest {
        threeHeroServer()
        app.profileSettings.setHomeHeroItems(3)
        val viewModel = viewModel()
        val states = mutableListOf<HomeUiState>()
        backgroundScope.launch { viewModel.uiState.collect { states += it } }

        viewModel.uiState.first { it.heroes.isNotEmpty() }

        assertTrue(states.filter { it.heroLoading }.all { it.heroSlots == 3 })
    }

    @Test
    fun playAndMyListActOnTheFeaturedItemTheyAreGiven() = runTest {
        // My List as the server has it once "b" is put on it, read again after the write.
        val server = threeHeroServer(myList = Reply.json(itemsJson(itemJson("b", "B", backdropTag = "b", logoTag = "l"))))
        server.on("POST", "/UserFavoriteItems/b", Reply.json("{}"))
        app.profileSettings.setHomeHeroItems(3)
        val viewModel = viewModel()
        viewModel.uiState.first { it.heroes.size == 3 }

        viewModel.onHeroPlay("b")
        assertEquals(HeroNav.Play("b"), viewModel.uiState.first { it.heroNav != null }.heroNav)
        viewModel.onHeroMyList("b")
        val after = viewModel.uiState.first { state -> state.heroes.any { it.isFavorite } }

        assertEquals(listOf("b"), after.heroes.filter { it.isFavorite }.map { it.id })
        // Something no longer featured does nothing.
        viewModel.onHeroPlay("gone")
        viewModel.onHeroNavHandled()
        assertNull(viewModel.uiState.first { it.heroNav == null }.heroNav)
    }

    @Test
    fun aNewNumberInSettingsPicksAgainForIt() = runTest {
        threeHeroServer()
        val viewModel = viewModel()
        assertEquals(1, viewModel.uiState.first { it.heroes.isNotEmpty() }.heroes.size)

        app.profileSettings.setHomeHeroItems(3)

        assertEquals(3, viewModel.uiState.first { it.heroes.size == 3 }.heroSlots)
    }

    @Test
    fun withNothingToFeatureHomeHasNoHero() = runTest {
        server()
        val viewModel = viewModel()

        viewModel.rowsLoaded(7)

        assertTrue(viewModel.uiState.value.heroes.isEmpty())
    }

    @Test
    fun aRowsSeeAllOpensItsLibraryOrItsGenre() = runTest {
        server()

        val rows = viewModel().rowsLoaded(7)

        val library = assertIs<SeeAll.Library>(rows[0].seeAll)
        assertEquals("lib-m", library.library.id)
        val genre = assertIs<SeeAll.Genre>(rows[2].seeAll)
        assertEquals(HomeFilter.Genre("g-drama", "Drama"), genre.filter)
    }

    @Test
    fun latestAsksForTwentyOfTheLibraryAndAGenreRowForTwentyByRating() = runTest {
        val server = server()

        viewModel().rowsLoaded(7)

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

        val rows = viewModel().rowsLoaded(6)

        assertEquals(listOf("Recently added in Movies", "Drama", "Thriller", "Action", "Comedy", "Science Fiction"), rows.map { it.title })
    }

    @Test
    fun aRowThatFailsShowsWhyWhileTheOthersLoad() = runTest {
        server(latest = { library -> if (library == "lib-m") Reply.status(HttpStatusCode.ServiceUnavailable) else Reply.json("[${itemJson("n", "New")}]") })

        val rows = viewModel().rowsLoaded(7)

        val failed = rows.first { it.title == "Recently added in Movies" }
        assertNotNull(failed.message)
        assertTrue(failed.items.isEmpty())
        assertTrue(rows.filter { it.message == null }.all { it.items.isNotEmpty() })
    }

    @Test
    fun tryAgainReloadsOnlyThatRow() = runTest {
        val server = server(latest = { library -> if (library == "lib-m") Reply.status(HttpStatusCode.ServiceUnavailable) else Reply.json("[${itemJson("n", "New")}]") })
        val viewModel = viewModel()
        viewModel.rowsLoaded(7)
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
    fun eightVideoLibrariesGetSixRowsAndEveryGenreStillHasOneWithNoCap() = runTest {
        val many = (1..8).map { itemJson("lib-$it", "Library $it", type = "CollectionFolder", isFolder = true, collectionType = "movies") }
        server(libs = many)

        val rows = viewModel().rowsLoaded(11)

        assertEquals(6, rows.count { it.id.startsWith("latest:") })
        assertEquals(5, rows.count { it.id.startsWith("genre:") })
    }

    @Test
    fun aMixedLibraryWithNoTypeGetsARowToo() = runTest {
        server(libs = listOf(itemJson("lib-mix", "Everything", type = "CollectionFolder", isFolder = true)))

        val rows = viewModel().rowsLoaded(6)

        assertEquals("Recently added in Everything", rows.first().title)
    }

    @Test
    fun aRefreshKeepsTheRowsOnScreenAndAFailingRefreshKeepsTheirItems() = runTest {
        val server = server()
        val viewModel = viewModel()
        viewModel.rowsLoaded(7)

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

}
