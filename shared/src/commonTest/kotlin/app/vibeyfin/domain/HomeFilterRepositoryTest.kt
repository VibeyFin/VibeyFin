package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** The server calls behind the Home category buttons: the sheets, the "Recently added" row and Continue watching under a filter. */
class HomeFilterRepositoryTest {
    private val app = TestApp()
    private val now = app.clock.now()

    @AfterTest
    fun tearDown() = runTest { app.close() }

    private fun <T> ApiResult<T>.success(): T = assertIs<ApiResult.Success<T>>(this).value

    private fun ago(days: Int): String = (now - days.days).toString()

    private fun idList(server: String, path: String) = app.jellyfin.requestsTo("GET", path).single().query

    @Test
    fun studiosAreListedByNameWhateverOrderTheServerUses() = runTest {
        app.signedIn().on("GET", "/Studios", Reply.json(itemsJson(itemJson("s2", "Neon", type = "Studio"), itemJson("s1", "a24", type = "Studio"), itemJson("s3", " ", type = "Studio"))))

        val studios = app.library.studios().success()

        assertEquals(listOf(FilterOption("s1", "a24"), FilterOption("s2", "Neon")), studios)
        assertEquals("u1", idList("", "/Studios")["userId"])
    }

    @Test
    fun genresAreAskedForByNameForMoviesAndShows() = runTest {
        app.signedIn().on("GET", "/Genres", Reply.json(itemsJson(itemJson("g1", "Drama", type = "Genre"), itemJson("g2", "Science Fiction", type = "Genre"))))

        val genres = app.library.genres().success()

        assertEquals(listOf("Drama", "Science Fiction"), genres.map { it.name })
        val query = idList("", "/Genres")
        assertEquals("Movie,Series", query["includeItemTypes"])
        assertEquals("SortName", query["sortBy"])
    }

