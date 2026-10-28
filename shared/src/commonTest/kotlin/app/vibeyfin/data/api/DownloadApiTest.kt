package app.vibeyfin.data.api

import app.vibeyfin.testing.FakeJellyfin
import app.vibeyfin.testing.Reply
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadApiTest {
    private val address = "http://nas:8096"
    private val jellyfin = FakeJellyfin()
    private val server = jellyfin.server(address)
    private val api = jellyfin.api(address, token = "token-1")

    /** Collects what a download hands over. */
    private class Collector(private val failOnWrite: Exception? = null) : DownloadSink {
        var start: DownloadStart? = null
        val bytes = StringBuilder()

        override suspend fun begin(start: DownloadStart) {
            this.start = start
        }

        override suspend fun write(bytes: ByteArray, count: Int) {
            failOnWrite?.let { throw it }
            this.bytes.append(bytes.decodeToString(0, count))
        }
    }

    @Test
    fun theWholeFileIsHandedToTheSinkWithItsSize() = runTest {
        server.on("GET", "/Items/m1/Download", Reply.file("hello world", etag = "\"abc\""))
        val sink = Collector()

        val result = api.download("m1", offset = 0, sink = sink)

        assertIs<ApiResult.Success<Unit>>(result)
        assertEquals("hello world", sink.bytes.toString())
        assertEquals(11L, sink.start!!.totalBytes)
        assertEquals(true, sink.start!!.resumed)
        assertEquals("\"abc\"", sink.start!!.validator)
        val request = jellyfin.requestsTo("GET", "/Items/m1/Download").single()
        assertNull(request.range)
        assertTrue(request.authorization!!.contains("""Token="token-1""""))
    }

    @Test
    fun aResumeAsksForTheRestFromTheOffsetAndReadsTheWholeSizeFromContentRange() = runTest {
        server.on("GET", "/Items/m1/Download", Reply.partial("world", from = 6, total = 11))
        val sink = Collector()

        api.download("m1", offset = 6, sink = sink)

        assertEquals("bytes=6-", jellyfin.requestsTo("GET", "/Items/m1/Download").single().range)
        assertEquals("world", sink.bytes.toString())
        assertEquals(11L, sink.start!!.totalBytes)
        assertEquals(true, sink.start!!.resumed)
    }

    @Test
    fun aServerThatIgnoresTheRangeSendsTheWholeFileAndTheSinkIsToldToStartOver() = runTest {
        server.on("GET", "/Items/m1/Download", Reply.file("hello world"))
        val sink = Collector()

        api.download("m1", offset = 6, sink = sink)

        assertEquals(false, sink.start!!.resumed)
        assertEquals(11L, sink.start!!.totalBytes)
        assertEquals("hello world", sink.bytes.toString())
    }

    @Test
    fun withoutAnETagTheLastModifiedDateIdentifiesTheFile() = runTest {
        server.on("GET", "/Items/m1/Download") {
            Reply(HttpStatusCode.OK, "hi", "application/octet-stream", mapOf(HttpHeaders.ContentLength to "2", HttpHeaders.LastModified to "Fri, 23 Oct 2026 13:17:17 GMT"))
        }
        val sink = Collector()

        api.download("m1", offset = 0, sink = sink)

        assertEquals("Fri, 23 Oct 2026 13:17:17 GMT", sink.start!!.validator)
    }

    @Test
    fun withoutPermissionTheServerRefuses() = runTest {
        server.on("GET", "/Items/m1/Download", Reply.status(HttpStatusCode.Forbidden))
        val sink = Collector()

        val result = api.download("m1", offset = 0, sink = sink)

        assertEquals(ApiResult.Failure(ApiError.Forbidden), result)
        assertNull(sink.start)
    }

    @Test
    fun aRejectedTokenIsReportedLikeOnAnyOtherCall() = runTest {
        server.on("GET", "/Items/m1/Download", Reply.status(HttpStatusCode.Unauthorized))
        val seen = mutableListOf<String>()
        val job = launch(Dispatchers.Unconfined) { jellyfin.unauthorized.tokens.collect { seen += it } }

        val result = api.download("m1", offset = 0, sink = Collector())

        assertEquals(ApiResult.Failure(ApiError.Unauthorized), result)
        assertEquals(listOf("token-1"), seen)
        job.cancel()
    }

    @Test
    fun aMissingFileIsNotFound() = runTest {
        // No handler: the fake answers 404.
        assertEquals(ApiResult.Failure(ApiError.NotFound), api.download("gone", offset = 0, sink = Collector()))
    }

    @Test
    fun aSinkThatCannotWriteEndsTheDownloadWithItsOwnError() = runTest {
        server.on("GET", "/Items/m1/Download", Reply.file("hello"))
        val diskFull = IllegalStateException("No space left on device")

        val thrown = assertFailsWith<Exception> { api.download("m1", offset = 0, sink = Collector(failOnWrite = diskFull)) }

        assertEquals(diskFull, thrown.cause)
    }

    @Test
    fun noConnectionIsANetworkFailure() = runTest {
        server.refuseConnections()

        assertEquals(ApiResult.Failure(ApiError.Network), api.download("m1", offset = 0, sink = Collector()))
    }

    @Test
    fun theDownloadInfoCallsAskForTheMediaSources() = runTest {
        server.on("GET", "/Items", Reply.json("""{"Items":[{"Id":"m1","Name":"Heat","Type":"Movie","MediaSources":[{"Id":"ms1","Container":"mkv","Size":1234}]}],"TotalRecordCount":1}"""))

        val result = api.itemsForDownload("u1", listOf("m1")).let { assertIs<ApiResult.Success<ItemsResultDto>>(it).value }

        assertEquals(1234L, result.items.single().mediaSources.single().size)
        assertTrue(jellyfin.requestsTo("GET", "/Items").single().query["fields"]!!.contains("MediaSources"))
    }

    @Test
    fun offlineProgressIsPostedAsUserData() = runTest {
        server.on("POST", "/UserItems/e1/UserData", Reply.noContent())

        val result = api.updateUserData("u1", "e1", UpdateUserItemDataDto(playbackPositionTicks = 600, played = false, lastPlayedDate = "2026-10-23T18:00:00.0000000Z"))

        assertIs<ApiResult.Success<Unit>>(result)
        val request = jellyfin.requestsTo("POST", "/UserItems/e1/UserData").single()
        assertEquals("u1", request.query["userId"])
        assertTrue(request.body.contains(""""PlaybackPositionTicks":600"""))
        assertTrue(request.body.contains(""""LastPlayedDate":"2026-10-23T18:00:00.0000000Z""""))
    }

    @Test
    fun theUserPolicyTellsWhetherDownloadingIsAllowed() {
        val allowed = JellyfinJson.decodeFromString<UserDto>("""{"Id":"u1","Policy":{"EnableContentDownloading":false}}""")
        val unstated = JellyfinJson.decodeFromString<UserDto>("""{"Id":"u1","Policy":{}}""")

        assertEquals(false, allowed.policy!!.enableContentDownloading)
        assertEquals(true, unstated.policy!!.enableContentDownloading)
    }
}
