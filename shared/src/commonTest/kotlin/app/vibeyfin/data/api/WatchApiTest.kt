package app.vibeyfin.data.api

import app.vibeyfin.testing.FakeJellyfin
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WatchApiTest {
    private val address = "http://nas:8096"
    private val jellyfin = FakeJellyfin()
    private val server = jellyfin.server(address)
    private val api = jellyfin.api(address, token = "token-1")

    private fun <T> ApiResult<T>.success(): T = assertIs<ApiResult.Success<T>>(this).value

    @Test
    fun whatIsInProgressIsAskedForAsVideoForTheUser() = runTest {
        server.on("GET", "/UserItems/Resume", Reply.json(itemsJson(itemJson("e1", type = "Episode", positionTicks = 600_000_000))))

        val result = api.resumeItems("u1", limit = 20).success()

        assertEquals(600_000_000L, result.items.single().userData?.playbackPositionTicks)
        val request = jellyfin.requestsTo("GET", "/UserItems/Resume").single()
        assertEquals("u1", request.query["userId"])
        assertEquals("Video", request.query["mediaTypes"])
        assertEquals("20", request.query["limit"])
        assertTrue(request.authorization!!.contains("""Token="token-1""""))
    }

    @Test
    fun nextUpLeavesHalfWatchedEpisodesToTheResumeList() = runTest {
        server.on("GET", "/Shows/NextUp", Reply.json(itemsJson(itemJson("e2", type = "Episode", seriesId = "s1"))))

        val result = api.nextUp("u1", limit = 20).success()

        assertEquals("s1", result.items.single().seriesId)
        val query = jellyfin.requestsTo("GET", "/Shows/NextUp").single().query
        assertEquals("u1", query["userId"])
        assertEquals("20", query["limit"])
        assertEquals("false", query["enableResumable"])
        assertEquals("false", query["enableRewatching"])
    }

    @Test
    fun bothAreReadsSoAServerErrorIsTriedAgain() = runTest {
        var calls = 0
        server.on("GET", "/UserItems/Resume") {
            if (++calls == 1) Reply.status(HttpStatusCode.ServiceUnavailable) else Reply.json(itemsJson())
        }

        api.resumeItems("u1", limit = 20).success()

        assertEquals(2, jellyfin.requestsTo("GET", "/UserItems/Resume").size)
    }

    @Test
    fun markingWatchedPostsAndMarkingUnwatchedDeletesForTheUser() = runTest {
        server.on("POST", "/UserPlayedItems/e1", Reply.json("""{"Played":true}"""))
        server.on("DELETE", "/UserPlayedItems/e1", Reply.json("""{"Played":false}"""))

        api.setPlayed("e1", "u1", played = true).success()
        api.setPlayed("e1", "u1", played = false).success()

        assertEquals("u1", jellyfin.requestsTo("POST", "/UserPlayedItems/e1").single().query["userId"])
        assertEquals("u1", jellyfin.requestsTo("DELETE", "/UserPlayedItems/e1").single().query["userId"])
    }

    @Test
    fun theAnswersBodyIsNotNeeded() = runTest {
        server.on("POST", "/UserPlayedItems/e1", Reply.noContent())

        api.setPlayed("e1", "u1", played = true).success()
    }

    @Test
    fun settingAMarkTwiceIsHarmlessSoAServerErrorIsTriedAgain() = runTest {
        server.on("POST", "/UserPlayedItems/e1", Reply.status(HttpStatusCode.ServiceUnavailable))

        val result = api.setPlayed("e1", "u1", played = true)

        assertIs<ApiResult.Failure>(result)
        assertEquals(3, jellyfin.requestsTo("POST", "/UserPlayedItems/e1").size)
    }

    @Test
    fun aRefusalIsNotTriedAgain() = runTest {
        server.on("POST", "/UserPlayedItems/e1", Reply.status(HttpStatusCode.Forbidden))

        assertEquals(ApiError.Forbidden, assertIs<ApiResult.Failure>(api.setPlayed("e1", "u1", played = true)).error)
        assertEquals(1, jellyfin.requestsTo("POST", "/UserPlayedItems/e1").size)
    }

    @Test
    fun episodesAreAskedForFromOneEpisodeOnwardsTwoAtATime() = runTest {
        server.on("GET", "/Shows/s1/Episodes", Reply.json(itemsJson(itemJson("e1", type = "Episode"), itemJson("e2", type = "Episode"))))

        val result = api.episodesFrom("s1", "e1", "u1", limit = 2).success()

        assertEquals(listOf("e1", "e2"), result.items.map { it.id })
        val query = jellyfin.requestsTo("GET", "/Shows/s1/Episodes").single().query
        assertEquals("u1", query["userId"])
        assertEquals("e1", query["startItemId"])
        assertEquals("2", query["limit"])
    }
}