    @Test
    fun showsAndMoviesAskForOnlyThatTypeNewestAddedFirst() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("a"))))

        app.library.recentlyAdded(HomeFilter.Shows, now).success()
        app.library.recentlyAdded(HomeFilter.Movies, now).success()

        val (shows, movies) = app.jellyfin.requestsTo("GET", "/Items").map { it.query }
        assertEquals("Series", shows["includeItemTypes"])
        assertEquals("Movie", movies["includeItemTypes"])
        assertEquals("DateCreated", shows["sortBy"])
        assertEquals("Descending", shows["sortOrder"])
        assertEquals("true", shows["recursive"])
    }

    @Test
    fun aGenreAndAStudioAreTheServersOwnFilters() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("a"))))

        app.library.recentlyAdded(HomeFilter.Genre("g1", "Drama"), now).success()
        app.library.recentlyAdded(HomeFilter.Studio("s1", "A24"), now).success()

        val (genre, studio) = app.jellyfin.requestsTo("GET", "/Items").map { it.query }
        assertEquals("g1", genre["genreIds"])
        assertEquals(null, genre["studioIds"])
        assertEquals("s1", studio["studioIds"])
        assertEquals("Movie,Series", studio["includeItemTypes"])
    }

    @Test
    fun newKeepsOnlyTheLast30DaysAndDropsWhatHasNoDate() = runTest {
        app.signedIn().on(
            "GET", "/Items",
            Reply.json(
                itemsJson(
                    itemJson("today", dateCreated = ago(0)),
                    itemJson("edge", dateCreated = ago(29)),
                    itemJson("old", dateCreated = ago(31)),
                    itemJson("undated"),
                ),
            ),
        )

        val items = app.library.recentlyAdded(HomeFilter.New, now).success()

        assertEquals(listOf("today", "edge"), items.map { it.id })
        // The server can't cut by creation date, so a whole page is fetched to cut here.
        assertEquals("60", idList("", "/Items")["limit"])
    }

    @Test
    fun theRowHoldsAtMostTwentyItems() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(*(1..30).map { itemJson("n$it", dateCreated = ago(1)) }.toTypedArray())))

        assertEquals(20, app.library.recentlyAdded(HomeFilter.New, now).success().size)
    }

    @Test
    fun aRejectedTokenInASheetIsUnauthorized() = runTest {
        app.signedIn().on("GET", "/Genres", Reply.status(HttpStatusCode.Unauthorized))

        assertEquals(ApiError.Unauthorized, assertIs<ApiResult.Failure>(app.library.genres()).error)
    }

    private val movie = itemJson("m1", type = "Movie", dateCreated = ago(2))
    private val oldMovie = itemJson("m2", type = "Movie", dateCreated = ago(90), positionTicks = 1, played = false)
    private val episode = itemJson("e1", type = "Episode", seriesId = "s1", dateCreated = ago(5))
    private val nextEpisode = itemJson("e2", type = "Episode", seriesId = "s2", dateCreated = ago(60))

    private fun watching(resume: List<String>, nextUp: List<String>, server: app.vibeyfin.testing.FakeServer) {
        server.on("GET", "/UserItems/Resume", Reply.json(itemsJson(*resume.toTypedArray())))
        server.on("GET", "/Shows/NextUp", Reply.json(itemsJson(*nextUp.toTypedArray())))
    }

    private suspend fun continueIds(filter: HomeFilter?) = app.watch.continueWatching(filter, now).success().map { it.id }

    @Test
    fun continueWatchingKeepsOnlyTheTypeOfShowsAndMovies() = runTest {
        watching(resume = listOf(movie, episode), nextUp = listOf(nextEpisode), server = app.signedIn())

        assertEquals(listOf("e1", "e2"), continueIds(HomeFilter.Shows))
        assertEquals(listOf("m1"), continueIds(HomeFilter.Movies))
        assertEquals(listOf("m1", "e1", "e2"), continueIds(null))
    }

    @Test
    fun continueWatchingUnderNewKeepsWhatWasAddedInTheLast30Days() = runTest {
        watching(resume = listOf(movie, oldMovie, episode), nextUp = listOf(nextEpisode), server = app.signedIn())

        assertEquals(listOf("m1", "e1"), continueIds(HomeFilter.New))
    }

    @Test
    fun aGenreIsAskedOfTheServerAboutTheShowForAnEpisode() = runTest {
        val server = app.signedIn()
        watching(resume = listOf(movie, episode), nextUp = listOf(nextEpisode), server = server)
        // The server says the movie and the first show are Drama; the second show is not.
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("m1"), itemJson("s1", type = "Series"))))

        assertEquals(listOf("m1", "e1"), continueIds(HomeFilter.Genre("g1", "Drama")))

        val query = app.jellyfin.requestsTo("GET", "/Items").single().query
        assertEquals("m1,s1,s2", query["ids"])
        assertEquals("g1", query["genreIds"])
    }

    @Test
    fun aStudioIsAskedTheSameWay() = runTest {
        val server = app.signedIn()
        watching(resume = listOf(episode), nextUp = emptyList(), server = server)
        server.on("GET", "/Items", Reply.json(itemsJson()))

        assertEquals(emptyList(), continueIds(HomeFilter.Studio("st1", "A24")))

        assertEquals("st1", app.jellyfin.requestsTo("GET", "/Items").single().query["studioIds"])
    }

    @Test
    fun withNothingInProgressAGenreAsksNothing() = runTest {
        watching(resume = emptyList(), nextUp = emptyList(), server = app.signedIn())

        assertEquals(emptyList(), continueIds(HomeFilter.Genre("g1", "Drama")))
        assertEquals(0, app.jellyfin.requestsTo("GET", "/Items").size)
    }

    @Test
    fun aFailedGenreCheckFailsTheRowInsteadOfShowingUnfilteredCards() = runTest {
        val server = app.signedIn()
        watching(resume = listOf(movie), nextUp = emptyList(), server = server)
        server.on("GET", "/Items", Reply.status(HttpStatusCode.Unauthorized))

        val result = app.watch.continueWatching(HomeFilter.Genre("g1", "Drama"), now)

        assertEquals(ApiError.Unauthorized, assertIs<ApiResult.Failure>(result).error)
    }
}
