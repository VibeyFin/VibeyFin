package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.domain.model.ProfileRef
import com.liamgleeson.vibeyfin.testing.FakeJellyfin
import com.liamgleeson.vibeyfin.testing.Reply
import com.liamgleeson.vibeyfin.testing.TestApp
import com.liamgleeson.vibeyfin.testing.count
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The reporter works on its own scope in real time, like the app does, so these tests wait for the
 * fake server to see each call instead of using a virtual clock.
 */
class PlaybackReporterTest {
    private val app = TestApp()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val plan = PlaybackPlan(
        itemId = "m1",
        mediaSourceId = "ms-1",
        playSessionId = "ps-1",
        method = PlayMethod.DirectPlay,
        url = "http://nas:8096/Videos/m1/stream.mp4",
        headers = emptyMap(),
        runtimeMs = 5_400_000,
    )

    @AfterTest
    fun tearDown() = runTest {
        scope.cancel()
        app.close()
    }

    /** A reporter that only sends when told to: the interval is too long to ever fire. */
    private fun reporter(interval: Duration = 10.hours) = app.reporter(scope, interval).also { it.begin(plan) }

    private suspend fun serverAnsweringReports(): FakeJellyfin {
        val server = app.signedIn()
        server.on("POST", "/Sessions/Playing", Reply.noContent())
        server.on("POST", "/Sessions/Playing/Progress", Reply.noContent())
        server.on("POST", "/Sessions/Playing/Stopped", Reply.noContent())
        return app.jellyfin
    }

    private fun body(request: com.liamgleeson.vibeyfin.testing.RecordedRequest) = Json.parseToJsonElement(request.body).jsonObject

    @Test
    fun startingPlaybackReportsItWithItsPositionAndMethod() = runTest {
        val jellyfin = serverAnsweringReports()
        val reporter = reporter()

        reporter.playing(5_000)

        val start = body(jellyfin.awaitRequests("POST", "/Sessions/Playing").single())
        assertEquals("m1", start.getValue("ItemId").jsonPrimitive.content)
        assertEquals("ms-1", start.getValue("MediaSourceId").jsonPrimitive.content)
        assertEquals("ps-1", start.getValue("PlaySessionId").jsonPrimitive.content)
        assertEquals("DirectPlay", start.getValue("PlayMethod").jsonPrimitive.content)
        assertEquals(50_000_000L, start.getValue("PositionTicks").jsonPrimitive.long)
        assertEquals(false, start.getValue("IsPaused").jsonPrimitive.boolean)
        assertEquals(true, start.getValue("CanSeek").jsonPrimitive.boolean)
    }

    @Test
    fun theReportsAreSignedInLikeEveryOtherCall() = runTest {
        val jellyfin = serverAnsweringReports()

        reporter().playing(0)

        val sent = jellyfin.awaitRequests("POST", "/Sessions/Playing").single()
        assertTrue(sent.authorization.orEmpty().contains("Token=\"token-1\""), sent.authorization)
    }

    @Test
    fun pausingAndResumingAreReportedAtOnceAndDontStartAgain() = runTest {
        val jellyfin = serverAnsweringReports()
        val reporter = reporter()

        reporter.playing(1_000)
        reporter.paused(12_000)
        reporter.playing(12_000)
        val progress = jellyfin.awaitRequests("POST", "/Sessions/Playing/Progress", 2).map(::body)

        assertEquals(listOf(true, false), progress.map { it.getValue("IsPaused").jsonPrimitive.boolean })
        assertEquals(listOf(120_000_000L, 120_000_000L), progress.map { it.getValue("PositionTicks").jsonPrimitive.long })
        assertEquals(1, jellyfin.requestsTo("POST", "/Sessions/Playing").size)
    }

    @Test
    fun aSeekIsReportedWithTheNewPosition() = runTest {
        val jellyfin = serverAnsweringReports()
        val reporter = reporter()

        reporter.playing(0)
        reporter.seeked(600_000)

        val progress = body(jellyfin.awaitRequests("POST", "/Sessions/Playing/Progress").single())
        assertEquals(6_000_000_000L, progress.getValue("PositionTicks").jsonPrimitive.long)
        assertEquals(false, progress.getValue("IsPaused").jsonPrimitive.boolean)
    }

