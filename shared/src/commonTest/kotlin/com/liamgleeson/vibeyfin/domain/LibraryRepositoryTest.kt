package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.api.ApiError
import com.liamgleeson.vibeyfin.data.api.ApiResult
import com.liamgleeson.vibeyfin.domain.model.ItemKind
import com.liamgleeson.vibeyfin.testing.Reply
import com.liamgleeson.vibeyfin.testing.TICKS_PER_MINUTE
import com.liamgleeson.vibeyfin.testing.TestApp
import com.liamgleeson.vibeyfin.testing.itemJson
import com.liamgleeson.vibeyfin.testing.itemsJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryRepositoryTest {
    private val app = TestApp()

    @AfterTest
    fun tearDown() = runTest { app.close() }

    private fun <T> ApiResult<T>.success(): T = assertIs<ApiResult.Success<T>>(this).value

    private fun ApiResult<*>.error(): ApiError = assertIs<ApiResult.Failure>(this).error

    @Test
    fun librariesAreOpenableWideTilesWithTheirArtwork() = runTest {
        val server = app.signedIn()
        server.on(
            "GET", "/UserViews",
            Reply.json(
                itemsJson(
                    itemJson("lib-1", "Movies", type = "CollectionFolder", isFolder = true, primaryTag = "tag-1"),
                    itemJson("lib-2", "Playlists", type = "UserView", isFolder = true),
                ),
            ),
        )

        val libraries = app.library.libraries().success()

        assertEquals(listOf("Movies", "Playlists"), libraries.map { it.title })
        assertEquals(listOf(ItemKind.Library, ItemKind.Library), libraries.map { it.kind })
        assertTrue(libraries.all { it.isFolder })
        assertEquals("http://nas:8096/Items/lib-1/Images/Primary?tag=tag-1&maxWidth=400", libraries[0].posterUrl)
        assertNull(libraries[1].posterUrl)
        assertEquals(16f / 9f, libraries[0].posterAspectRatio)
        // The server's library picture already says the name; one without a picture needs the caption.
        assertTrue(libraries[0].nameIsOnPoster)
        assertFalse(libraries[1].nameIsOnPoster)
        assertEquals("u1", app.jellyfin.requestsTo("GET", "/UserViews").single().query["userId"])
    }

    @Test
    fun aMovieKeepsEverythingTheDetailPageShows() = runTest {
        val server = app.signedIn()
        server.on(
            "GET", "/Items",
            Reply.json(
                itemsJson(
                    itemJson(
                        "m1", "Caminandes", year = 2013, runtimeTicks = 1_460_416_670, overview = "A llama.",
                        genres = listOf("Animation", "Comedy"), communityRating = 7.3, officialRating = "PG",
                        primaryTag = "p", backdropTag = "b",
                    ),
                ),
            ),
        )

        val movie = app.library.item("m1").success()

        assertEquals("m1", movie.id)
        assertEquals("Caminandes", movie.title)
        assertEquals(ItemKind.Movie, movie.kind)
        assertFalse(movie.isFolder)
        assertEquals(2013, movie.year)
        // 1,460,416,670 ticks of 100 ns is 146 seconds.
        assertEquals(2, movie.runtimeMinutes)
        assertEquals("A llama.", movie.overview)
        assertEquals(listOf("Animation", "Comedy"), movie.genres)
        assertEquals(7.3, movie.rating)
        assertEquals("PG", movie.ageRating)
        assertEquals(2f / 3f, movie.posterAspectRatio)
        assertFalse(movie.nameIsOnPoster)
        assertEquals("http://nas:8096/Items/m1/Images/Primary?tag=p&maxWidth=400", movie.posterUrl)
        assertEquals("http://nas:8096/Items/m1/Images/Backdrop?tag=b&maxWidth=1280", movie.backdropUrl)
    }

    @Test
    fun anEpisodeKnowsItsShowSeasonAndNumber() = runTest {
        val server = app.signedIn()
        server.on(
            "GET", "/Items",
            Reply.json(
                itemsJson(
                    itemJson("e1", "Pilot", type = "Episode", seriesName = "Big Buck", seasonNumber = 1, episodeNumber = 3, primaryTag = "p"),
                ),
            ),
        )

        val episode = app.library.item("e1").success()

        assertEquals(ItemKind.Episode, episode.kind)
        assertEquals("Big Buck", episode.seriesName)
        assertEquals(1, episode.seasonNumber)
        assertEquals(3, episode.episodeNumber)
        assertEquals(16f / 9f, episode.posterAspectRatio)
    }

    @Test
    fun theKindFollowsTheServersType() = runTest {
        val server = app.signedIn()
        server.on(
            "GET", "/Items",
            Reply.json(
                itemsJson(
                    itemJson("a", type = "Folder", isFolder = true),
                    itemJson("b", type = "Series", isFolder = true),
                    itemJson("c", type = "Season", isFolder = true),
                    itemJson("d", type = "BoxSet", isFolder = true),
                    itemJson("e", type = "Audio"),
                ),
            ),
        )

        val kinds = app.library.children("p").success().items.map { it.kind }

        assertEquals(listOf(ItemKind.Folder, ItemKind.Series, ItemKind.Season, ItemKind.Other, ItemKind.Other), kinds)
    }

    @Test
    fun aServerAspectRatioBeatsTheGuess() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("m1", aspectRatio = 1.0))))

        assertEquals(1f, app.library.item("m1").success().posterAspectRatio)
    }

    @Test
    fun runtimesRoundToTheNearestMinuteAndNeverShowZero() = runTest {
        val server = app.signedIn()
        server.on(
            "GET", "/Items",
            Reply.json(
                itemsJson(
                    itemJson("a", runtimeTicks = 90 * TICKS_PER_MINUTE + TICKS_PER_MINUTE / 2),
                    itemJson("b", runtimeTicks = 90 * TICKS_PER_MINUTE + TICKS_PER_MINUTE / 2 - 1),
                    itemJson("c", runtimeTicks = 1_000),
                    itemJson("d", runtimeTicks = 0),
                    itemJson("e"),
                ),
            ),
        )

        val minutes = app.library.children("p").success().items.map { it.runtimeMinutes }

        assertEquals(listOf(91, 90, 1, null, null), minutes)
    }

    @Test
    fun blankTextIsLeftOutAndAMissingNameIsEmpty() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("a", name = null, overview = "  ", officialRating = ""))))

        val item = app.library.item("a").success()

        assertEquals("", item.title)
        assertNull(item.overview)
        assertNull(item.ageRating)
    }

    @Test
    fun aFolderListIsOnePageAtATime() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("a"), itemJson("b"), total = 5)))

        val page = app.library.children("folder-1", startIndex = 0, limit = 2).success()

        assertEquals(listOf("a", "b"), page.items.map { it.id })
        assertEquals(5, page.total)
        assertTrue(page.hasMore)
        val query = app.jellyfin.requestsTo("GET", "/Items").single().query
        assertEquals("folder-1", query["parentId"])
        assertEquals("2", query["limit"])
    }

    @Test
    fun theLastPageHasNoMoreAndAnEmptyPageNeverLoopsForever() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items") { request ->
            when (request.query["startIndex"]) {
                "4" -> Reply.json(itemsJson(itemJson("e"), total = 5, startIndex = 4))
                else -> Reply.json(itemsJson(total = 9, startIndex = 7))
            }
        }

        assertFalse(app.library.children("p", startIndex = 4).success().hasMore)
        assertFalse(app.library.children("p", startIndex = 7).success().hasMore)
    }

    @Test
    fun anItemTheServerDoesNotKnowIsNotFound() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson()))

        assertEquals(ApiError.NotFound, app.library.item("gone").error())
    }

    @Test
    fun failuresComeBackAsTheirTypedErrors() = runTest {
        val server = app.signedIn()
        server.on("GET", "/UserViews", Reply.status(HttpStatusCode.Forbidden))
        server.on("GET", "/Items", Reply.status(HttpStatusCode.NotFound))

        assertEquals(ApiError.Forbidden, app.library.libraries().error())
        assertEquals(ApiError.NotFound, app.library.item("m1").error())
        assertEquals(ApiError.NotFound, app.library.children("p").error())
    }

    @Test
    fun nothingIsAskedWhenNobodyIsSignedIn() = runTest {
        app.serverAt("http://nas:8096")

        assertEquals(ApiError.Unauthorized, app.library.libraries().error())
        assertEquals(ApiError.Unauthorized, app.library.children("p").error())
        assertEquals(ApiError.Unauthorized, app.library.item("m1").error())
        assertEquals(0, app.jellyfin.requests.size)
    }

    @Test
    fun aRejectedTokenEndsTheSession() = runTest {
        val server = app.signedIn()
        server.on("GET", "/UserViews", Reply.status(HttpStatusCode.Unauthorized))

        assertEquals(ApiError.Unauthorized, app.library.libraries().error())

        // The profile is locked first, then the state moves on, so wait for the state.
        app.connection.state.first { it is ConnectionState.Expired }
        assertNull(app.accounts.sessionToken("srv-1", "u1"))
    }
}
