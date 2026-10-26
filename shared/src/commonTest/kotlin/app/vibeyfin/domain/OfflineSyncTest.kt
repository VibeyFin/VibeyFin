package app.vibeyfin.domain

import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.downloaded
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What is watched on the device: how it is recorded, and how it reaches the server. */
class OfflineSyncTest {
    private val app = TestApp()
    private val alex = ProfileRef("srv-1", "u1")

    @AfterTest
    fun tearDown() = runTest { app.close() }

    /** The server's record of an item: where, whether finished, and when last watched (null: never). */
    private fun serverItem(id: String, position: Long = 0, played: Boolean = false, lastPlayed: String? = null) =
        """{"Items":[{"Id":"$id","Name":"X","Type":"Movie","UserData":{"PlaybackPositionTicks":$position,"Played":$played,"LastPlayedDate":${lastPlayed?.let { "\"$it\"" } ?: "null"}}}],"TotalRecordCount":1}"""

    private suspend fun server(item: String = serverItem("m1")): FakeServer {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(item))
        server.on("POST", "/UserItems/m1/UserData", Reply.noContent())
        server.on("POST", "/UserPlayedItems/m1", Reply.json("""{"Played":true}"""))
        return server
    }

    // Recording.

    @Test
    fun stoppingInTheMiddleKeepsThePlace() = runTest {
        server()
        app.downloaded("m1", runtimeMs = 100_000)

        val record = app.offlineProgress.record(alex, "m1", positionMs = 40_000, runtimeMs = 100_000)

        assertEquals(400_000_000L, record.positionTicks)
        assertFalse(record.played)
    }

    @Test
    fun stoppingNearTheEndMeansWatchedAndNoPlace() = runTest {
        server()
        app.downloaded("m1", runtimeMs = 100_000)

        val record = app.offlineProgress.record(alex, "m1", positionMs = 95_000, runtimeMs = 100_000)

        assertTrue(record.played)
        assertEquals(0L, record.positionTicks)
    }

    @Test
    fun stoppingAtTheVeryStartKeepsNoPlace() = runTest {
        server()
        app.downloaded("m1", runtimeMs = 100_000)

        val record = app.offlineProgress.record(alex, "m1", positionMs = 3_000, runtimeMs = 100_000)

        assertEquals(0L, record.positionTicks)
        assertFalse(record.played)
    }

    @Test
    fun theRecordIsKeptForTheServerAndShownOnTheDeviceAtOnce() = runTest {
        server()
        app.downloaded("m1", runtimeMs = 100_000)
        app.clock.advance(kotlin.time.Duration.parse("5s"))

        app.offlineProgress.record(alex, "m1", positionMs = 40_000, runtimeMs = 100_000)

        val waiting = app.pending.progress(alex, "m1")!!
        assertEquals(400_000_000L, waiting.positionTicks)
        assertEquals(app.clock.now().toEpochMilliseconds(), waiting.watchedAt)
        assertEquals(40_000L, app.offlineLibrary.playable(alex, "m1")!!.item.resumePositionMs)
    }

    // Sending.

    @Test
    fun aPartWatchedItemSetsThePositionAndWhenItWasWatched() = runTest {
        server()
        app.downloaded("m1", runtimeMs = 100_000)
        app.offlineProgress.record(alex, "m1", positionMs = 40_000, runtimeMs = 100_000)

        assertTrue(app.offlineSync.syncAll())

        val body = app.jellyfin.requestsTo("POST", "/UserItems/m1/UserData").single().body
        assertTrue(body.contains(""""PlaybackPositionTicks":400000000"""), body)
        assertTrue(body.contains(""""Played":false"""), body)
        assertTrue(body.contains(""""LastPlayedDate":"""), body)
        assertNull(app.pending.progress(alex, "m1"), "sent, so forgotten")
    }

    @Test
    fun aFinishedItemIsMarkedWatchedTheOrdinaryWay() = runTest {
        server()
        app.downloaded("m1", runtimeMs = 100_000)
        app.offlineProgress.record(alex, "m1", positionMs = 99_000, runtimeMs = 100_000)

        assertTrue(app.offlineSync.syncAll())

        assertEquals(1, app.jellyfin.requestsTo("POST", "/UserPlayedItems/m1").size)
        assertEquals(0, app.jellyfin.requestsTo("POST", "/UserItems/m1/UserData").size)
        assertNull(app.pending.progress(alex, "m1"))
    }

    @Test
    fun aNewerRecordOnTheServerIsNotOverwritten() = runTest {
        // Watched on another device at a time after this one was: the server wins.
        server(serverItem("m1", position = 700_000_000, lastPlayed = "2099-01-01T00:00:00.0000000Z"))
        app.downloaded("m1", runtimeMs = 100_000)
        app.offlineProgress.record(alex, "m1", positionMs = 40_000, runtimeMs = 100_000)

        assertTrue(app.offlineSync.syncAll(), "nothing is left waiting: it was dropped")

        assertEquals(0, app.jellyfin.requestsTo("POST", "/UserItems/m1/UserData").size)
        assertEquals(0, app.jellyfin.requestsTo("POST", "/UserPlayedItems/m1").size)
        assertNull(app.pending.progress(alex, "m1"))
        // And the device takes the server's place as its own.
        assertEquals(70_000L, app.offlineLibrary.playable(alex, "m1")!!.item.resumePositionMs)
    }

    @Test
    fun anOlderRecordOnTheServerIsReplaced() = runTest {
        server(serverItem("m1", position = 700_000_000, lastPlayed = "2000-01-01T00:00:00.0000000Z"))
        app.downloaded("m1", runtimeMs = 100_000)
        app.offlineProgress.record(alex, "m1", positionMs = 40_000, runtimeMs = 100_000)

        app.offlineSync.syncAll()

        assertEquals(1, app.jellyfin.requestsTo("POST", "/UserItems/m1/UserData").size)
    }

    @Test
    fun withNoServerTheRecordWaits() = runTest {
        val server = server()
        app.downloaded("m1", runtimeMs = 100_000)
        app.offlineProgress.record(alex, "m1", positionMs = 40_000, runtimeMs = 100_000)
        server.refuseConnections()

        assertFalse(app.offlineSync.syncAll())

        assertEquals(400_000_000L, app.pending.progress(alex, "m1")!!.positionTicks)

        server.refuseConnections(false)
        assertTrue(app.offlineSync.syncAll())
        assertNull(app.pending.progress(alex, "m1"))
    }

    @Test
    fun aServerErrorKeepsTheRecordToo() = runTest {
        val server = server()
        app.downloaded("m1", runtimeMs = 100_000)
        app.offlineProgress.record(alex, "m1", positionMs = 40_000, runtimeMs = 100_000)
        server.on("POST", "/UserItems/m1/UserData", Reply.status(HttpStatusCode.ServiceUnavailable))

        assertFalse(app.offlineSync.syncAll())

        assertTrue(app.pending.progress(alex, "m1") != null)
    }

    @Test
    fun anItemThatIsGoneFromTheServerIsDropped() = runTest {
        server("""{"Items":[],"TotalRecordCount":0}""")
        app.downloaded("m1", runtimeMs = 100_000)
        app.offlineProgress.record(alex, "m1", positionMs = 40_000, runtimeMs = 100_000)

        assertTrue(app.offlineSync.syncAll())

        assertNull(app.pending.progress(alex, "m1"))
    }

    @Test
    fun theScreensAreToldWhenSomethingWasSent() = runTest {
        server()
        app.downloaded("m1", runtimeMs = 100_000)
        app.offlineProgress.record(alex, "m1", positionMs = 40_000, runtimeMs = 100_000)
        var told = 0
        val job = launch(Dispatchers.Unconfined) { app.watchChanges.events.collect { told++ } }

        app.offlineSync.syncAll()

        assertTrue(told >= 1)
        job.cancel()
    }

    // Bringing the server's record back.

    @Test
    fun onceOnlineTheServersRecordReplacesTheDevicesWhenNothingIsWaiting() = runTest {
        server(serverItem("m1", position = 300_000_000, lastPlayed = "2026-10-20T18:00:00.0000000Z"))
        app.downloaded("m1", positionMs = 10_000, runtimeMs = 100_000)

        app.offlineSync.syncAll()

        assertEquals(30_000L, app.offlineLibrary.playable(alex, "m1")!!.item.resumePositionMs)
    }

    @Test
    fun anItemWatchedElsewhereIsMarkedWatchedHere() = runTest {
        server(serverItem("m1", played = true, lastPlayed = "2026-10-20T18:00:00.0000000Z"))
        app.downloaded("m1", runtimeMs = 100_000)

        app.offlineSync.syncAll()

        assertTrue(app.offlineLibrary.playable(alex, "m1")!!.item.played)
    }

    @Test
    fun whatIsWaitingToBeSentIsNotOverwrittenByTheRefresh() = runTest {
        val server = server(serverItem("m1", position = 300_000_000))
        app.downloaded("m1", runtimeMs = 100_000)
        app.offlineProgress.record(alex, "m1", positionMs = 40_000, runtimeMs = 100_000)
        // The write fails, so the record stays; the refresh must not replace it with the server's older record.
        server.on("POST", "/UserItems/m1/UserData", Reply.status(HttpStatusCode.ServiceUnavailable))

        app.offlineSync.syncAll()

        assertEquals(40_000L, app.offlineLibrary.playable(alex, "m1")!!.item.resumePositionMs)
    }

    // When it runs.

    @Test
    fun aConnectionComingBackSendsWhatWaited() = runTest {
        server()
        app.downloaded("m1", runtimeMs = 100_000)
        app.offlineProgress.record(alex, "m1", positionMs = 40_000, runtimeMs = 100_000)
        app.network.set(NetworkState.None)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        app.offlineSync.start(scope, app.network)
        assertEquals(0, app.jellyfin.requestsTo("POST", "/UserItems/m1/UserData").size, "no connection, nothing sent")

        app.network.set(NetworkState.Unmetered)

        assertEquals(1, app.jellyfin.awaitRequests("POST", "/UserItems/m1/UserData").size)
        assertNull(app.pending.progress(alex, "m1"))
        scope.cancel()
    }

    @Test
    fun theOfflineModeSwitchDoesNotStopTheSending() = runTest {
        // The switch only says what the app shows; sending is about whether the server can be reached.
        server()
        app.downloaded("m1", runtimeMs = 100_000)
        app.offlineProgress.record(alex, "m1", positionMs = 40_000, runtimeMs = 100_000)
        app.offline.setManual(true)

        assertTrue(app.offlineSync.syncAll())
        assertEquals(1, app.jellyfin.requestsTo("POST", "/UserItems/m1/UserData").size)
        assertTrue(app.preferences.offlineMode.first())
    }
}
