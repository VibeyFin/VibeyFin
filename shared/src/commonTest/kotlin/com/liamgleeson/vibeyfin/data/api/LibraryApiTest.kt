package com.liamgleeson.vibeyfin.data.api

import com.liamgleeson.vibeyfin.testing.ConnectionRefused
import com.liamgleeson.vibeyfin.testing.FakeJellyfin
import com.liamgleeson.vibeyfin.testing.Reply
import com.liamgleeson.vibeyfin.testing.itemJson
import com.liamgleeson.vibeyfin.testing.itemsJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryApiTest {
    private val address = "http://nas:8096"
    private val jellyfin = FakeJellyfin()
    private val server = jellyfin.server(address)
    private val api = jellyfin.api(address, token = "token-1")

    private fun <T> ApiResult<T>.success(): T = assertIs<ApiResult.Success<T>>(this).value

    private fun ApiResult<*>.error(): ApiError = assertIs<ApiResult.Failure>(this).error

    @Test
    fun librariesAreTheUsersViews() = runTest {
        server.on("GET", "/UserViews", Reply.json(itemsJson(itemJson("lib-1", "Movies", type = "CollectionFolder", isFolder = true))))

        val views = api.userViews("u1").success()

        assertEquals(listOf("lib-1"), views.items.map { it.id })
        val request = jellyfin.requestsTo("GET", "/UserViews").single()
        assertEquals("u1", request.query["userId"])
        assertTrue(request.authorization!!.contains("""Token="token-1""""))
    }

    @Test
    fun aListIsAskedForOnePageInNameOrder() = runTest {
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("a"), total = 130, startIndex = 60)))

        val page = api.items(parentId = "folder-1", userId = "u1", startIndex = 60, limit = 60).success()

        assertEquals(130, page.totalRecordCount)
        val query = jellyfin.requestsTo("GET", "/Items").single().query
        assertEquals("folder-1", query["parentId"])
        assertEquals("u1", query["userId"])
        assertEquals("SortName", query["sortBy"])
        assertEquals("Ascending", query["sortOrder"])
        assertEquals("60", query["startIndex"])
        assertEquals("60", query["limit"])
    }

    @Test
    fun oneItemIsAskedForByIdWithItsOverviewAndGenres() = runTest {
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("m1", overview = "A llama.", genres = listOf("Animation")))))

        val item = api.itemsById("m1", "u1").success().items.single()

        assertEquals("A llama.", item.overview)
        assertEquals(listOf("Animation"), item.genres)
        val query = jellyfin.requestsTo("GET", "/Items").single().query
        assertEquals("m1", query["ids"])
        assertEquals("u1", query["userId"])
        assertEquals("Overview,Genres,MediaStreams,Trickplay", query["fields"])
    }

    @Test
    fun anItemWithOnlyAnIdIsStillRead() = runTest {
        server.on("GET", "/Items", Reply.json("""{"Items":[{"Id":"bare"}]}"""))

        val item = api.itemsById("bare", "u1").success().items.single()

        assertEquals("bare", item.id)
        assertEquals(emptyList(), item.genres)
        assertEquals(emptyMap(), item.imageTags)
        assertFalse(item.isFolder)
    }

    @Test
    fun aPartialTrickplayEntryDoesNotFailTheItem() = runTest {
        server.on("GET", "/Items", Reply.json("""{"Items":[{"Id":"m1","Trickplay":{"src1":{"320":{"Width":320}}}}]}"""))

        val item = api.itemsById("m1", "u1").success().items.single()

        val info = item.trickplay.getValue("src1").getValue("320")
        assertEquals(320, info.width)
        assertEquals(0, info.thumbnailCount)
        assertEquals(0, info.interval)
    }

    @Test
    fun imageUrlsCarryTheTagSoACachedImageIsNeverStale() {
        assertEquals(
            "http://nas:8096/Items/m1/Images/Primary?tag=abc&maxWidth=400",
            itemImageUrl("http://nas:8096", "m1", "Primary", "abc", 400),
        )
    }

    // Retries

    @Test
    fun aReadIsTriedAgainWhenNothingAnswers() = runTest {
        var calls = 0
        server.on("GET", "/UserViews") {
            if (++calls < 3) throw ConnectionRefused()
            Reply.json(itemsJson(itemJson("lib-1")))
        }

        api.userViews("u1").success()

        assertEquals(3, jellyfin.requestsTo("GET", "/UserViews").size)
    }

    @Test
    fun aReadIsTriedAgainAfterAServerError() = runTest {
        var calls = 0
        server.on("GET", "/UserViews") {
            if (++calls == 1) Reply.status(HttpStatusCode.ServiceUnavailable) else Reply.json(itemsJson(itemJson("lib-1")))
        }

        api.userViews("u1").success()

        assertEquals(2, jellyfin.requestsTo("GET", "/UserViews").size)
    }

    @Test
    fun aReadGivesUpAfterThreeTriesWithTheLastFailure() = runTest {
        server.on("GET", "/UserViews", Reply.status(HttpStatusCode.ServiceUnavailable))

        assertEquals(ApiError.Server(503), api.userViews("u1").error())
        assertEquals(3, jellyfin.requestsTo("GET", "/UserViews").size)
    }

    @Test
    fun aMissingOrForbiddenReadIsNotRepeated() = runTest {
        server.on("GET", "/UserViews", Reply.status(HttpStatusCode.Forbidden))
        server.on("GET", "/Items", Reply.status(HttpStatusCode.NotFound))

        assertEquals(ApiError.Forbidden, api.userViews("u1").error())
        assertEquals(ApiError.NotFound, api.items("p", "u1", 0, 60).error())
        assertEquals(2, jellyfin.requests.size)
    }

    @Test
    fun aReadThatAnswersNonsenseIsNotRepeated() = runTest {
        server.on("GET", "/UserViews", Reply.json("<html>proxy page</html>"))

        assertEquals(ApiError.BadResponse, api.userViews("u1").error())
        assertEquals(1, jellyfin.requests.size)
    }

    @Test
    fun aRejectedTokenIsReportedOnceAndNotRepeated() = runTest(UnconfinedTestDispatcher()) {
        server.on("GET", "/UserViews", Reply.status(HttpStatusCode.Unauthorized))
        val heard = async { jellyfin.unauthorized.tokens.first() }

        assertEquals(ApiError.Unauthorized, api.userViews("u1").error())

        assertEquals("token-1", heard.await())
        assertEquals(1, jellyfin.requests.size)
    }

    @Test
    fun theTokenSurvivesAnotherTryAndAnotherApi() = runTest {
        var calls = 0
        server.on("GET", "/UserViews") {
            if (++calls == 1) throw ConnectionRefused()
            Reply.json(itemsJson(itemJson("lib-1")))
        }

        jellyfin.api(address).withToken("later").userViews("u1").success()

        assertEquals(2, jellyfin.requests.size)
        assertTrue(jellyfin.requests.all { it.authorization!!.contains("""Token="later"""") })
    }

    @Test
    fun signingInIsNeverRepeated() = runTest {
        server.on("POST", "/Users/AuthenticateByName", Reply.status(HttpStatusCode.ServiceUnavailable))
        server.on("POST", "/Users/AuthenticateWithQuickConnect") { throw ConnectionRefused() }

        assertEquals(ApiError.Server(503), jellyfin.api(address).authenticateByName("alex", "pw").error())
        assertEquals(ApiError.Network, jellyfin.api(address).authenticateWithQuickConnect("secret").error())
        assertEquals(2, jellyfin.requests.size)
    }

    @Test
    fun readsThatWereNotAskedToRetryDoNot() = runTest {
        server.on("GET", "/Users/Me") { throw ConnectionRefused() }

        assertEquals(ApiError.Network, api.me().error())
        assertEquals(1, jellyfin.requests.size)
    }

    @Test
    fun theWaitDoublesBetweenTries() = runTest {
        val slow = FakeJellyfin(RetryPolicy(attempts = 3, backoff = 100.milliseconds))
        val slowServer = slow.server(address)
        slowServer.on("GET", "/UserViews", Reply.status(HttpStatusCode.BadGateway))

        slow.api(address, "t").userViews("u1")

        // 100 ms before the second try and 200 ms before the third.
        assertEquals(300, testScheduler.currentTime)
    }

    @Test
    fun aPolicyNeedsAtLeastOneTry() {
        assertFailsWith<IllegalArgumentException> { RetryPolicy(attempts = 0, backoff = 1.milliseconds) }
    }

    @Test
    fun noRetriesMeansOneTry() = runTest {
        val once = FakeJellyfin(RetryPolicy.None)
        once.server(address).on("GET", "/UserViews", Reply.status(HttpStatusCode.BadGateway))

        once.api(address, "t").userViews("u1")

        assertEquals(1, once.requests.size)
    }
}
