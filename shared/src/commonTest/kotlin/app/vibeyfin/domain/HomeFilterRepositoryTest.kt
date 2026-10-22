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

/** The server calls behind the Home category buttons: the Categories dropdown and the category pages. */
class HomeFilterRepositoryTest {
    private val app = TestApp()
    @AfterTest
    fun tearDown() = runTest { app.close() }

    private fun <T> ApiResult<T>.success(): T = assertIs<ApiResult.Success<T>>(this).value

    private fun query(path: String) = app.jellyfin.requestsTo("GET", path).single().query

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

        app.library.filteredPage(HomeFilter.Shows).success()
        app.library.filteredPage(HomeFilter.Movies).success()

        val (shows, movies) = app.jellyfin.requestsTo("GET", "/Items").map { it.query }
        assertEquals("Series", shows["includeItemTypes"])
        assertEquals("Movie", movies["includeItemTypes"])
        assertEquals("SortName", shows["sortBy"])
        assertEquals("Ascending", shows["sortOrder"])
        assertEquals("true", shows["recursive"])
        assertEquals("0", shows["startIndex"])
    }

    @Test
    fun aGenreIsTheServersOwnFilterAcrossMoviesAndShows() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a"))))

        app.library.filteredPage(HomeFilter.Genre("g1", "Drama")).success()

        val query = query("/Items")
        assertEquals("g1", query["genreIds"])
        assertEquals("Movie,Series", query["includeItemTypes"])
        assertEquals("SortName", query["sortBy"])
    }

    @Test
    fun aPageCarriesTheServersTotalAndWhereItStarts() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a"), itemJson("b"), total = 5, startIndex = 2)))

        val page = app.library.filteredPage(HomeFilter.Movies, startIndex = 2, limit = 2).success()

        assertEquals(5, page.total)
        assertTrue(page.hasMore)
        assertEquals("2", query("/Items")["startIndex"])
        assertEquals("2", query("/Items")["limit"])
    }

    @Test
    fun newIsByReleaseYearNewestFirst() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a", year = 2026), itemJson("b", year = 1999), total = 2)))

        val page = app.library.filteredPage(HomeFilter.New).success()

        assertEquals(listOf("a", "b"), page.items.map { it.id })
        val query = query("/Items")
        assertEquals("ProductionYear,PremiereDate", query["sortBy"])
        assertEquals("Descending", query["sortOrder"])
        assertEquals("Movie,Series", query["includeItemTypes"])
    }

    @Test
    fun newIsNotCutByDateSoItPagesThroughTheWholeLibrary() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a", year = 1980), itemJson("b", year = 1979), total = 9)))

        val page = app.library.filteredPage(HomeFilter.New, limit = 2).success()

        assertEquals(9, page.total)
        assertTrue(page.hasMore)
    }
}
