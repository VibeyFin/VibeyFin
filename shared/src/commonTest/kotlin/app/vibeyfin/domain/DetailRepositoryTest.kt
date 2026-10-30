package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import app.vibeyfin.testing.personJson
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What an item's page reads beyond the item: its Logo, cast, similar titles, seasons, episodes and next episode. */
class DetailRepositoryTest {
    private val app = TestApp()

    @AfterTest
    fun tearDown() = runTest { app.close() }

    private fun <T> ApiResult<T>.success(): T = assertIs<ApiResult.Success<T>>(this).value

    private fun query(path: String) = app.jellyfin.requestsTo("GET", path).single().query

    @Test
    fun anItemCarriesItsLogoItsActorsAndItsSeasonCount() = runTest {
        app.signedIn().on(
            "GET",
            "/Items",
            Reply.json(
                itemsJson(
                    itemJson(
                        "show",
                        "Severance",
                        type = "Series",
                        logoTag = "logo-tag",
                        childCount = 2,
                        people = listOf(
                            personJson("p1", "Adam Scott", role = "Mark", imageTag = "face"),
                            personJson("p2", "Ben Stiller", type = "Director"),
                            personJson("p3", "Guest Person", type = "GuestStar"),
                            personJson("p4", "No Picture"),
                        ),
                    ),
                ),
            ),
        )

        val item = app.library.item("show").success()

        assertEquals("http://nas:8096/Items/show/Images/Logo?tag=logo-tag&maxWidth=640", item.logoUrl)
        // Actors and guest stars, in the server's order; the director is not cast.
        assertEquals(listOf("Adam Scott", "Guest Person", "No Picture"), item.cast.map { it.name })
        assertEquals("Mark", item.cast.first().role)
        assertEquals("http://nas:8096/Items/p1/Images/Primary?tag=face&maxWidth=200", item.cast.first().imageUrl)
        assertNull(item.cast.last().imageUrl)
        assertEquals(2, item.seasonCount)
    }

    @Test
    fun theSeasonCountIsOnlyForAShow() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("m", "Heat", childCount = 5))))

        assertNull(app.library.item("m").success().seasonCount)
    }

    @Test
    fun theItemIsAskedForWithItsPeopleAndChildCount() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("m"))))

        app.library.item("m").success()

        val fields = query("/Items")["fields"].orEmpty().split(",")
        assertTrue("People" in fields && "ChildCount" in fields)
    }

    @Test
    fun similarTitlesAreTwelveOfThemForThisItem() = runTest {
        app.signedIn().on("GET", "/Items/m1/Similar", Reply.json(itemsJson(itemJson("a"), itemJson("b"))))

        val similar = app.library.similar("m1").success()

        assertEquals(listOf("a", "b"), similar.map { it.id })
        assertEquals("12", query("/Items/m1/Similar")["limit"])
        assertEquals("u1", query("/Items/m1/Similar")["userId"])
    }

    @Test
    fun seasonsComeInTheServersOrderWithTheirNumbers() = runTest {
        app.signedIn().on(
            "GET",
            "/Shows/s1/Seasons",
            Reply.json(
                itemsJson(
                    itemJson("sp", "Specials", type = "Season", isFolder = true, episodeNumber = 0, seriesId = "s1"),
                    itemJson("s1x1", "Season 1", type = "Season", isFolder = true, episodeNumber = 1, seriesId = "s1"),
                ),
            ),
        )

        val seasons = app.library.seasons("s1").success()

        assertEquals(listOf("Specials", "Season 1"), seasons.map { it.title })
        assertTrue(seasons.all { it.kind == ItemKind.Season })
        assertEquals("u1", query("/Shows/s1/Seasons")["userId"])
    }

    @Test
    fun episodesOfASeasonAreAskedForWithTheirSynopsis() = runTest {
        app.signedIn().on(
            "GET",
            "/Shows/s1/Episodes",
            Reply.json(itemsJson(itemJson("e1", "Pilot", type = "Episode", overview = "It begins.", episodeNumber = 1, seasonNumber = 1, seriesId = "s1"))),
        )

        val episodes = app.library.episodes("s1", "season-1").success()

        assertEquals("It begins.", episodes.single().overview)
        val query = query("/Shows/s1/Episodes")
        assertEquals("season-1", query["seasonId"])
        assertEquals("Overview", query["fields"])
    }

    @Test
    fun nextUpOfAShowAsksForOneEpisodeAResumableOneIncluded() = runTest {
        app.signedIn().on("GET", "/Shows/NextUp", Reply.json(itemsJson(itemJson("e4", "Four", type = "Episode", seasonNumber = 2, episodeNumber = 4, seriesId = "s1"))))

        val next = app.watch.nextUpOf("s1").success()

        assertEquals("e4", next?.id)
        val query = query("/Shows/NextUp")
        assertEquals("s1", query["seriesId"])
        assertEquals("1", query["limit"])
        assertEquals("true", query["enableResumable"])
    }

    @Test
    fun aShowNeverStartedOrFinishedHasNothingNextUp() = runTest {
        app.signedIn().on("GET", "/Shows/NextUp", Reply.json(itemsJson()))

        assertNull(app.watch.nextUpOf("s1").success())
    }

    @Test
    fun anItemCarriesWhetherItIsOnMyListAndItsTrailers() = runTest {
        app.signedIn().on(
            "GET",
            "/Items",
            Reply.json(
                itemsJson(
                    itemJson(
                        "m1",
                        "Heat",
                        favorite = true,
                        localTrailerCount = 2,
                        remoteTrailers = listOf("not a link", "https://www.youtube.com/watch?v=abc"),
                    ),
                ),
            ),
        )

        val item = app.library.item("m1").success()

        assertTrue(item.isFavorite)
        assertEquals(2, item.localTrailerCount)
        // The first address that is a web link.
        assertEquals("https://www.youtube.com/watch?v=abc", item.remoteTrailerUrl)
    }

    @Test
    fun anItemNotOnMyListWithNoTrailers() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("m1", "Heat"))))

        val item = app.library.item("m1").success()

        assertEquals(false, item.isFavorite)
        assertEquals(0, item.localTrailerCount)
        assertNull(item.remoteTrailerUrl)
    }

    @Test
    fun theCollectionsAnItemIsInAreFoldersThatOpenTheirMembers() = runTest {
        app.signedIn().on("GET", "/Items/m1/Collections", Reply.json(itemsJson(itemJson("box-1", "Neon Collection", type = "BoxSet", isFolder = true))))

        val collections = app.library.collectionsOf("m1").success()

        assertEquals(listOf("box-1"), collections.map { it.id })
        assertTrue(collections.single().isFolder)
        assertEquals("u1", query("/Items/m1/Collections")["userId"])
    }

    @Test
    fun theLocalTrailerIsTheFirstOfTheServersAndNullWhenThereIsNone() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items/m1/LocalTrailers", Reply.json("[${itemJson("t1", "Trailer", type = "Trailer")}]"))
        server.on("GET", "/Items/m2/LocalTrailers", Reply.json("[]"))

        assertEquals("t1", app.library.localTrailer("m1").success()?.id)
        assertNull(app.library.localTrailer("m2").success())
    }
}
