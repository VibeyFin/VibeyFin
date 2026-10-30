package app.vibeyfin.domain

import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.count
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class PlaybackRecoveryTest {
    private val app = TestApp()

    private val alex = ProfileRef("srv-1", "u1")
    private val sam = ProfileRef("srv-1", "u2")

    @AfterTest
    fun tearDown() = runTest { app.close() }

    private fun stop(profile: ProfileRef = alex, session: String = "ps-1", ticks: Long = 315_000_000L) = PendingStop(profile, itemId = "m1", mediaSourceId = "ms-1", playSessionId = session, positionTicks = ticks, failed = false)

    // Alex is signed in; the server takes every `Stopped`.
    private suspend fun serverTakingStops(): FakeServer = app.signedIn().also { it.on("POST", "/Sessions/Playing/Stopped", Reply.noContent()) }

    private suspend fun savedSamToo() {
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
    }

    private suspend fun savedSessions() = app.database.count("pending_reports")

    @Test
    fun aSessionAnEarlierRunLeftBehindIsSentAndForgotten() = runTest {
        serverTakingStops()
        app.pending.save(stop(ticks = 315_000_000L))

        app.recovery.recoverOnStart()

        val sent = Json.parseToJsonElement(app.jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").single().body).jsonObject
        assertEquals("ps-1", sent.getValue("PlaySessionId").jsonPrimitive.content)
        assertEquals("m1", sent.getValue("ItemId").jsonPrimitive.content)
        assertEquals(315_000_000L, sent.getValue("PositionTicks").jsonPrimitive.long)
        assertEquals(0, savedSessions())
    }

    @Test
    fun itIsSentWithTheTokenOfTheProfileThatStartedItAndNobodyIsSignedOut() = runTest {
        serverTakingStops()
        savedSamToo()
        app.pending.save(stop(profile = sam))

        app.recovery.recoverOnStart()

        val request = app.jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").single()
        assertTrue(request.authorization.orEmpty().contains("Token=\"token-2\""), request.authorization)
        assertIs<ConnectionState.Connected>(app.connection.state.value)
        assertEquals(0, savedSessions())
    }

    @Test
    fun aLockedProfileHasNothingToSendWithSoItsSessionIsDropped() = runTest {
        serverTakingStops()
        savedSamToo()
        app.accounts.expire("srv-1", "u2")
        app.pending.save(stop(profile = sam))

        app.recovery.recoverOnStart()

        assertTrue(app.jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").isEmpty())
        assertEquals(0, savedSessions())
    }

    @Test
    fun aTokenTheServerRejectsDropsTheSessionAndLocksThatProfileOnly() = runTest {
        val server = serverTakingStops()
        savedSamToo()
        server.on("POST", "/Sessions/Playing/Stopped", Reply.status(HttpStatusCode.Unauthorized))
        app.pending.save(stop(profile = sam))

        app.recovery.recoverOnStart()

        assertEquals(0, savedSessions())
        assertFalse(app.accounts.account("srv-1", "u2")!!.hasSession)
        assertTrue(app.accounts.account("srv-1", "u1")!!.hasSession)
        assertIs<ConnectionState.Connected>(app.connection.state.value)
    }

    @Test
    fun theSignedInProfilesRejectedTokenEndsItsSessionLikeAnyOtherCall() = runTest {
        val server = serverTakingStops()
        server.on("POST", "/Sessions/Playing/Stopped", Reply.status(HttpStatusCode.Unauthorized))
        app.pending.save(stop(profile = alex))

        app.recovery.recoverOnStart()

        val state = withContext(Dispatchers.Default) { withTimeout(5.seconds) { app.connection.state.first { it is ConnectionState.Expired } } }
        assertEquals(ConnectionState.Expired("srv-1", "u1"), state)
        assertEquals(0, savedSessions())
    }

    @Test
    fun noConnectionKeepsTheSessionForTheNextStart() = runTest {
        val server = serverTakingStops()
        app.pending.save(stop())
        server.refuseConnections()

        app.recovery.recoverOnStart()

        assertEquals(1, savedSessions())
    }

    @Test
    fun aServerErrorKeepsTheSessionForTheNextStart() = runTest {
        val server = serverTakingStops()
        server.on("POST", "/Sessions/Playing/Stopped", Reply.status(HttpStatusCode.ServiceUnavailable))
        app.pending.save(stop())

        app.recovery.recoverOnStart()

        assertEquals(1, app.jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").size)
        assertEquals(1, savedSessions())
    }

    @Test
    fun anAnswerThatSaysNoEndsTheSessionBecauseAskingAgainChangesNothing() = runTest {
        val server = serverTakingStops()
        server.on("POST", "/Sessions/Playing/Stopped", Reply.status(HttpStatusCode.NotFound))
        app.pending.save(stop())

        app.recovery.recoverOnStart()

        assertEquals(0, savedSessions())
    }

    @Test
    fun aSessionOlderThanADayIsDroppedWithoutAskingAndNewerOnesStillGo() = runTest {
        serverTakingStops()
        app.pending.save(stop(session = "old"))
        app.clock.advance(25.hours)
        app.pending.save(stop(session = "new"))

        app.recovery.recoverOnStart()

        val sent = app.jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped")
        assertEquals(listOf("new"), sent.map { Json.parseToJsonElement(it.body).jsonObject.getValue("PlaySessionId").jsonPrimitive.content })
        assertEquals(0, savedSessions())
    }

    @Test
    fun everySessionIsSentOldestFirst() = runTest {
        serverTakingStops()
        listOf("a", "b", "c").forEach {
            app.pending.save(stop(session = it))
            app.clock.advance(1.hours)
        }

        app.recovery.recoverOnStart()

        val sessions = app.jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped")
            .map { Json.parseToJsonElement(it.body).jsonObject.getValue("PlaySessionId").jsonPrimitive.content }
        assertEquals(listOf("a", "b", "c"), sessions)
    }

    @Test
    fun savingASessionAgainMovesItsPositionAndAddsNoRow() = runTest {
        serverTakingStops()
        app.pending.save(stop(ticks = 1L))
        app.pending.save(stop(ticks = 2L))

        assertEquals(1, savedSessions())
        assertEquals(2L, app.pending.stops().single().positionTicks)
    }

    @Test
    fun aServerTakesItsSavedSessionsWithIt() = runTest {
        serverTakingStops()
        app.pending.save(stop())

        app.servers.delete("srv-1")

        assertEquals(0, savedSessions())
    }
}
