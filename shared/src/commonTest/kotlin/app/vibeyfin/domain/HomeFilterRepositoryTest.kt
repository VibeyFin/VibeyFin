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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/** The server calls behind the Home category buttons: the dropdown lists and the category pages. */
class HomeFilterRepositoryTest {
    private val app = TestApp()
    private val now = app.clock.now()

    @AfterTest
    fun tearDown() = runTest { app.close() }

    private fun <T> ApiResult<T>.success(): T = assertIs<ApiResult.Success<T>>(this).value

    private fun ago(days: Int): String = (now - days.days).toString()

    private fun query(path: String) = app.jellyfin.requestsTo("GET", path).single().query

    @Test
    fun studiosAreListedByNameWhateverOrderTheServerUses() = runTest {
        app.signedIn().on("GET", "/Studios", Reply.json(itemsJson(itemJson("s2", "Neon", type = "Studio"), itemJson("s1", "a24", type = "Studio"), itemJson("s3", " ", type = "Studio"))))

        val studios = app.library.studios().success()

        assertEquals(listOf(FilterOption("s1", "a24"), FilterOption("s2", "Neon")), studios)
        assertEquals("u1", query("/Studios")["userId"])
    }

    @Test
    fun genresAreAskedForByNameForMoviesAndShows() = runTest {
        app.signedIn().on("GET", "/Genres", Reply.json(itemsJson(itemJson("g1", "Drama", type = "Genre"), itemJson("g2", "Science Fiction", type = "Genre"))))

        val genres = app.library.genres().success()

        assertEquals(listOf("Drama", "Science Fiction"), genres.map { it.name })
        assertEquals("Movie,Series", query("/Genres")["includeItemTypes"])
        assertEquals("SortName", query("/Genres")["sortBy"])
    }

    @Test
    fun aRejectedTokenInADropdownIsUnauthorized() = runTest {
        app.signedIn().on("GET", "/Genres", Reply.status(HttpStatusCode.Unauthorized))

        assertEquals(ApiError.Unauthorized, assertIs<ApiResult.Failure>(app.library.genres()).error)
    }

    @Test
    fun showsAndMoviesAskForOnlyThatTypeByName() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a"))))

        app.library.filteredPage(HomeFilter.Shows, now).success()
        app.library.filteredPage(HomeFilter.Movies, now).success()

        val (shows, movies) = app.jellyfin.requestsTo("GET", "/Items").map { it.query }
        assertEquals("Series", shows["includeItemTypes"])
        assertEquals("Movie", movies["includeItemTypes"])
        assertEquals("SortName", shows["sortBy"])
        assertEquals("Ascending", shows["sortOrder"])
        assertEquals("true", shows["recursive"])
        assertEquals("0", shows["startIndex"])
    }

    @Test
    fun aGenreAndAStudioAreTheServersOwnFilters() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a"))))

        app.library.filteredPage(HomeFilter.Genre("g1", "Drama"), now).success()
        app.library.filteredPage(HomeFilter.Studio("s1", "A24"), now).success()

        val (genre, studio) = app.jellyfin.requestsTo("GET", "/Items").map { it.query }
        assertEquals("g1", genre["genreIds"])
        assertEquals(null, genre["studioIds"])
        assertEquals("s1", studio["studioIds"])
        assertEquals("Movie,Series", studio["includeItemTypes"])
    }

    @Test
    fun aPageCarriesTheServersTotalAndWhereItStarts() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a"), itemJson("b"), total = 5, startIndex = 2)))

        val page = app.library.filteredPage(HomeFilter.Movies, now, startIndex = 2, limit = 2).success()

        assertEquals(5, page.total)
        assertTrue(page.hasMore)
        assertEquals("2", query("/Items")["startIndex"])
        assertEquals("2", query("/Items")["limit"])
    }

    @Test
    fun newIsNewestFirstAndEndsAtTheFirstItemOlderThan30Days() = runTest {
        app.signedIn().on(
            "GET", "/Items",
            Reply.json(
                itemsJson(
                    itemJson("today", dateCreated = ago(0)),
                    itemJson("edge", dateCreated = ago(29)),
                    itemJson("old", dateCreated = ago(31)),
                    itemJson("older", dateCreated = ago(90)),
                    total = 40,
                ),
            ),
        )

        val page = app.library.filteredPage(HomeFilter.New, now).success()

        assertEquals(listOf("today", "edge"), page.items.map { it.id })
        // Newest first, so the list ends here whatever the server holds beyond it.
        assertEquals(2, page.total)
        assertEquals(false, page.hasMore)
        assertEquals("DateCreated", query("/Items")["sortBy"])
        assertEquals("Descending", query("/Items")["sortOrder"])
    }

    @Test
    fun newKeepsGoingWhileEveryItemIsRecent() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a", dateCreated = ago(1)), itemJson("b", dateCreated = ago(2)), total = 9)))

        val page = app.library.filteredPage(HomeFilter.New, now, limit = 2).success()

        assertEquals(9, page.total)
        assertTrue(page.hasMore)
    }

    @Test
    fun newDropsAnItemTheServerGaveNoDateFor() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("undated"))))

        assertEquals(emptyList(), app.library.filteredPage(HomeFilter.New, now).success().items)
    }
}
