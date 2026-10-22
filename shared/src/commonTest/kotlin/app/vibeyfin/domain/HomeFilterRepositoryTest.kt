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

    @Test
    fun genresCarryHowManyMoviesAndShowsTheyHoldAndAnEmptyOneIsLeftOut() = runTest {
        app.signedIn().on(
            "GET", "/Genres",
            Reply.json(
                itemsJson(
                    itemJson("g1", "Drama", type = "Genre", movieCount = 8, seriesCount = 1),
                    itemJson("g2", "Western", type = "Genre", movieCount = 0, seriesCount = 0),
                    itemJson("g3", "Action", type = "Genre", movieCount = 6),
                    itemJson("g4", "Unknown count", type = "Genre"),
                ),
            ),
        )

        val genres = app.library.genres().success()

        assertEquals(listOf("Action" to 6, "Drama" to 9, "Unknown count" to null), genres.map { it.name to it.count })
        assertEquals("ItemCounts", query("/Genres")["fields"])
    }

    @Test
    fun eachSortOrdersByTheServersKeys() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a"))))

        app.library.filteredPage(HomeFilter.Movies, sort = PageSort.Name).success()
        app.library.filteredPage(HomeFilter.Movies, sort = PageSort.Year).success()
        app.library.filteredPage(HomeFilter.Movies, sort = PageSort.Rating).success()

        val (name, year, rating) = app.jellyfin.requestsTo("GET", "/Items").map { it.query }
        assertEquals("SortName" to "Ascending", name["sortBy"] to name["sortOrder"])
        assertEquals("ProductionYear,PremiereDate" to "Descending", year["sortBy"] to year["sortOrder"])
        assertEquals("CommunityRating,SortName" to "Descending", rating["sortBy"] to rating["sortOrder"])
    }

    @Test
    fun aShowsPageCanBeNarrowedToAGenre() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a"))))

        app.library.filteredPage(HomeFilter.Shows, genreId = "g-drama").success()

        val query = query("/Items")
        assertEquals("g-drama", query["genreIds"])
        assertEquals("Series", query["includeItemTypes"])
    }

    @Test
    fun aGenresOwnPageKeepsItsGenreWhateverElseIsAsked() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a"))))

        app.library.filteredPage(HomeFilter.Genre("g1", "Drama"), genreId = "other").success()

        assertEquals("g1", query("/Items")["genreIds"])
    }

    private val genresJson = itemsJson(
        itemJson("g1", "Drama", type = "Genre", movieCount = 8, seriesCount = 1),
        itemJson("g2", "Sitcom", type = "Genre", movieCount = 0, seriesCount = 3),
        itemJson("g3", "Action", type = "Genre", movieCount = 6, seriesCount = 0),
    )

    @Test
    fun theMoviesPageCountsOnlyMoviesAndLeavesOutGenresWithNone() = runTest {
        app.signedIn().on("GET", "/Genres", Reply.json(genresJson))

        val genres = app.library.genres(HomeFilter.Movies).success()

        assertEquals(listOf("Action" to 6, "Drama" to 8), genres.map { it.name to it.count })
        assertEquals("Movie", query("/Genres")["includeItemTypes"])
    }

    @Test
    fun theShowsPageCountsOnlyShowsAndLeavesOutGenresWithNone() = runTest {
        app.signedIn().on("GET", "/Genres", Reply.json(genresJson))

        val genres = app.library.genres(HomeFilter.Shows).success()

        assertEquals(listOf("Drama" to 1, "Sitcom" to 3), genres.map { it.name to it.count })
        assertEquals("Series", query("/Genres")["includeItemTypes"])
    }

    @Test
    fun newAndHomeCountMoviesAndShowsTogether() = runTest {
        app.signedIn().on("GET", "/Genres", Reply.json(genresJson))

        val onNew = app.library.genres(HomeFilter.New).success()
        val onHome = app.library.genres().success()

        assertEquals(listOf("Action" to 6, "Drama" to 9, "Sitcom" to 3), onNew.map { it.name to it.count })
        assertEquals(onNew, onHome)
        assertEquals(listOf("Movie,Series", "Movie,Series"), app.jellyfin.requestsTo("GET", "/Genres").map { it.query["includeItemTypes"] })
    }

    // The years of a page, read from its own items.

    @Test
    fun theYearsOfAPageAreThoseOfItsItemsDistinctNewestFirst() = runTest {
        val server = app.signedIn()
        val all = listOf(2026, 2026, 2025, null, 2025, 1999)
        server.on("GET", "/Items") { request ->
            val start = request.query["startIndex"]!!.toInt()
            val page = all.drop(start).take(2)
            Reply.json(itemsJson(*page.mapIndexed { i, year -> itemJson("t${start + i}", year = year) }.toTypedArray(), total = all.size, startIndex = start))
        }

        val years = app.library.yearsOf(HomeFilter.Movies, genreId = "g-action").success()

        assertEquals(listOf(2026, 2025, 1999), years)
        val requests = app.jellyfin.requestsTo("GET", "/Items").map { it.query }
        assertEquals(3, requests.size)
        assertEquals(listOf("0", "2", "4"), requests.map { it["startIndex"] })
        assertEquals("Movie", requests.first()["includeItemTypes"])
        assertEquals("g-action", requests.first()["genreIds"])
        assertEquals("false", requests.first()["enableImages"])
        assertEquals("ProductionYear,PremiereDate", requests.first()["sortBy"])
    }

    @Test
    fun aGenresOwnPageScansThatGenre() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a", year = 2001))))

        assertEquals(listOf(2001), app.library.yearsOf(HomeFilter.Genre("g1", "Drama")).success())
        assertEquals("g1", query("/Items")["genreIds"])
    }

    @Test
    fun aScanThatFailsHalfwayKeepsTheYearsItFound() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items") { request ->
            if (request.query["startIndex"] == "0") {
                Reply.json(itemsJson(itemJson("a", year = 2020), itemJson("b", year = 2019), total = 9))
            } else {
                Reply.status(HttpStatusCode.ServiceUnavailable)
            }
        }

        assertEquals(listOf(2020, 2019), app.library.yearsOf(HomeFilter.New).success())
    }

    @Test
    fun aScanThatFindsNothingBeforeFailingIsAFailure() = runTest {
        app.signedIn().on("GET", "/Items", Reply.status(HttpStatusCode.ServiceUnavailable))

        assertIs<ApiResult.Failure>(app.library.yearsOf(HomeFilter.New))
    }
}