    @Test
    fun aSeekBeforePlaybackStartedSendsNothing() = runTest {
        val jellyfin = serverAnsweringReports()
        val reporter = reporter()

        reporter.seeked(60_000)
        reporter.paused(60_000)
        reporter.stop(60_000)
        reporter.playing(60_000)

        jellyfin.awaitRequests("POST", "/Sessions/Playing")
        assertEquals(listOf("POST /Sessions/Playing"), jellyfin.callLog().filter { it.startsWith("POST /Sessions") })
    }

    @Test
    fun stoppingReportsTheLastPositionOnce() = runTest {
        val jellyfin = serverAnsweringReports()
        val reporter = reporter()

        reporter.playing(0)
        reporter.position(30_000)
        reporter.stop(31_500)
        reporter.stop(31_500)

        val stopped = body(jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped").single())
        assertEquals("ps-1", stopped.getValue("PlaySessionId").jsonPrimitive.content)
        assertEquals(315_000_000L, stopped.getValue("PositionTicks").jsonPrimitive.long)
        assertEquals(false, stopped.getValue("Failed").jsonPrimitive.boolean)
        assertEquals(1, jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").size)
    }

    @Test
    fun aStopBecauseTheStreamFailedSaysSo() = runTest {
        val jellyfin = serverAnsweringReports()
        val reporter = reporter()

        reporter.playing(0)
        reporter.stop(2_000, failed = true)

        val stopped = body(jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped").single())
        assertEquals(true, stopped.getValue("Failed").jsonPrimitive.boolean)
    }

    @Test
    fun theCallsArriveInTheOrderTheyWereMade() = runTest {
        val jellyfin = serverAnsweringReports()
        val reporter = reporter()

        reporter.playing(0)
        reporter.paused(5_000)
        reporter.playing(5_000)
        reporter.seeked(9_000)
        reporter.stop(9_500)
        jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped")

        assertEquals(
            listOf(
                "POST /Sessions/Playing",
                "POST /Sessions/Playing/Progress",
                "POST /Sessions/Playing/Progress",
                "POST /Sessions/Playing/Progress",
                "POST /Sessions/Playing/Stopped",
            ),
            jellyfin.callLog().filter { it.startsWith("POST /Sessions") },
        )
    }

    @Test
    fun playingAfterAStopStartsTheSessionAgain() = runTest {
        // The app goes to the background (stop), then comes back and plays on.
        val jellyfin = serverAnsweringReports()
        val reporter = reporter()

        reporter.playing(0)
        reporter.stop(20_000)
        reporter.playing(20_000)

        val starts = jellyfin.awaitRequests("POST", "/Sessions/Playing", 2).map(::body)
        assertEquals(listOf(0L, 200_000_000L), starts.map { it.getValue("PositionTicks").jsonPrimitive.long })
        assertEquals(1, jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").size)
    }

    @Test
    fun aNewStreamIsReportedUnderItsOwnPlaySession() = runTest {
        // The transcode fallback: the first stream stops as failed, the second starts a new session.
        val jellyfin = serverAnsweringReports()
        val reporter = reporter()

        reporter.playing(0)
        reporter.stop(4_000, failed = true)
        reporter.begin(plan.copy(playSessionId = "ps-2", method = PlayMethod.Transcode))
        reporter.playing(4_000)

        val starts = jellyfin.awaitRequests("POST", "/Sessions/Playing", 2).map(::body)
        assertEquals(listOf("ps-1", "ps-2"), starts.map { it.getValue("PlaySessionId").jsonPrimitive.content })
        assertEquals("Transcode", starts[1].getValue("PlayMethod").jsonPrimitive.content)
    }

    @Test
    fun progressIsSentOnTheIntervalWhilePlayingAndNotWhilePaused() = runTest {
        val jellyfin = serverAnsweringReports()
        val reporter = reporter(interval = 40.milliseconds)

        reporter.playing(0)
        reporter.position(7_000)
        val ticks = jellyfin.awaitRequests("POST", "/Sessions/Playing/Progress", 3).map(::body)
        assertTrue(ticks.all { it.getValue("IsPaused").jsonPrimitive.boolean.not() })
        assertEquals(70_000_000L, ticks.last().getValue("PositionTicks").jsonPrimitive.long)

        reporter.paused(7_000)
        jellyfin.awaitRequests("POST", "/Sessions/Playing/Progress", ticks.size + 1) // the pause itself
        withContext(Dispatchers.Default) { delay(200) }
        val settled = jellyfin.requestsTo("POST", "/Sessions/Playing/Progress").size
        withContext(Dispatchers.Default) { delay(200) }

        assertEquals(settled, jellyfin.requestsTo("POST", "/Sessions/Playing/Progress").size)
    }

    @Test
    fun stoppedStillGoesOutWhenTheReporterIsClosedRightAfter() = runTest {
        // What happens when the screen goes away: the ViewModel stops, closes, and is gone.
        val jellyfin = serverAnsweringReports()
        val reporter = reporter()

        reporter.playing(0)
        reporter.stop(8_000)
        reporter.close()

        jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped")
    }

    @Test
    fun aFailedStoppedIsTriedOnceMore() = runTest {
        val jellyfin = serverAnsweringReports()
        var calls = 0
        app.jellyfin.server("http://nas:8096").on("POST", "/Sessions/Playing/Stopped") {
            if (++calls == 1) Reply.status(HttpStatusCode.ServiceUnavailable) else Reply.noContent()
        }
        val reporter = reporter()

        reporter.playing(0)
        reporter.stop(1_000)

        jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped", 2)
        withContext(Dispatchers.Default) { delay(100) }
        assertEquals(2, jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").size)
    }

    @Test
    fun aFailedProgressIsDroppedAndPlaybackReportsOnNormally() = runTest {
        val jellyfin = serverAnsweringReports()
        var calls = 0
        app.jellyfin.server("http://nas:8096").on("POST", "/Sessions/Playing/Progress") {
            if (++calls == 1) Reply.status(HttpStatusCode.ServiceUnavailable) else Reply.noContent()
        }
        val reporter = reporter()

        reporter.playing(0)
        reporter.paused(1_000)
        reporter.playing(2_000)

        jellyfin.awaitRequests("POST", "/Sessions/Playing/Progress", 2)
        reporter.stop(3_000)
        jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped")
        // Only the two progress reports were made: the failed one was not repeated.
        assertEquals(2, jellyfin.requestsTo("POST", "/Sessions/Playing/Progress").size)
    }

    @Test
    fun withNobodySignedInNothingIsSentAndNothingCrashes() = runTest {
        val reporter = reporter()

        reporter.playing(0)
        reporter.stop(1_000)
        reporter.close()
        withContext(Dispatchers.Default) { delay(100) }

        assertTrue(app.jellyfin.requests.isEmpty())
        assertEquals(0, app.database.count("pending_reports"))
    }

    @Test
    fun switchingTracksInTheSameSessionIsReportedAtOnceWithTheNewTracks() = runTest {
        val jellyfin = serverAnsweringReports()
        val reporter = app.reporter(scope, 10.hours).also { it.begin(plan.copy(audioStreamIndex = 1, subtitleStreamIndex = null)) }

        reporter.playing(0)
        reporter.position(15_000)
        reporter.begin(plan.copy(audioStreamIndex = 2, subtitleStreamIndex = 4))

        val progress = body(jellyfin.awaitRequests("POST", "/Sessions/Playing/Progress").single())
        assertEquals(2, progress.getValue("AudioStreamIndex").jsonPrimitive.int)
        assertEquals(4, progress.getValue("SubtitleStreamIndex").jsonPrimitive.int)
        assertEquals(150_000_000L, progress.getValue("PositionTicks").jsonPrimitive.long)
    }

    @Test
    fun theSameTracksAgainOrANewSessionSendNothingExtra() = runTest {
        val jellyfin = serverAnsweringReports()
        val reporter = app.reporter(scope, 10.hours).also { it.begin(plan.copy(audioStreamIndex = 1)) }

        reporter.playing(0)
        reporter.begin(plan.copy(audioStreamIndex = 1))
        reporter.begin(plan.copy(playSessionId = "ps-2", audioStreamIndex = 2))
        reporter.stop(1_000)
        jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped")

        assertEquals(0, jellyfin.requestsTo("POST", "/Sessions/Playing/Progress").size)
    }

    @Test
    fun theReportsNameTheTracksOfTheStream() = runTest {
        val jellyfin = serverAnsweringReports()
        val reporter = app.reporter(scope, 10.hours).also { it.begin(plan.copy(audioStreamIndex = 2, subtitleStreamIndex = 3)) }

        reporter.playing(0)

        val start = body(jellyfin.awaitRequests("POST", "/Sessions/Playing").single())
        assertEquals(2, start.getValue("AudioStreamIndex").jsonPrimitive.int)
        assertEquals(3, start.getValue("SubtitleStreamIndex").jsonPrimitive.int)
    }

    private suspend fun awaitSavedSessions(expected: Int) {
        repeat(500) {
            if (app.database.count("pending_reports") == expected) return
            withContext(Dispatchers.Default) { delay(10) }
        }
        assertEquals(expected, app.database.count("pending_reports"))
    }

    @Test
    fun aSessionIsSavedWhileItPlaysAndForgottenOnceTheServerHasHeardItStopped() = runTest {
        val jellyfin = serverAnsweringReports()
        val reporter = reporter()

        reporter.playing(0)
        jellyfin.awaitRequests("POST", "/Sessions/Playing")
        assertEquals(1, app.database.count("pending_reports"))

        reporter.stop(9_000)
        awaitSavedSessions(0)
        assertEquals(1, jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").size)
    }

    @Test
    fun theSavedSessionFollowsPlaybackAndNamesTheProfileThatStartedIt() = runTest {
        val jellyfin = serverAnsweringReports()
        val reporter = reporter(interval = 40.milliseconds)

        reporter.playing(0)
        reporter.position(7_000)
        jellyfin.awaitRequests("POST", "/Sessions/Playing/Progress", 2)

        val saved = app.pending.stops().single()
        assertEquals(ProfileRef("srv-1", "u1"), saved.profile)
        assertEquals("ps-1", saved.playSessionId)
        assertEquals(70_000_000L, saved.positionTicks)
    }

    @Test
    fun aStoppedTheServerCouldNotTakeStaysSavedForTheNextStart() = runTest {
        val jellyfin = serverAnsweringReports()
        jellyfin.server("http://nas:8096").on("POST", "/Sessions/Playing/Stopped", Reply.status(HttpStatusCode.ServiceUnavailable))
        val reporter = reporter()

        reporter.playing(0)
        reporter.stop(31_500)

        jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped", 2)
        withContext(Dispatchers.Default) { delay(100) }
        val saved = app.pending.stops().single()
        assertEquals(315_000_000L, saved.positionTicks)
        assertEquals(2, jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").size)
    }

    @Test
    fun stoppedIsSentAsTheProfileThatStartedItEvenAfterSwitchingProfile() = runTest {
        val jellyfin = serverAnsweringReports()
        val reporter = reporter()

        reporter.playing(0)
        jellyfin.awaitRequests("POST", "/Sessions/Playing")
        app.connection.switchProfile()
        reporter.stop(12_000)

        val stopped = jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped").single()
        assertTrue(stopped.authorization.orEmpty().contains("Token=\"token-1\""), stopped.authorization)
    }

    @Test
    fun screensAreToldOnlyAfterTheServerHasBeenToldItStopped() = runTest {
        val jellyfin = serverAnsweringReports()
        val reporter = reporter()
        val announced = CompletableDeferred<Unit>()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            app.watchChanges.events.first()
            announced.complete(Unit)
        }

        reporter.playing(0)
        reporter.stop(1_000)
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { announced.await() } }

        assertEquals(1, jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").size)
    }
}
