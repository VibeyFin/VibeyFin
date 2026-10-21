package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TICKS_PER_MINUTE
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
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

    @Test
    fun whatTheUserHasWatchedComesWithTheItem() = runTest {
        val server = app.signedIn()
        server.on(
            "GET", "/Items",
            Reply.json(
                itemsJson(
                    itemJson(
                        "e1", "Pilot", type = "Episode", seriesName = "Show", seriesId = "s1", seasonNumber = 2, episodeNumber = 5,
                        runtimeTicks = 40 * TICKS_PER_MINUTE, positionTicks = 10 * TICKS_PER_MINUTE, thumbTag = "t", primaryTag = "p",
                    ),
                ),
            ),
        )

        val episode = app.library.item("e1").success()

        assertEquals("s1", episode.seriesId)
        assertEquals(40 * 60_000L, episode.runtimeMs)
        assertEquals(10 * 60_000L, episode.resumePositionMs)
        assertFalse(episode.played)
        assertEquals(0.25f, episode.progress)
    }

    @Test
    fun aFinishedItemHasNoPositionToResumeFromWhateverTheServerStored() = runTest {
        val server = app.signedIn()
        server.on(
            "GET", "/Items",
            Reply.json(itemsJson(itemJson("m1", runtimeTicks = 90 * TICKS_PER_MINUTE, positionTicks = 80 * TICKS_PER_MINUTE, played = true))),
        )

        val movie = app.library.item("m1").success()

        assertTrue(movie.played)
        assertEquals(0L, movie.resumePositionMs)
        assertNull(movie.progress)
    }

    @Test
    fun anItemTheUserHasNotStartedHasNoProgress() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("m1", runtimeTicks = 90 * TICKS_PER_MINUTE))))

        val movie = app.library.item("m1").success()

        assertFalse(movie.played)
        assertEquals(0L, movie.resumePositionMs)
        assertNull(movie.progress)
    }

    @Test
    fun theLandscapePictureIsTheThumbElseTheBackdropElseThePrimaryImage() = runTest {
        val server = app.signedIn()
        server.on(
            "GET", "/Items",
            Reply.json(
                itemsJson(
                    itemJson("a", thumbTag = "t", backdropTag = "b", primaryTag = "p"),
                    itemJson("b", backdropTag = "b", primaryTag = "p"),
                    itemJson("c", primaryTag = "p"),
                    itemJson("d"),
                ),
            ),
        )

        val urls = app.library.children("folder").success().items.map { it.landscapeUrl }

        assertEquals(
            listOf(
                "http://nas:8096/Items/a/Images/Thumb?tag=t&maxWidth=640",
                "http://nas:8096/Items/b/Images/Backdrop?tag=b&maxWidth=640",
                "http://nas:8096/Items/c/Images/Primary?tag=p&maxWidth=640",
                null,
            ),
            urls,
        )
    }

    @Test
    fun eachPictureCarriesTheBlurHashOfItsOwnTag() = runTest {
        val server = app.signedIn()
        server.on(
            "GET", "/Items",
            Reply.json(
                itemsJson(
                    itemJson(
                        "a", thumbTag = "t", backdropTag = "b", primaryTag = "p",
                        blurHashes = mapOf(
                            // An older picture's hash sits beside the current one and must not be used.
                            "Primary" to mapOf("old" to "STALE", "p" to "POSTER"),
                            "Backdrop" to mapOf("b" to "BACKDROP"),
                            "Thumb" to mapOf("t" to "THUMB"),
                            "Logo" to mapOf("l" to "LOGO"),
                        ),
                    ),
                ),
            ),
        )

        val item = app.library.children("folder").success().items.single()

        assertEquals("POSTER", item.posterBlurHash)
        assertEquals("BACKDROP", item.backdropBlurHash)
        assertEquals("THUMB", item.landscapeBlurHash)
    }

    @Test
    fun theLandscapeBlurHashBelongsToWhicheverPictureTheLandscapeUrlUses() = runTest {
        val server = app.signedIn()
        server.on(
            "GET", "/Items",
            Reply.json(
                itemsJson(
                    itemJson("b", backdropTag = "b", primaryTag = "p", blurHashes = mapOf("Backdrop" to mapOf("b" to "BACKDROP"), "Primary" to mapOf("p" to "POSTER"))),
                    itemJson("c", primaryTag = "p", blurHashes = mapOf("Primary" to mapOf("p" to "POSTER"))),
                ),
            ),
        )

        val (fromBackdrop, fromPoster) = app.library.children("folder").success().items

        assertEquals("BACKDROP", fromBackdrop.landscapeBlurHash)
        assertEquals("POSTER", fromPoster.landscapeBlurHash)
    }

    @Test
    fun aPictureWithoutAUsableBlurHashHasNone() = runTest {
        val server = app.signedIn()
        server.on(
            "GET", "/Items",
            Reply.json(
                itemsJson(
                    // A picture the server never hashed, one whose hash is for another tag, one that is blank, and no picture.
                    itemJson("a", primaryTag = "p"),
                    itemJson("b", primaryTag = "p", blurHashes = mapOf("Primary" to mapOf("other" to "POSTER"))),
                    itemJson("c", primaryTag = "p", blurHashes = mapOf("Primary" to mapOf("p" to "  "))),
                    itemJson("d", blurHashes = mapOf("Primary" to mapOf("p" to "POSTER"))),
                ),
            ),
        )

        val items = app.library.children("folder").success().items

        assertTrue(items.all { it.posterBlurHash == null && it.landscapeBlurHash == null && it.backdropBlurHash == null })
    }
}
