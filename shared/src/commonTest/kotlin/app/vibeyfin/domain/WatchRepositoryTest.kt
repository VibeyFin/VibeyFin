package app.vibeyfin.domain

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
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

class WatchRepositoryTest {
    private val app = TestApp()

    @AfterTest
    fun tearDown() = runTest { app.close() }

    private fun <T> ApiResult<T>.success(): T = assertIs<ApiResult.Success<T>>(this).value

    private fun item(id: String, seriesId: String? = null) = MediaItem(
        id = id, title = id, kind = ItemKind.Episode, isFolder = false, year = null, runtimeMinutes = null, genres = emptyList(),
        overview = null, rating = null, ageRating = null, seriesName = null, seasonNumber = null, episodeNumber = null,
        posterAspectRatio = 16f / 9f, posterUrl = null, backdropUrl = null, seriesId = seriesId,
    )

    private fun ids(items: List<MediaItem>) = items.map { it.id }

    @Test
    fun whatIsInProgressComesFirstInTheServersOrderThenNextUp() {
        val merged = mergeContinueWatching(
            inProgress = listOf(item("a", "s1"), item("b", "s2")),
            nextUp = listOf(item("c", "s3"), item("d", "s4")),
        )

        assertEquals(listOf("a", "b", "c", "d"), ids(merged))
    }

    @Test
    fun aShowWithSomethingInProgressIsListedOnceAsThatItem() {
        val merged = mergeContinueWatching(
            inProgress = listOf(item("a", "s1")),
            nextUp = listOf(item("next-of-s1", "s1"), item("c", "s3")),
        )

        assertEquals(listOf("a", "c"), ids(merged))
    }

    @Test
    fun anItemInBothListsAppearsOnce() {
        val merged = mergeContinueWatching(inProgress = listOf(item("a")), nextUp = listOf(item("a"), item("b")))

        assertEquals(listOf("a", "b"), ids(merged))
    }

    @Test
    fun aShowGetsOneNextUpCardEvenIfTheServerSendsTwo() {
        val merged = mergeContinueWatching(inProgress = emptyList(), nextUp = listOf(item("a", "s1"), item("b", "s1")))

        assertEquals(listOf("a"), ids(merged))
    }

    @Test
    fun nextUpItemsWithNoShowAreKept() {
        val merged = mergeContinueWatching(inProgress = emptyList(), nextUp = listOf(item("a"), item("b")))

        assertEquals(listOf("a", "b"), ids(merged))
    }

    @Test
    fun theRowHoldsAtMostTwentyAndKeepsWhatIsInProgress() {
        val inProgress = (1..15).map { item("p$it", "ps$it") }
        val nextUp = (1..15).map { item("n$it", "ns$it") }

        val merged = mergeContinueWatching(inProgress, nextUp)

        assertEquals(20, merged.size)
        assertEquals(inProgress.map { it.id } + (1..5).map { "n$it" }, ids(merged))
    }

    @Test
    fun theRowIsBuiltFromBothListsForTheSignedInUser() = runTest {
        val server = app.signedIn()
        server.on("GET", "/UserItems/Resume", Reply.json(itemsJson(itemJson("e1", type = "Episode", seriesId = "s1", positionTicks = 600_000_000))))
        server.on("GET", "/Shows/NextUp", Reply.json(itemsJson(itemJson("e2", type = "Episode", seriesId = "s1"), itemJson("e9", type = "Episode", seriesId = "s2"))))

        val row = app.watch.continueWatching().success()

        assertEquals(listOf("e1", "e9"), ids(row))
        assertEquals("u1", app.jellyfin.requestsTo("GET", "/UserItems/Resume").single().query["userId"])
        assertEquals("u1", app.jellyfin.requestsTo("GET", "/Shows/NextUp").single().query["userId"])
    }

    @Test
    fun whenOneListFailsTheRowIsTheOther() = runTest {
        val server = app.signedIn()
        server.on("GET", "/UserItems/Resume", Reply.status(HttpStatusCode.ServiceUnavailable))
        server.on("GET", "/Shows/NextUp", Reply.json(itemsJson(itemJson("e9", type = "Episode", seriesId = "s2"))))

        assertEquals(listOf("e9"), ids(app.watch.continueWatching().success()))

        server.on("GET", "/UserItems/Resume", Reply.json(itemsJson(itemJson("e1", type = "Episode", positionTicks = 600_000_000))))
        server.on("GET", "/Shows/NextUp", Reply.status(HttpStatusCode.InternalServerError))

        assertEquals(listOf("e1"), ids(app.watch.continueWatching().success()))
    }

    @Test
    fun whenBothFailTheRowFailsWithTheFirstError() = runTest {
        val server = app.signedIn()
        server.on("GET", "/UserItems/Resume", Reply.status(HttpStatusCode.Forbidden))
        server.on("GET", "/Shows/NextUp", Reply.status(HttpStatusCode.NotFound))

        val failure = assertIs<ApiResult.Failure>(app.watch.continueWatching())

        assertEquals(ApiError.Forbidden, failure.error)
    }

