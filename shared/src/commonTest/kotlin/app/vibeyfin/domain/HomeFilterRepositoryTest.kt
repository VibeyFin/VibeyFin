package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.model.ItemKind
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
import kotlin.test.assertNull
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
    fun newIsWhatWasAddedToTheServerMostRecentlyFirst() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a"), itemJson("b"), total = 2)))

        val page = app.library.filteredPage(HomeFilter.New).success()

        assertEquals(listOf("a", "b"), page.items.map { it.id })
        val query = query("/Items")
        assertEquals("DateCreated", query["sortBy"])
        assertEquals("Descending", query["sortOrder"])
        assertEquals("Movie,Series", query["includeItemTypes"])
    }

    @Test
    fun newPagesThroughTheWholeLibrary() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a"), itemJson("b"), total = 9)))

        val page = app.library.filteredPage(HomeFilter.New, limit = 2).success()

        assertEquals(9, page.total)
        assertTrue(page.hasMore)
    }

    @Test
    fun genresCarryHowManyMoviesAndShowsTheyHoldAndAnEmptyOneIsLeftOut() = runTest {
        app.signedIn().on(
            "GET",
            "/Genres",
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

        app.library.filteredPage(HomeFilter.Movies, FilterSet(sort = PageSort.Name)).success()
        app.library.filteredPage(HomeFilter.Movies, FilterSet(sort = PageSort.Added)).success()
        app.library.filteredPage(HomeFilter.Movies, FilterSet(sort = PageSort.Rating)).success()

        val (name, added, rating) = app.jellyfin.requestsTo("GET", "/Items").map { it.query }
        assertEquals("SortName" to "Ascending", name["sortBy"] to name["sortOrder"])
        assertEquals("DateCreated" to "Descending", added["sortBy"] to added["sortOrder"])
        assertEquals("CommunityRating,SortName" to "Descending", rating["sortBy"] to rating["sortOrder"])
    }

    @Test
    fun aShowsPageCanBeNarrowedToAGenre() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a"))))

        app.library.filteredPage(HomeFilter.Shows, FilterSet(genres = listOf(NamedValue("g-drama", "Drama")))).success()

        val query = query("/Items")
        assertEquals("g-drama", query["genreIds"])
        assertEquals("Series", query["includeItemTypes"])
    }

    @Test
    fun aGenresOwnPageKeepsItsGenreWhateverElseIsAsked() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a"))))

        app.library.filteredPage(HomeFilter.Genre("g1", "Drama"), FilterSet(genres = listOf(NamedValue("other", "Other")))).success()

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

    // Recently added shows the show, never an episode or a season.

    @Test
    fun aNewEpisodeAndANewSeasonAreShownAsTheirShowWithTheShowsPoster() = runTest {
        app.signedIn().on(
            "GET",
            "/Items/Latest",
            Reply.json(
                "[" + listOf(
                    itemJson("ep1", "Pilot", type = "Episode", seriesId = "show-1", seriesName = "Severance", primaryTag = "episode-still", seriesPrimaryImageTag = "show-poster", seasonNumber = 1, episodeNumber = 1, year = 2022),
                    itemJson("season-2", "Season 2", type = "Season", seriesId = "show-2", seriesName = "Andor", primaryTag = "season-cover", seriesPrimaryImageTag = "andor-poster"),
                    itemJson("movie-1", "Heat", type = "Movie", primaryTag = "heat-poster", year = 1995),
                ).joinToString(",") + "]",
            ),
        )

        val items = app.library.latest("lib-s").success()

        assertEquals(listOf("show-1", "show-2", "movie-1"), items.map { it.id })
        assertEquals(listOf("Severance", "Andor", "Heat"), items.map { it.title })
        assertEquals(listOf(ItemKind.Series, ItemKind.Series, ItemKind.Movie), items.map { it.kind })
        // The show's own poster, not the still or the season's cover.
        assertEquals("http://nas:8096/Items/show-1/Images/Primary?tag=show-poster&maxWidth=400", items[0].posterUrl)
        assertEquals("http://nas:8096/Items/show-2/Images/Primary?tag=andor-poster&maxWidth=400", items[1].posterUrl)
        assertEquals("http://nas:8096/Items/movie-1/Images/Primary?tag=heat-poster&maxWidth=400", items[2].posterUrl)
        // A show opens its seasons, and is tall like a poster.
        assertTrue(items[0].isFolder)
        assertEquals(2f / 3f, items[0].posterAspectRatio)
        assertNull(items[0].episodeNumber)
    }

    @Test
    fun severalNewEpisodesOfOneShowAreOnePoster() = runTest {
        app.signedIn().on(
            "GET",
            "/Items/Latest",
            Reply.json(
                "[" + listOf(
                    itemJson("ep3", "Three", type = "Episode", seriesId = "show-1", seriesName = "Severance", seriesPrimaryImageTag = "p"),
                    itemJson("ep2", "Two", type = "Episode", seriesId = "show-1", seriesName = "Severance", seriesPrimaryImageTag = "p"),
                    itemJson("show-2", "Andor", type = "Series", primaryTag = "andor"),
                ).joinToString(",") + "]",
            ),
        )

        val items = app.library.latest("lib-s").success()

        assertEquals(listOf("show-1", "show-2"), items.map { it.id })
    }

    @Test
    fun aShowWithNoPosterIsStillAShowWithNoPicture() = runTest {
        app.signedIn().on("GET", "/Items/Latest", Reply.json("[${itemJson("ep1", "Pilot", type = "Episode", seriesId = "show-1", seriesName = "Severance", primaryTag = "still")}]"))

        val show = app.library.latest("lib-s").success().single()

        assertEquals("show-1", show.id)
        assertNull(show.posterUrl)
    }
}
