package com.liamgleeson.vibeyfin.data.api

import com.liamgleeson.vibeyfin.testing.FakeJellyfin
import com.liamgleeson.vibeyfin.testing.Reply
import com.liamgleeson.vibeyfin.testing.itemJson
import com.liamgleeson.vibeyfin.testing.itemsJson
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
}