    @Test
    fun aRejectedTokenOnEitherListIsWhatTheRowReports() = runTest {
        val server = app.signedIn()
        server.on("GET", "/UserItems/Resume", Reply.status(HttpStatusCode.ServiceUnavailable))
        server.on("GET", "/Shows/NextUp", Reply.status(HttpStatusCode.Unauthorized))

        val failure = assertIs<ApiResult.Failure>(app.watch.continueWatching())

        assertEquals(ApiError.Unauthorized, failure.error)
    }

    @Test
    fun withNobodySignedInTheRowIsUnauthorized() = runTest {
        val failure = assertIs<ApiResult.Failure>(app.watch.continueWatching())

        assertEquals(ApiError.Unauthorized, failure.error)
        assertEquals(0, app.jellyfin.requests.size)
    }

    private fun CoroutineScope.countChanges(): MutableList<Unit> {
        val seen = mutableListOf<Unit>()
        launch(UnconfinedTestDispatcher(), start = CoroutineStart.UNDISPATCHED) { app.watchChanges.events.collect { seen += it } }
        return seen
    }

    @Test
    fun aSavedMarkSaysWhatWasWatchedChanged() = runTest {
        app.signedIn().on("POST", "/UserPlayedItems/e1", Reply.noContent())
        val seen = backgroundScope.countChanges()

        app.watch.setPlayed("e1", played = true).success()

        assertEquals(1, seen.size)
        assertEquals("u1", app.jellyfin.requestsTo("POST", "/UserPlayedItems/e1").single().query["userId"])
    }

    @Test
    fun aMarkThatFailedChangesNothingSoNobodyIsTold() = runTest {
        app.signedIn().on("DELETE", "/UserPlayedItems/e1", Reply.status(HttpStatusCode.Forbidden))
        val seen = backgroundScope.countChanges()

        assertIs<ApiResult.Failure>(app.watch.setPlayed("e1", played = false))

        assertEquals(0, seen.size)
    }

    @Test
    fun markingWithNobodySignedInIsUnauthorized() = runTest {
        assertEquals(ApiError.Unauthorized, assertIs<ApiResult.Failure>(app.watch.setPlayed("e1", played = true)).error)
    }

    private fun episodeItem(id: String, seriesId: String? = "s1") = MediaItem(
        id = id, title = id, kind = ItemKind.Episode, isFolder = false, year = null, runtimeMinutes = null, genres = emptyList(),
        overview = null, rating = null, ageRating = null, seriesName = null, seasonNumber = null, episodeNumber = null,
        posterAspectRatio = 16f / 9f, posterUrl = null, backdropUrl = null, seriesId = seriesId,
    )

    @Test
    fun theNextEpisodeIsTheOneAfterTheCurrentInTheShowsOwnOrder() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Shows/s1/Episodes", Reply.json(itemsJson(itemJson("e1", type = "Episode"), itemJson("e2", "Two", type = "Episode"))))

        val next = app.watch.nextEpisode(episodeItem("e1")).success()

        assertEquals("e2", next?.id)
        assertEquals("e1", app.jellyfin.requestsTo("GET", "/Shows/s1/Episodes").single().query["startItemId"])
    }

    @Test
    fun theLastEpisodeHasNoNextOne() = runTest {
        app.signedIn().on("GET", "/Shows/s1/Episodes", Reply.json(itemsJson(itemJson("e9", type = "Episode"))))

        assertEquals(null, app.watch.nextEpisode(episodeItem("e9")).success())
    }

    @Test
    fun whenTheServerDoesNotStartAtTheEpisodeAskedForThereIsNoGuess() = runTest {
        app.signedIn().on("GET", "/Shows/s1/Episodes", Reply.json(itemsJson(itemJson("other", type = "Episode"), itemJson("e2", type = "Episode"))))

        assertEquals(null, app.watch.nextEpisode(episodeItem("e1")).success())
    }

    @Test
    fun aMovieOrAnEpisodeWithNoShowIsNotAskedAbout() = runTest {
        app.signedIn()

        assertEquals(null, app.watch.nextEpisode(episodeItem("e1", seriesId = null)).success())
        assertEquals(null, app.watch.nextEpisode(episodeItem("m1").copy(kind = ItemKind.Movie)).success())
        assertEquals(0, app.jellyfin.requestsTo("GET", "/Shows/s1/Episodes").size)
    }

    @Test
    fun aFailedLookupIsAFailure() = runTest {
        app.signedIn().on("GET", "/Shows/s1/Episodes", Reply.status(HttpStatusCode.Forbidden))

        assertEquals(ApiError.Forbidden, assertIs<ApiResult.Failure>(app.watch.nextEpisode(episodeItem("e1"))).error)
    }
}
