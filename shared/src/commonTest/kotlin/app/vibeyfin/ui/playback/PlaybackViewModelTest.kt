package app.vibeyfin.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.domain.DynamicRange
import app.vibeyfin.domain.LiveDiagnostics
import app.vibeyfin.domain.PlayMethod
import app.vibeyfin.testing.FakeJellyfin
import app.vibeyfin.testing.HLS_PATH
import app.vibeyfin.testing.RecordedRequest
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TICKS_PER_MINUTE
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.audioStreamJson
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import app.vibeyfin.testing.mediaSourceJson
import app.vibeyfin.testing.playbackInfoJson
import app.vibeyfin.testing.segmentJson
import app.vibeyfin.testing.segmentsJson
import app.vibeyfin.testing.subtitleStreamJson
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.subtitles.SubtitleAdded
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()

    // The reporter outlives the ViewModel, as in the app, so it has a scope of its own.
    private val reporterScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        // Wait for the reporters too: one still writing to the database as it closes crashes the JVM.
        reporterScope.coroutineContext.job.cancelAndJoin()
        Dispatchers.resetMain()
        app.close()
    }

    // The waits before the tries at a stream that lost its connection, short so the tests don't wait.
    private val retryDelays = listOf(1.milliseconds, 1.milliseconds)

    private fun viewModel(
        id: String = "m1",
        startOver: Boolean = false,
        autoAdvance: Boolean = true,
        countdownStep: Duration = 1.hours,
    ) = viewModels.track(
        PlaybackViewModel(
            id, app.library, app.playback, app.reporter(reporterScope), app.connection, app.watch, app.profileSettings, app.binge,
            app.offlineLibrary, app.offline, app.localReporter(reporterScope), app.subtitles,
            retryDelays, startOver, autoAdvance, countdownStep,
        ),
    )

    // A signed-in server that offers a direct play of "Heat", answers the reports and, for an episode test, can be given another item.
    private suspend fun server(
        info: String = playbackInfoJson(mediaSourceJson()),
        item: String = itemJson("m1", "Heat", year = 1995),
        segments: Reply? = null,
    ): FakeJellyfin {
        val server = app.signedIn()
        if (segments != null) server.on("GET", "/MediaSegments/m1", segments)
        server.on("GET", "/Items", Reply.json(itemsJson(item)))
        server.on("POST", "/Items/m1/PlaybackInfo", Reply.json(info))
        server.on("POST", "/Sessions/Playing", Reply.noContent())
        server.on("POST", "/Sessions/Playing/Progress", Reply.noContent())
        server.on("POST", "/Sessions/Playing/Stopped", Reply.noContent())
        return app.jellyfin
    }

    private fun bodyOf(request: RecordedRequest) = Json.parseToJsonElement(request.body).jsonObject

    private val noLive = LiveDiagnostics(null, null, null, null, null, null, null, null, null)

    @Test
    fun `diagnostics start from the plan`() = runTest {
        server(info = playbackInfoJson(mediaSourceJson(videoWidth = 1920, videoHeight = 1080, streams = listOf(audioStreamJson(1, codec = "eac3")), defaultAudio = 1)))

        val diagnostics = assertNotNull(viewModel().uiState.first { it.diagnostics != null }.diagnostics)

        assertEquals(PlayMethod.DirectPlay, diagnostics.method)
        assertEquals("h264", diagnostics.videoCodec)
        assertEquals("eac3", diagnostics.audioCodec)
        assertEquals(1920, diagnostics.width)
        assertEquals(1080, diagnostics.height)
        assertEquals(emptyList(), diagnostics.transcodeReasons)
    }

    @Test
    fun `a transcode lists the reasons from the stream URL`() = runTest {
        server(
            info = playbackInfoJson(
                mediaSourceJson(directPlay = false, directStream = false, transcodingUrl = "$HLS_PATH&TranscodeReasons=VideoCodecNotSupported,AudioCodecNotSupported"),
            ),
        )

        val diagnostics = assertNotNull(viewModel().uiState.first { it.diagnostics != null }.diagnostics)

        assertEquals(PlayMethod.Transcode, diagnostics.method)
        assertEquals(listOf("VideoCodecNotSupported", "AudioCodecNotSupported"), diagnostics.transcodeReasons)
    }

    @Test
    fun `live values merge without losing the plan's`() = runTest {
        server(info = playbackInfoJson(mediaSourceJson(videoWidth = 1920, videoHeight = 1080)))
        val viewModel = viewModel()
        viewModel.uiState.first { it.diagnostics != null }

        viewModel.onDiagnostics(noLive.copy(droppedFrames = 4, throughputKbps = 9000, width = 1280))
        val diagnostics = assertNotNull(viewModel.uiState.first { it.diagnostics?.droppedFrames == 4 }.diagnostics)

        assertEquals(1280, diagnostics.width)
        assertEquals(1080, diagnostics.height)
        assertEquals("h264", diagnostics.videoCodec)
        assertEquals(9000, diagnostics.throughputKbps)
        assertEquals(PlayMethod.DirectPlay, diagnostics.method)
    }

    @Test
    fun `toggling the stats in the player flips the profile setting`() = runTest {
        server()
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }

        viewModel.onToggleDiagnostics()
        assertTrue(viewModel.uiState.first { it.showDiagnostics }.showDiagnostics)
        assertEquals(true, app.profileSettings.showPlaybackStats.first())

        viewModel.onToggleDiagnostics()
        assertEquals(false, viewModel.uiState.first { !it.showDiagnostics }.showDiagnostics)
        assertEquals(false, app.profileSettings.showPlaybackStats.first())
    }

    @Test
    fun `showDiagnostics follows the profile setting`() = runTest {
        server()
        val viewModel = viewModel()
        assertEquals(false, viewModel.uiState.first { it.stream != null }.showDiagnostics)

        app.profileSettings.setShowPlaybackStats(true)
        assertTrue(viewModel.uiState.first { it.showDiagnostics }.showDiagnostics)

        app.profileSettings.setShowPlaybackStats(false)
        assertEquals(false, viewModel.uiState.first { !it.showDiagnostics }.showDiagnostics)
    }

    @Test
    fun `bufferAheadSeconds follows the profile setting`() = runTest {
        server()
        val viewModel = viewModel()
        assertEquals(20, viewModel.uiState.first { it.stream != null }.bufferAheadSeconds)

        app.profileSettings.setBufferAheadSeconds(50)
        assertEquals(50, viewModel.uiState.first { it.bufferAheadSeconds == 50 }.bufferAheadSeconds)
    }

    @Test
    fun `pictureInPicture follows the profile setting`() = runTest {
        server()
        val viewModel = viewModel()
        assertEquals(true, viewModel.uiState.first { it.stream != null }.pictureInPicture)

        app.profileSettings.setPictureInPicture(false)
        assertEquals(false, viewModel.uiState.first { !it.pictureInPicture }.pictureInPicture)
    }

    @Test
    fun `swipeGestures follows the profile setting`() = runTest {
        server()
        val viewModel = viewModel()
        assertEquals(true, viewModel.uiState.first { it.stream != null }.swipeGestures)

        app.profileSettings.setSwipeGestures(false)
        assertEquals(false, viewModel.uiState.first { !it.swipeGestures }.swipeGestures)
    }

    @Test
    fun `subtitle style follows the profile settings`() = runTest {
        server()
        val viewModel = viewModel()
        val initial = viewModel.uiState.first { it.stream != null }
        assertEquals(true, initial.captionsFollowSystem)
        assertEquals(1, initial.subtitleSize)
        assertEquals(0, initial.subtitleTextColor)
        assertEquals(1, initial.subtitleBackground)

        app.profileSettings.setCaptionsFollowSystem(false)
        app.profileSettings.setSubtitleSize(3)
        app.profileSettings.setSubtitleTextColor(2)
        app.profileSettings.setSubtitleBackground(2)
        val changed = viewModel.uiState.first { it.subtitleSize == 3 && it.subtitleTextColor == 2 && it.subtitleBackground == 2 }
        assertEquals(false, changed.captionsFollowSystem)
    }

    @Test
    fun `missing values stay null`() = runTest {
        server()

        val diagnostics = assertNotNull(viewModel().uiState.first { it.diagnostics != null }.diagnostics)

        assertNull(diagnostics.width)
        assertNull(diagnostics.audioCodec)
        assertNull(diagnostics.droppedFrames)
        assertNull(diagnostics.throughputKbps)
        assertNull(diagnostics.bufferMs)
    }

    @Test
    fun isLoadingUntilTheServerHasAnswered() {
        assertTrue(PlaybackUiState().loading)
        assertNull(PlaybackUiState().stream)
    }

    @Test
    fun offersTheStreamTheServerChose() = runTest {
        server()

        val state = viewModel().uiState.first { it.stream != null }

        val stream = assertNotNull(state.stream)
        assertTrue(stream.url.startsWith("http://nas:8096/Videos/m1/stream.mp4?static=true"), stream.url)
        assertTrue(stream.headers.getValue("Authorization").contains("Token=\"token-1\""))
        assertEquals(0L, stream.startPositionMs)
        assertEquals(false, state.loading)
        assertNull(state.message)
    }

    @Test
    fun showsTheTitleOfAMovie() = runTest {
        server()

        val state = viewModel().uiState.first { it.stream != null && it.title.isNotEmpty() }

        assertEquals("Heat", state.title)
        assertNull(state.subtitle)
    }

    @Test
    fun carriesAPictureOfTheItemForTheNowPlayingCard() = runTest {
        server(item = itemJson("m1", "Heat", primaryTag = "p1", backdropTag = "b1"))

        val withArtwork = viewModel().uiState.first { it.title.isNotEmpty() }

        assertEquals(true, withArtwork.artworkUrl?.contains("/Items/m1/Images/"))
    }

    @Test
    fun hasNoArtworkWhenTheItemHasNoPicture() = runTest {
        server(item = itemJson("m1", "Heat"))

        assertNull(viewModel().uiState.first { it.title.isNotEmpty() }.artworkUrl)
    }

    @Test
    fun showsTheShowAndEpisodeForAnEpisode() = runTest {
        server(
            item = itemJson("m1", "Northbound", type = "Episode", seriesName = "Paper Harbour", seasonNumber = 2, episodeNumber = 4),
        )

        val state = viewModel().uiState.first { it.title.isNotEmpty() }

        assertEquals("Northbound", state.title)
        assertEquals("Paper Harbour  ·  S2 E4", state.subtitle)
    }

    @Test
    fun saysSoWhenTheServerHasNoWayToPlayIt() = runTest {
        server(info = playbackInfoJson(mediaSourceJson(directPlay = false, directStream = false), errorCode = "NoCompatibleStream"))

        val state = viewModel().uiState.first { !it.loading }

        assertNull(state.stream)
        assertEquals(LoadMessages.NO_COMPATIBLE_STREAM, state.message)
    }

    @Test
    fun aFailureToReachTheServerCanBeRetried() = runTest {
        val jellyfin = server()
        app.jellyfin.server("http://nas:8096").refuseConnections()
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.first { !it.loading }.message != null)

        app.jellyfin.server("http://nas:8096").refuseConnections(false)
        viewModel.onRetry()
        val state = viewModel.uiState.first { it.stream != null }

        assertNull(state.message)
        assertTrue(jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo").isNotEmpty())
    }

    @Test
    fun aRejectedTokenSendsTheUserBackToWhoIsWatching() = runTest {
        val jellyfin = server()
        app.jellyfin.server("http://nas:8096").on("POST", "/Items/m1/PlaybackInfo", Reply.status(HttpStatusCode.Unauthorized))

        val state = viewModel().uiState.first { it.exit != null }

        assertEquals(HomeExit.WhoIsWatching("srv-1"), state.exit)
        assertEquals(1, jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo").size)
    }

    @Test
    fun passesWhatThePlayerDoesOnToTheServer() = runTest {
        val jellyfin = server()
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }

        viewModel.onPlaying(0)
        viewModel.onPaused(40_000)
        viewModel.onPlaying(40_000)
        viewModel.onSeeked(90_000)
        viewModel.onClosed(95_000)

        val stopped = bodyOf(jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped").single())
        assertEquals(950_000_000L, stopped.getValue("PositionTicks").jsonPrimitive.long)
        assertEquals("ms-1", stopped.getValue("MediaSourceId").jsonPrimitive.content)
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
    fun theReportsNameTheStreamThatWasChosen() = runTest {
        val jellyfin = server()
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }

        viewModel.onPlaying(0)

        val start = bodyOf(jellyfin.awaitRequests("POST", "/Sessions/Playing").single())
        assertEquals("m1", start.getValue("ItemId").jsonPrimitive.content)
        assertEquals("ps-1", start.getValue("PlaySessionId").jsonPrimitive.content)
        assertEquals("DirectPlay", start.getValue("PlayMethod").jsonPrimitive.content)
    }

    @Test
    fun reachingTheEndStopsPlaybackAndClosesTheScreen() = runTest {
        val jellyfin = server()
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }

        viewModel.onPlaying(0)
        viewModel.onEnded(5_400_000)

        val state = viewModel.uiState.first { it.finished }
        assertTrue(state.finished)
        val stopped = bodyOf(jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped").single())
        assertEquals(54_000_000_000L, stopped.getValue("PositionTicks").jsonPrimitive.long)
    }

    @Test
    fun goingToTheBackgroundStopsTheReportAndPlayingAgainStartsItAgain() = runTest {
        val jellyfin = server()
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }

        viewModel.onPlaying(10_000)
        viewModel.onBackgrounded(30_000)
        viewModel.onPlaying(30_000)

        jellyfin.awaitRequests("POST", "/Sessions/Playing", 2)
        assertEquals(1, jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").size)
        // Still the same stream: the player kept its place, nothing was negotiated again.
        assertEquals(1, jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo").size)
    }

    @Test
    fun aDirectPlayThatFailsFallsBackToATranscodeFromWhereItWas() = runTest {
        val jellyfin = server()
        val viewModel = viewModel()
        val first = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)
        viewModel.onPlaying(0)
        viewModel.onPosition(61_000)

        // The server now offers a transcode, as it does when asked not to direct play.
        app.jellyfin.server("http://nas:8096").on(
            "POST",
            "/Items/m1/PlaybackInfo",
            Reply.json(playbackInfoJson(mediaSourceJson(id = "ms-1", directPlay = false, directStream = false, transcodingUrl = HLS_PATH), playSessionId = "ps-2")),
        )
        viewModel.onFailed(PlaybackFailure.Unsupported)

        val second = assertNotNull(viewModel.uiState.first { it.hasStreamOtherThan(first.id) }.stream)
        assertEquals("http://nas:8096$HLS_PATH", second.url)
        assertEquals(61_000L, second.startPositionMs)
        assertNotEquals(first.id, second.id)
        val stopped = bodyOf(jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped").single())
        assertEquals(true, stopped.getValue("Failed").jsonPrimitive.boolean)
        val negotiations = jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo")
        assertEquals(2, negotiations.size)
        assertEquals(false, bodyOf(negotiations.last()).getValue("EnableDirectPlay").jsonPrimitive.boolean)

        viewModel.onPlaying(61_000)
        val restarted = jellyfin.awaitRequests("POST", "/Sessions/Playing", 2).map(::bodyOf)
        assertEquals(listOf("ps-1", "ps-2"), restarted.map { it.getValue("PlaySessionId").jsonPrimitive.content })
        assertEquals("Transcode", restarted.last().getValue("PlayMethod").jsonPrimitive.content)
    }

    @Test
    fun `a file only a video reencode can play transcodes without any setting`() = runTest {
        server(
            info = playbackInfoJson(
                mediaSourceJson(directPlay = false, directStream = false, transcodingUrl = "$HLS_PATH&TranscodeReasons=VideoCodecNotSupported"),
            ),
        )

        val state = viewModel().uiState.first { it.stream != null }

        assertNull(state.message)
        assertEquals(PlayMethod.Transcode, state.diagnostics?.method)
    }

    @Test
    fun aTranscodeThatFailsIsReportedToTheUserInsteadOfLooping() = runTest {
        val jellyfin = server(
            info = playbackInfoJson(mediaSourceJson(directPlay = false, directStream = false, transcodingUrl = HLS_PATH)),
        )
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }
        viewModel.onPlaying(0)

        viewModel.onFailed(PlaybackFailure.Unsupported)

        val state = viewModel.uiState.first { it.message != null }
        assertEquals(LoadMessages.PLAYBACK_FAILED, state.message)
        assertNull(state.stream)
        assertEquals(1, jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo").size)
    }

    @Test
    fun aFailureBeforeAnyStreamIsIgnored() = runTest {
        val jellyfin = server()
        app.jellyfin.server("http://nas:8096").refuseConnections()
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }
        val negotiated = jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo").size

        PlaybackFailure.entries.forEach(viewModel::onFailed)

        assertEquals(negotiated, jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo").size)
        assertNull(viewModel.uiState.value.stream)
    }

    // -- failures --

    @Test
    fun aLostConnectionTriesTheSameStreamAgainFromWhereItWas() = runTest {
        val jellyfin = server()
        val viewModel = viewModel()
        val first = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)
        viewModel.onPlaying(0)
        viewModel.onPosition(61_000)

        viewModel.onFailed(PlaybackFailure.Network)

        val second = assertNotNull(viewModel.uiState.first { it.hasStreamOtherThan(first.id) }.stream)
        assertEquals(first.url, second.url)
        assertEquals(first.headers, second.headers)
        assertEquals(61_000L, second.startPositionMs)
        // Nothing was negotiated again, so the file is still played as it was, not transcoded.
        assertEquals(1, negotiations().size)
        // The server is told the video is paused while it waits, and the play session carries on.
        val progress = bodyOf(jellyfin.awaitRequests("POST", "/Sessions/Playing/Progress").first())
        assertEquals(true, progress.getValue("IsPaused").jsonPrimitive.boolean)
        assertTrue(jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").isEmpty())
    }

    @Test
    fun aTranscodeThatLosesItsConnectionIsTriedAgainToo() = runTest {
        server(info = playbackInfoJson(mediaSourceJson(directPlay = false, directStream = false, transcodingUrl = HLS_PATH)))
        val viewModel = viewModel()
        val first = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)

        viewModel.onFailed(PlaybackFailure.Network)

        val second = assertNotNull(viewModel.uiState.first { it.hasStreamOtherThan(first.id) }.stream)
        assertEquals(first.url, second.url)
        assertEquals(1, negotiations().size)
    }

    @Test
    fun waitingToTryAgainShowsTheSpinnerAndNoError() = runTest {
        server()
        val viewModel = viewModel()
        val states = mutableListOf<PlaybackUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }
        val first = assertNotNull(viewModel.uiState.first { it.stream != null && it.title.isNotEmpty() }.stream)

        viewModel.onFailed(PlaybackFailure.Network)
        viewModel.uiState.first { it.hasStreamOtherThan(first.id) }

        val afterFirst = states.dropWhile { it.stream?.id != first.id }
        assertTrue(afterFirst.any { it.loading && it.stream == null && it.title.isNotEmpty() }, "no waiting state in $afterFirst")
        assertTrue(afterFirst.all { it.message == null }, "an error was shown in $afterFirst")
    }

    @Test
    fun whenTheTriesRunOutItIsAConnectionErrorWithTryAgainAndNoTranscode() = runTest {
        val jellyfin = server()
        val viewModel = viewModel()
        var stream = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)
        viewModel.onPlaying(0)
        viewModel.onPosition(61_000)

        repeat(retryDelays.size) {
            val previous = stream
            viewModel.onFailed(PlaybackFailure.Network)
            stream = assertNotNull(viewModel.uiState.first { it.hasStreamOtherThan(previous.id) }.stream)
            // The player says it is ready where it started: that is not playing on.
            viewModel.onPosition(61_000)
        }
        viewModel.onFailed(PlaybackFailure.Network)

        val state = viewModel.uiState.first { it.message != null }
        assertEquals(LoadMessages.PLAYBACK_NETWORK, state.message)
        assertNull(state.stream)
        assertEquals(false, state.loading)
        assertEquals(1, negotiations().size)
        val stopped = bodyOf(jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped").single())
        assertEquals(true, stopped.getValue("Failed").jsonPrimitive.boolean)

        // "Try again" asks the server afresh, from where it was.
        viewModel.onRetry()
        val again = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)
        assertEquals(61_000L, again.startPositionMs)
        assertEquals(2, negotiations().size)
    }

    // Plays on a device that can't direct play, then fails [times] more times with [failure] on the transcode.
    private suspend fun TestScope.transcodeThatEndsInAnError(failure: PlaybackFailure, times: Int): PlaybackViewModel {
        serverWithTracks()
        val viewModel = viewModel()
        var stream = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)
        viewModel.onPlaying(0)
        viewModel.onPosition(20_000)
        viewModel.onFailed(PlaybackFailure.Unsupported)
        stream = assertNotNull(viewModel.uiState.first { it.hasStreamOtherThan(stream.id) }.stream)
        repeat(times) {
            val previous = stream
            viewModel.onFailed(failure)
            val next = viewModel.uiState.first { it.hasStreamOtherThan(previous.id) || it.message != null }
            next.stream?.let { stream = it }
        }
        return viewModel
    }

    @Test
    fun tryingAgainAfterALostConnectionKeepsATranscodeThatWasNeeded() = runTest {
        val viewModel = transcodeThatEndsInAnError(PlaybackFailure.Network, times = retryDelays.size + 1)
        assertEquals(LoadMessages.PLAYBACK_NETWORK, viewModel.uiState.first { it.message != null }.message)
        val before = negotiations().size

        viewModel.onRetry()

        viewModel.uiState.first { it.stream != null }
        assertEquals(before + 1, negotiations().size)
        assertEquals(false, bodyOf(negotiations().last()).getValue("EnableDirectPlay").jsonPrimitive.boolean)
    }

    @Test
    fun tryingAgainAfterATranscodeThatWouldNotPlayStartsAfresh() = runTest {
        val viewModel = transcodeThatEndsInAnError(PlaybackFailure.Unsupported, times = 1)
        assertEquals(LoadMessages.PLAYBACK_FAILED, viewModel.uiState.first { it.message != null }.message)

        viewModel.onRetry()

        viewModel.uiState.first { it.stream != null }
        assertEquals(true, bodyOf(negotiations().last()).getValue("EnableDirectPlay").jsonPrimitive.boolean)
    }

    @Test
    fun playingOnPastTheFailureGivesTheTriesBack() = runTest {
        server()
        val viewModel = viewModel()
        var stream = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)
        viewModel.onPlaying(0)
        viewModel.onPosition(61_000)
        repeat(retryDelays.size) {
            val previous = stream
            viewModel.onFailed(PlaybackFailure.Network)
            stream = assertNotNull(viewModel.uiState.first { it.hasStreamOtherThan(previous.id) }.stream)
        }

        viewModel.onPosition(67_000)
        viewModel.onFailed(PlaybackFailure.Network)

        val next = assertNotNull(viewModel.uiState.first { it.hasStreamOtherThan(stream.id) }.stream)
        assertEquals(67_000L, next.startPositionMs)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun aRejectedTokenOnTheStreamAsksTheServerAgainFromTheSamePosition() = runTest {
        val jellyfin = server()
        val viewModel = viewModel()
        val first = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)
        viewModel.onPlaying(0)
        viewModel.onPosition(30_000)

        viewModel.onFailed(PlaybackFailure.Unauthorized)

        val second = assertNotNull(viewModel.uiState.first { it.hasStreamOtherThan(first.id) }.stream)
        assertEquals(30_000L, second.startPositionMs)
        assertEquals(2, negotiations().size)
        // Still a direct play: the stream was refused, not unplayable.
        assertEquals(true, bodyOf(negotiations().last()).getValue("EnableDirectPlay").jsonPrimitive.boolean)
        val stopped = bodyOf(jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped").single())
        assertEquals(true, stopped.getValue("Failed").jsonPrimitive.boolean)
    }

    @Test
    fun aDeadTokenOnTheStreamEndsTheSessionThroughTheUsualHandling() = runTest {
        val jellyfin = server()
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }
        viewModel.onPlaying(0)
        app.jellyfin.server("http://nas:8096").on("POST", "/Items/m1/PlaybackInfo", Reply.status(HttpStatusCode.Unauthorized))

        viewModel.onFailed(PlaybackFailure.Unauthorized)

        val state = viewModel.uiState.first { it.exit != null }
        assertEquals(HomeExit.WhoIsWatching("srv-1"), state.exit)
        assertEquals(2, jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo").size)
    }

    @Test
    fun aStreamRefusedAgainAfterAFreshOneIsAnErrorNotALoop() = runTest {
        server()
        val viewModel = viewModel()
        val first = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)
        viewModel.onPlaying(0)
        viewModel.onFailed(PlaybackFailure.Unauthorized)
        viewModel.uiState.first { it.hasStreamOtherThan(first.id) }

        viewModel.onFailed(PlaybackFailure.Unauthorized)

        val state = viewModel.uiState.first { it.message != null }
        assertEquals(LoadMessages.PLAYBACK_FAILED, state.message)
        assertNull(state.stream)
        assertEquals(2, negotiations().size)
    }

    @Test
    fun leavingTheScreenWithoutClosingStillSendsStopped() = runTest {
        // The ViewModel is cleared when its screen is popped; whatever the player didn't say, it does.
        val jellyfin = server()
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = PlaybackViewModel("m1", app.library, app.playback, app.reporter(reporterScope), app.connection, app.watch, app.profileSettings, app.binge, app.offlineLibrary, app.offline, app.localReporter(reporterScope), app.subtitles) as T
            },
        )[PlaybackViewModel::class]
        viewModel.uiState.first { it.stream != null }
        viewModel.onPlaying(0)
        viewModel.onPosition(12_000)

        store.clear()

        val stopped = bodyOf(jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped").single())
        assertEquals(120_000_000L, stopped.getValue("PositionTicks").jsonPrimitive.long)
    }

    private fun clearableViewModel(store: ViewModelStore): PlaybackViewModel = ViewModelProvider.create(
        store,
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = PlaybackViewModel("m1", app.library, app.playback, app.reporter(reporterScope), app.connection, app.watch, app.profileSettings, app.binge, app.offlineLibrary, app.offline, app.localReporter(reporterScope), app.subtitles) as T
        },
    )[PlaybackViewModel::class]

    // -- audio and subtitle selection --

    private val tracks = listOf(
        audioStreamJson(1, "English - AAC", language = "eng"),
        audioStreamJson(2, "Spanish - AAC", language = "spa"),
        subtitleStreamJson(3, "English subtitles", language = "eng"),
        subtitleStreamJson(4, "Spanish subtitles", language = "spa"),
        subtitleStreamJson(5, "Image subtitles", delivery = "Encode", codec = "hdmv_pgs_subtitle"),
    )

    // A server that answers like Jellyfin does: the user's tracks come back as the selected ones only
    // when the request names the media source, otherwise it picks ([audioDefault], [subtitleDefault]).
    // With [transcode], or when direct play is turned off, the source has to be streamed.
    private suspend fun serverWithTracks(
        transcode: Boolean = false,
        subtitleDefault: Int? = null,
        streams: List<String> = tracks,
    ): FakeJellyfin {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("m1", "Heat", year = 1995))))
        server.on("POST", "/Sessions/Playing", Reply.noContent())
        server.on("POST", "/Sessions/Playing/Progress", Reply.noContent())
        server.on("POST", "/Sessions/Playing/Stopped", Reply.noContent())
        server.on("POST", "/Items/m1/PlaybackInfo") { request ->
            val body = bodyOf(request)
            val named = "MediaSourceId" in body
            val audio = if (named) body["AudioStreamIndex"]?.jsonPrimitive?.int else 1
            val subtitle = if (named) body["SubtitleStreamIndex"]?.jsonPrimitive?.int else subtitleDefault
            val streamed = transcode || !body.getValue("EnableDirectPlay").jsonPrimitive.boolean
            val source = if (streamed) {
                mediaSourceJson(directPlay = false, directStream = false, transcodingUrl = HLS_PATH, streams = streams, defaultAudio = audio, defaultSubtitle = subtitle)
            } else {
                mediaSourceJson(streams = streams, defaultAudio = audio, defaultSubtitle = subtitle)
            }
            Reply.json(playbackInfoJson(source))
        }
        return app.jellyfin
    }

    private fun negotiations() = app.jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo")

    // A stream is on offer and it is not the one with [id]: the player has been given a new one.
    private fun PlaybackUiState.hasStreamOtherThan(id: Int) = stream?.let { it.id != id } == true

    @Test
    fun offersTheAudioAndSubtitleChoicesTheFileHas() = runTest {
        serverWithTracks()

        val state = viewModel().uiState.first { it.stream != null }

        assertEquals(listOf("English - AAC", "Spanish - AAC"), state.audioTracks.map { it.label })
        assertEquals(listOf(true, false), state.audioTracks.map { it.selected })
        assertEquals(
            listOf(Strings.subtitlesOff, "English subtitles", "Spanish subtitles", "Image subtitles"),
            state.subtitleTracks.map { it.label },
        )
        assertEquals(listOf(SUBTITLES_OFF, 3, 4, 5), state.subtitleTracks.map { it.index })
        // Nothing is shown until the user (or their server settings) asks, so "Off" is the selected row.
        assertEquals(listOf(true, false, false, false), state.subtitleTracks.map { it.selected })
        assertTrue(state.hasTrackChoices)
    }

    @Test
    fun theSubtitleTheServerPicksForTheUserIsTheSelectedOne() = runTest {
        serverWithTracks(subtitleDefault = 4)

        val state = viewModel().uiState.first { it.stream != null }

        assertEquals(listOf(false, false, true, false), state.subtitleTracks.map { it.selected })
    }

    @Test
    fun aFileWithOneAudioTrackAndNoSubtitlesHasNothingToChoose() = runTest {
        serverWithTracks(streams = listOf(audioStreamJson(1, "English - AAC")))

        val state = viewModel().uiState.first { it.stream != null }

        assertTrue(state.audioTracks.isEmpty())
        assertTrue(state.subtitleTracks.isEmpty())
        assertEquals(false, state.hasTrackChoices)
    }

    @Test
    fun aSingleAudioTrackAloneDoesNotNeedAPickerButSubtitlesDo() = runTest {
        serverWithTracks(streams = listOf(audioStreamJson(1, "English - AAC"), subtitleStreamJson(3, "English subtitles")))

        val state = viewModel().uiState.first { it.stream != null }

        assertTrue(state.audioTracks.isEmpty())
        assertEquals(2, state.subtitleTracks.size)
        assertTrue(state.hasTrackChoices)
    }

    @Test
    fun aTrackWithNoNameIsCalledByItsPosition() = runTest {
        serverWithTracks(streams = listOf("""{"Index":1,"Type":"Audio"}""", """{"Index":2,"Type":"Audio"}"""))

        val state = viewModel().uiState.first { it.stream != null }

        assertEquals(listOf(Strings.track(1), Strings.track(2)), state.audioTracks.map { it.label })
    }

    @Test
    fun theStreamCarriesEverySubtitleFileAndSaysWhoPicksTheAudio() = runTest {
        serverWithTracks()

        val stream = assertNotNull(viewModel().uiState.first { it.stream != null }.stream)

        assertEquals(listOf(3, 4), stream.subtitleFiles.map { it.index })
        assertTrue(stream.audioInPlayer)
    }

    @Test
    fun aStreamTheServerPreparesHasItsAudioChosenAlready() = runTest {
        serverWithTracks(transcode = true)

        val stream = assertNotNull(viewModel().uiState.first { it.stream != null }.stream)

        assertEquals(false, stream.audioInPlayer)
    }

    @Test
    fun anAudioChangeInADirectPlayHappensInThePlayerAndNothingRestarts() = runTest {
        val jellyfin = serverWithTracks()
        val viewModel = viewModel()
        val first = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)
        viewModel.onPlaying(0)

        viewModel.onSelectAudio(2)

        val state = viewModel.uiState.first { it.audioTracks.any { track -> track.index == 2 && track.selected } }
        assertEquals(first.id, state.stream?.id)
        assertEquals(listOf(false, true), state.audioTracks.map { it.selected })
        assertEquals(1, negotiations().size)
        // The server hears about it at once, and the track stays part of what the session plays.
        viewModel.onPaused(5_000)
        val progress = jellyfin.awaitRequests("POST", "/Sessions/Playing/Progress", 2).map(::bodyOf)
        assertEquals(listOf(2, 2), progress.map { it.getValue("AudioStreamIndex").jsonPrimitive.int })
        assertEquals(listOf(false, true), progress.map { it.getValue("IsPaused").jsonPrimitive.boolean })
    }

    @Test
    fun aSubtitleSentAsAFileIsSwitchedInThePlayerAndSoIsTurningItOff() = runTest {
        serverWithTracks()
        val viewModel = viewModel()
        val first = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)

        viewModel.onSelectSubtitle(4)
        val on = viewModel.uiState.first { state -> state.subtitleTracks.any { it.index == 4 && it.selected } }
        viewModel.onSelectSubtitle(SUBTITLES_OFF)
        val off = viewModel.uiState.first { state -> state.subtitleTracks.any { it.index == SUBTITLES_OFF && it.selected } }

        assertEquals(first.id, on.stream?.id)
        assertEquals(first.id, off.stream?.id)
        assertEquals(1, negotiations().size)
    }

    @Test
    fun aSubtitleThatIsNotAFileMeansAskingTheServerToBurnItInFromTheSamePosition() = runTest {
        val jellyfin = serverWithTracks()
        val viewModel = viewModel()
        val first = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)
        viewModel.onPlaying(0)
        viewModel.onPosition(42_000)

        viewModel.onSelectSubtitle(5)

        val second = assertNotNull(viewModel.uiState.first { it.hasStreamOtherThan(first.id) }.stream)
        assertEquals(42_000L, second.startPositionMs)
        val request = bodyOf(negotiations().last())
        assertEquals("ms-1", request.getValue("MediaSourceId").jsonPrimitive.content)
        assertEquals(5, request.getValue("SubtitleStreamIndex").jsonPrimitive.int)
        // Audio stays as it was: both indices are always sent together with the source.
        assertEquals(1, request.getValue("AudioStreamIndex").jsonPrimitive.int)
        // The first stream ended where it was, as a normal stop.
        val stopped = bodyOf(jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped").single())
        assertEquals(false, stopped.getValue("Failed").jsonPrimitive.boolean)
        assertEquals(420_000_000L, stopped.getValue("PositionTicks").jsonPrimitive.long)
    }

    @Test
    fun takingBurnedInSubtitlesOffMeansAskingAgainWithNoSubtitles() = runTest {
        serverWithTracks(subtitleDefault = 5)
        val viewModel = viewModel()
        val first = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)
        viewModel.onPosition(10_000)

        viewModel.onSelectSubtitle(SUBTITLES_OFF)

        val second = assertNotNull(viewModel.uiState.first { it.hasStreamOtherThan(first.id) }.stream)
        assertEquals(10_000L, second.startPositionMs)
        assertEquals(-1, bodyOf(negotiations().last()).getValue("SubtitleStreamIndex").jsonPrimitive.int)
        assertTrue(viewModel.uiState.value.subtitleTracks.first().selected)
    }

    @Test
    fun withASubtitleBurnedInAnotherFileSubtitleNeedsANewStreamToo() = runTest {
        serverWithTracks(subtitleDefault = 5)
        val viewModel = viewModel()
        val first = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)

        viewModel.onSelectSubtitle(3)

        viewModel.uiState.first { it.hasStreamOtherThan(first.id) }
        assertEquals(3, bodyOf(negotiations().last()).getValue("SubtitleStreamIndex").jsonPrimitive.int)
    }

    @Test
    fun anAudioChangeOnAStreamTheServerPreparesMeansAskingForThatAudio() = runTest {
        serverWithTracks(transcode = true)
        val viewModel = viewModel()
        val first = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)
        viewModel.onPosition(30_000)

        viewModel.onSelectAudio(2)

        val second = assertNotNull(viewModel.uiState.first { it.hasStreamOtherThan(first.id) }.stream)
        assertEquals(30_000L, second.startPositionMs)
        val request = bodyOf(negotiations().last())
        assertEquals("ms-1", request.getValue("MediaSourceId").jsonPrimitive.content)
        assertEquals(2, request.getValue("AudioStreamIndex").jsonPrimitive.int)
        assertEquals(listOf(false, true), viewModel.uiState.value.audioTracks.map { it.selected })
    }

    @Test
    fun choosingWhatIsAlreadySelectedDoesNothing() = runTest {
        serverWithTracks()
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }

        viewModel.onSelectAudio(1)
        viewModel.onSelectSubtitle(SUBTITLES_OFF)

        assertEquals(1, negotiations().size)
    }

    @Test
    fun choosingATrackBeforeThereIsAStreamDoesNothing() = runTest {
        serverWithTracks()
        app.jellyfin.server("http://nas:8096").refuseConnections()
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }

        viewModel.onSelectAudio(2)
        viewModel.onSelectSubtitle(3)

        assertTrue(viewModel.uiState.value.stream == null)
        assertTrue(negotiations().isEmpty())
    }

    @Test
    fun theUsersChoicesSurviveTheTranscodeFallback() = runTest {
        serverWithTracks()
        val viewModel = viewModel()
        val first = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)
        viewModel.onPlaying(0)
        viewModel.onSelectAudio(2)
        viewModel.onSelectSubtitle(4)
        viewModel.onPosition(20_000)

        viewModel.onFailed(PlaybackFailure.Unsupported)

        val second = assertNotNull(viewModel.uiState.first { it.hasStreamOtherThan(first.id) }.stream)
        assertEquals(false, second.audioInPlayer)
        val request = bodyOf(negotiations().last())
        assertEquals(false, request.getValue("EnableDirectPlay").jsonPrimitive.boolean)
        assertEquals("ms-1", request.getValue("MediaSourceId").jsonPrimitive.content)
        assertEquals(2, request.getValue("AudioStreamIndex").jsonPrimitive.int)
        assertEquals(4, request.getValue("SubtitleStreamIndex").jsonPrimitive.int)
        assertEquals(listOf(false, true), viewModel.uiState.value.audioTracks.map { it.selected })
    }

    @Test
    fun afterTheFallbackAnAudioChangeIsAnotherNegotiationBecauseTheServerOwnsTheAudio() = runTest {
        serverWithTracks()
        val viewModel = viewModel()
        val first = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)
        viewModel.onPlaying(0)
        viewModel.onFailed(PlaybackFailure.Unsupported)
        val second = assertNotNull(viewModel.uiState.first { it.hasStreamOtherThan(first.id) }.stream)

        viewModel.onSelectAudio(2)

        viewModel.uiState.first { it.hasStreamOtherThan(second.id) }
        assertEquals(3, negotiations().size)
        assertEquals(false, bodyOf(negotiations().last()).getValue("EnableDirectPlay").jsonPrimitive.boolean)
    }

    private fun halfWatched(minutes: Long = 10, played: Boolean = false) = itemJson(
        "m1",
        "Heat",
        year = 1995,
        runtimeTicks = 90 * TICKS_PER_MINUTE,
        positionTicks = minutes * TICKS_PER_MINUTE,
        played = played,
    )

    @Test
    fun resumesFiveSecondsBeforeWhereTheServerSaysTheUserStopped() = runTest {
        server(item = halfWatched(minutes = 10))

        val stream = assertNotNull(viewModel().uiState.first { it.stream != null }.stream)

        assertEquals(10 * 60_000L - RESUME_REWIND_MS, stream.startPositionMs)
    }

    @Test
    fun aPositionUnderTheRewindStartsAtTheBeginning() = runTest {
        server(item = itemJson("m1", "Heat", runtimeTicks = 90 * TICKS_PER_MINUTE, positionTicks = 30_000_000))

        val stream = assertNotNull(viewModel().uiState.first { it.stream != null }.stream)

        assertEquals(0L, stream.startPositionMs)
    }

    @Test
    fun startOverIgnoresTheSavedPosition() = runTest {
        server(item = halfWatched())

        val stream = assertNotNull(viewModel(startOver = true).uiState.first { it.stream != null }.stream)

        assertEquals(0L, stream.startPositionMs)
    }

    @Test
    fun anItemTheServerSaysIsWatchedStartsAtTheBeginning() = runTest {
        server(item = halfWatched(played = true))

        val stream = assertNotNull(viewModel().uiState.first { it.stream != null }.stream)

        assertEquals(0L, stream.startPositionMs)
    }

    @Test
    fun withoutTheItemPlaybackDoesNotStartAndRetryReadsItAgain() = runTest {
        val jellyfin = server(item = halfWatched())
        jellyfin.server("http://nas:8096").on("GET", "/Items", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel()

        val failed = viewModel.uiState.first { it.message != null }
        assertEquals(LoadMessages.forError(ApiError.Server(503)), failed.message)
        assertNull(failed.stream)
        assertFalse(failed.loading)
        // Starting at 0 would later report a small position over the real one, so nothing was negotiated.
        assertEquals(0, jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo").size)

        jellyfin.server("http://nas:8096").on("GET", "/Items", Reply.json(itemsJson(halfWatched())))
        viewModel.onRetry()
        val stream = assertNotNull(viewModel.uiState.first { it.stream != null }.stream)

        assertEquals(10 * 60_000L - RESUME_REWIND_MS, stream.startPositionMs)
    }

    @Test
    fun aRetryAfterAFailureOnceStartedKeepsThePositionItReached() = runTest {
        server(item = halfWatched())
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }
        viewModel.onPosition(700_000)

        viewModel.onFailed(PlaybackFailure.Network)
        val stream = assertNotNull(viewModel.uiState.first { it.stream?.id == 2 }.stream)

        assertEquals(700_000L, stream.startPositionMs)
    }

    // ---- Auto-advance ----

    private val tenMinutes = 10 * 60_000L

    private fun episode(id: String, number: Int, minutes: Long = 10) = itemJson(
        id,
        if (id == "m1") "Heat" else "Northbound",
        type = "Episode",
        seriesName = "Paper Harbour",
        seriesId = "s1",
        seasonNumber = 1,
        episodeNumber = number,
        runtimeTicks = minutes * TICKS_PER_MINUTE,
    )

    // A signed-in server with a show: m1 is episode 1, e2 comes after it.
    private suspend fun seriesServer(
        first: String = episode("m1", 1),
        episodes: String = itemsJson(first, episode("e2", 2)),
    ): FakeJellyfin {
        val jellyfin = server(item = first)
        val show = jellyfin.server("http://nas:8096")
        show.on("GET", "/Shows/s1/Episodes", Reply.json(episodes))
        show.on("POST", "/Items/e2/PlaybackInfo", Reply.json(playbackInfoJson(mediaSourceJson())))
        return jellyfin
    }

    private fun TestScope.statesOf(viewModel: PlaybackViewModel): List<PlaybackUiState> {
        val seen = SeenStates()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiState.collect { seen.add(it) }
        }
        return seen
    }

    // Playing, with the next episode looked up (it is asked for in the background).
    private suspend fun TestScope.playing(viewModel: PlaybackViewModel, jellyfin: FakeJellyfin): List<PlaybackUiState> {
        val seen = statesOf(viewModel)
        viewModel.uiState.first { it.stream != null }
        viewModel.onPlaying(0)
        jellyfin.awaitRequests("GET", "/Shows/s1/Episodes")
        repeat(5) { yield() }
        return seen
    }

    @Test
    fun theCardAppearsInTheLast30SecondsWithATenSecondCountdown() = runTest {
        val jellyfin = seriesServer()
        val viewModel = viewModel()
        val seen = playing(viewModel, jellyfin)

        viewModel.onPosition(tenMinutes - 31_000)
        assertNull(seen.last().upNext)
        viewModel.onPosition(tenMinutes - 30_000)

        val card = assertNotNull(seen.last().upNext)
        assertEquals("e2", card.id)
        assertEquals("S1 E2", card.label)
        assertEquals("Northbound", card.title)
        assertEquals(10, card.secondsLeft)
        assertFalse(seen.last().finished)
    }

    @Test
    fun atZeroTheNextEpisodeStartsInPlaceAndTheFinishedOneIsReportedAtItsEnd() = runTest {
        val jellyfin = seriesServer()
        val viewModel = viewModel(countdownStep = 1.milliseconds)
        val seen = playing(viewModel, jellyfin)
        val first = assertNotNull(seen.last().stream)

        viewModel.onPosition(tenMinutes - 30_000)
        val next = viewModel.uiState.first { it.title == "Northbound" && it.stream != null }

        val stream = assertNotNull(next.stream)
        assertNotEquals(first.id, stream.id)
        assertEquals(0L, stream.startPositionMs)
        assertNull(next.upNext)
        assertFalse(next.finished)
        assertEquals("Paper Harbour  ·  S1 E2", next.subtitle)
        val stopped = bodyOf(jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped").first())
        assertEquals("m1", stopped.getValue("ItemId").jsonPrimitive.content)
        // At its end, not where the countdown cut in, so the server counts it as watched.
        assertEquals(tenMinutes * 10_000, stopped.getValue("PositionTicks").jsonPrimitive.long)
        assertEquals(1, jellyfin.requestsTo("POST", "/Items/e2/PlaybackInfo").size)
    }

    @Test
    fun `an episode with a next one offers it for the Next episode button`() = runTest {
        val jellyfin = seriesServer()
        val viewModel = viewModel()
        val seen = playing(viewModel, jellyfin)

        assertEquals("S1 E2  ·  Northbound", seen.last().nextEpisode)

        viewModel.onPlayNow()
        val next = viewModel.uiState.first { it.title == "Northbound" && it.stream != null }
        // The next one has no episode after it.
        assertNull(next.nextEpisode)
    }

    @Test
    fun `the next episode is known with autoplay off too`() = runTest {
        val jellyfin = seriesServer()
        val viewModel = viewModel(autoAdvance = false)
        val seen = playing(viewModel, jellyfin)

        assertEquals("S1 E2  ·  Northbound", seen.last().nextEpisode)
    }

    @Test
    fun `the last episode and a movie offer no next episode`() = runTest {
        val last = seriesServer(episodes = itemsJson(episode("m1", 1)))
        val seen = playing(viewModel(), last)
        assertNull(seen.last().nextEpisode)
    }

    @Test
    fun `a movie offers no next episode`() = runTest {
        server()
        val viewModel = viewModel()
        assertNull(viewModel.uiState.first { it.stream != null }.nextEpisode)
    }

    @Test
    fun `an episode with one before it offers it for the Previous button`() = runTest {
        val jellyfin = seriesServer(episodes = itemsJson(episode("e0", 0), episode("m1", 1), episode("e2", 2)))
        jellyfin.server("http://nas:8096").on("POST", "/Items/e0/PlaybackInfo", Reply.json(playbackInfoJson(mediaSourceJson())))
        val viewModel = viewModel()
        val seen = playing(viewModel, jellyfin)
        viewModel.onPosition(60_000)

        assertTrue(seen.last().episodeControls)
        assertEquals("S1 E0  ·  Northbound", seen.last().previousEpisode)

        viewModel.onPlayPrevious()
        val previous = viewModel.uiState.first { it.subtitle == "Paper Harbour  ·  S1 E0" && it.stream != null }

        assertEquals(1, jellyfin.requestsTo("POST", "/Items/e0/PlaybackInfo").size)
        assertTrue(previous.episodeControls)
        val stopped = bodyOf(jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped").first())
        assertEquals("m1", stopped.getValue("ItemId").jsonPrimitive.content)
        // Going back is not finishing it: it stays where it was left.
        assertEquals(60_000L * 10_000, stopped.getValue("PositionTicks").jsonPrimitive.long)
    }

    @Test
    fun `the first episode offers no previous one but still has the episode controls`() = runTest {
        val jellyfin = seriesServer()
        val seen = playing(viewModel(), jellyfin)

        assertTrue(seen.last().episodeControls)
        assertNull(seen.last().previousEpisode)
    }

    @Test
    fun `a movie has no episode controls`() = runTest {
        server()
        val state = viewModel().uiState.first { it.stream != null }

        assertFalse(state.episodeControls)
        assertNull(state.previousEpisode)
    }

    @Test
    fun playNowDoesNotWaitForTheCountdown() = runTest {
        val jellyfin = seriesServer()
        val viewModel = viewModel()
        val seen = playing(viewModel, jellyfin)
        viewModel.onPosition(tenMinutes - 20_000)
        assertNotNull(seen.last().upNext)

        viewModel.onPlayNow()
        val next = viewModel.uiState.first { it.title == "Northbound" && it.stream != null }

        assertNull(next.upNext)
    }

    @Test
    fun watchCreditsDismissesTheCardForGoodAndPlaysOnToTheEndAndTheScreenCloses() = runTest {
        val jellyfin = seriesServer()
        val viewModel = viewModel()
        val seen = playing(viewModel, jellyfin)
        viewModel.onPosition(tenMinutes - 25_000)
        assertNotNull(seen.last().upNext)

        viewModel.onWatchCredits()
        assertNull(seen.last().upNext)
        viewModel.onPosition(tenMinutes - 10_000)
        assertNull(seen.last().upNext)
        viewModel.onEnded(tenMinutes)

        assertTrue(seen.last().finished)
        assertEquals(0, jellyfin.requestsTo("POST", "/Items/e2/PlaybackInfo").size)
    }

    @Test
    fun aShortVideoOnlyCountsDownOnceItHasEnded() = runTest {
        val short = episode("m1", 1, minutes = 2)
        val jellyfin = seriesServer(first = short, episodes = itemsJson(short, episode("e2", 2)))
        val viewModel = viewModel(countdownStep = 1.hours)
        val seen = playing(viewModel, jellyfin)

        viewModel.onPosition(2 * 60_000L - 10_000)
        assertNull(seen.last().upNext)
        viewModel.onEnded(2 * 60_000L)

        assertNotNull(seen.last().upNext)
        assertFalse(seen.last().finished)
        // "Watch credits" at that point has nothing left to watch: the screen closes.
        viewModel.onWatchCredits()
        assertTrue(seen.last().finished)
    }

    @Test
    fun aVideoThatEndsDuringTheCountdownStartsTheNextAtOnce() = runTest {
        val jellyfin = seriesServer()
        val viewModel = viewModel()
        val seen = playing(viewModel, jellyfin)
        viewModel.onPosition(tenMinutes - 5_000)
        assertNotNull(seen.last().upNext)

        viewModel.onEnded(tenMinutes)
        val next = viewModel.uiState.first { it.title == "Northbound" && it.stream != null }

        assertFalse(next.finished)
    }

    @Test
    fun aMovieJustFinishes() = runTest {
        val jellyfin = server(item = itemJson("m1", "Heat", runtimeTicks = 10 * TICKS_PER_MINUTE))
        val viewModel = viewModel()
        val seen = statesOf(viewModel)
        viewModel.uiState.first { it.stream != null }

        viewModel.onPosition(tenMinutes - 5_000)
        assertNull(seen.last().upNext)
        viewModel.onEnded(tenMinutes)

        assertTrue(seen.last().finished)
        assertEquals(0, jellyfin.requestsTo("GET", "/Shows/s1/Episodes").size)
    }

    @Test
    fun theLastEpisodeJustFinishes() = runTest {
        val only = episode("m1", 1)
        val jellyfin = seriesServer(first = only, episodes = itemsJson(only))
        val viewModel = viewModel()
        val seen = playing(viewModel, jellyfin)

        viewModel.onPosition(tenMinutes - 5_000)
        assertNull(seen.last().upNext)
        viewModel.onEnded(tenMinutes)

        assertTrue(seen.last().finished)
    }

    @Test
    fun withAutoAdvanceOffNoCardShowsAndTheEpisodeJustFinishes() = runTest {
        val jellyfin = seriesServer()
        val viewModel = viewModel(autoAdvance = false)
        // The next episode is still looked up, for the Next episode button.
        val seen = playing(viewModel, jellyfin)

        viewModel.onPosition(tenMinutes - 5_000)
        viewModel.onEnded(tenMinutes)

        assertNull(seen.last().upNext)
        assertTrue(seen.last().finished)
        assertEquals(0, jellyfin.requestsTo("POST", "/Items/e2/PlaybackInfo").size)
    }

    @Test
    fun aFailedLookupOfTheNextEpisodeJustFinishes() = runTest {
        val jellyfin = seriesServer()
        jellyfin.server("http://nas:8096").on("GET", "/Shows/s1/Episodes", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel()
        val seen = playing(viewModel, jellyfin)

        viewModel.onPosition(tenMinutes - 5_000)
        assertNull(seen.last().upNext)
        viewModel.onEnded(tenMinutes)

        assertTrue(seen.last().finished)
    }

    @Test
    fun goingToTheBackgroundTakesTheCardAwayWithoutPlayingOn() = runTest {
        val jellyfin = seriesServer()
        val viewModel = viewModel()
        val seen = playing(viewModel, jellyfin)
        viewModel.onPosition(tenMinutes - 25_000)
        assertNotNull(seen.last().upNext)

        viewModel.onBackgrounded(tenMinutes - 25_000)
        // The countdown runs on virtual time: had it survived, this would play it to its end.
        advanceUntilIdle()

        assertNull(seen.last().upNext)
        assertEquals(0, app.binge.count.value)
        assertEquals("Heat", seen.last().title)
        assertEquals(0, jellyfin.requestsTo("POST", "/Items/e2/PlaybackInfo").size)
    }

    @Test
    fun closingTheScreenTakesTheCardAway() = runTest {
        val jellyfin = seriesServer()
        val viewModel = viewModel()
        val seen = playing(viewModel, jellyfin)
        viewModel.onPosition(tenMinutes - 25_000)
        assertNotNull(seen.last().upNext)

        viewModel.onClosed(tenMinutes - 25_000)

        assertNull(seen.last().upNext)
    }

    @Test
    fun seekingBackOutOfTheWindowTakesTheCardAwayAndItComesBackWhenTheEndIsNearAgain() = runTest {
        val jellyfin = seriesServer()
        val viewModel = viewModel()
        val seen = playing(viewModel, jellyfin)
        viewModel.onPosition(tenMinutes - 25_000)
        assertNotNull(seen.last().upNext)

        viewModel.onSeeked(tenMinutes - 120_000)
        assertNull(seen.last().upNext)
        viewModel.onPosition(tenMinutes - 20_000)

        assertEquals(10, assertNotNull(seen.last().upNext).secondsLeft)
    }

    private suspend fun serverWithSegments(vararg segments: String): PlaybackViewModel = serverWithSettings({}, *segments)

    // Like [serverWithSegments], with profile settings saved (after sign-in) before the player opens.
    private suspend fun serverWithSettings(settings: suspend () -> Unit, vararg segments: String): PlaybackViewModel {
        server(segments = Reply.json(segmentsJson(*segments)))
        settings()
        return viewModel().also { vm ->
            vm.uiState.first { it.stream != null }
            // The segments come in a separate call; wait until it has been made, so the first position isn't checked before it.
            while (app.jellyfin.requestsTo("GET", "/MediaSegments/m1").isEmpty()) yield()
        }
    }

    @Test
    fun `offer appears inside an intro and stays for the whole segment`() = runTest {
        val vm = serverWithSegments(segmentJson("Intro", 10_000, 90_000))

        vm.onPosition(5_000)
        assertNull(vm.uiState.value.skipOffer)
        vm.onPosition(10_000)
        assertEquals(SkipOffer("Skip intro", 90_000), vm.uiState.value.skipOffer)
        vm.onPosition(50_000)
        assertEquals(SkipOffer("Skip intro", 90_000), vm.uiState.value.skipOffer)
        vm.onPosition(89_999)
        assertNotNull(vm.uiState.value.skipOffer)
        vm.onPosition(90_000)
        assertNull(vm.uiState.value.skipOffer)
    }

    @Test
    fun `no offer outside any segment`() = runTest {
        val vm = serverWithSegments(segmentJson("Intro", 10_000, 90_000))

        vm.onPosition(200_000)

        assertNull(vm.uiState.value.skipOffer)
    }

    @Test
    fun `outro offers Skip credits`() = runTest {
        val vm = serverWithSegments(segmentJson("Outro", 5_400_000, 6_000_000))

        vm.onPosition(5_500_000)

        assertEquals(SkipOffer("Skip credits", 6_000_000), vm.uiState.value.skipOffer)
    }

    @Test
    fun `skip credits gives way to the up next card and comes back without it`() = runTest {
        val jellyfin = seriesServer()
        jellyfin.server("http://nas:8096").on("GET", "/MediaSegments/m1", Reply.json(segmentsJson(segmentJson("Outro", tenMinutes - 60_000, tenMinutes))))
        val viewModel = viewModel()
        val seen = playing(viewModel, jellyfin)
        jellyfin.awaitRequests("GET", "/MediaSegments/m1")

        viewModel.onPosition(tenMinutes - 61_000)
        assertNull(seen.last().upNext)
        viewModel.onPosition(tenMinutes - 60_000)
        assertNotNull(seen.last().upNext)
        assertNull(seen.last().skipOffer)
        // Both overlays never show at once, not even for one state.
        assertTrue(seen.none { it.upNext != null && it.skipOffer != null })

        viewModel.onWatchCredits()
        assertNull(seen.last().upNext)
        assertEquals(SkipOffer("Skip credits", tenMinutes), seen.last().skipOffer)
    }

    @Test
    fun `the card waits for the credits to start however early they come`() = runTest {
        val jellyfin = seriesServer()
        jellyfin.server("http://nas:8096").on("GET", "/MediaSegments/m1", Reply.json(segmentsJson(segmentJson("Outro", tenMinutes - 120_000, tenMinutes))))
        val viewModel = viewModel()
        val seen = playing(viewModel, jellyfin)
        jellyfin.awaitRequests("GET", "/MediaSegments/m1")

        viewModel.onPosition(tenMinutes - 121_000)
        assertNull(seen.last().upNext)
        viewModel.onPosition(tenMinutes - 120_000)
        assertNotNull(seen.last().upNext)
    }

    @Test
    fun `the countdown stands still while the video is paused`() = runTest {
        val jellyfin = seriesServer()
        val viewModel = viewModel(countdownStep = 1.milliseconds)
        val seen = playing(viewModel, jellyfin)

        viewModel.onPaused(tenMinutes - 31_000)
        viewModel.onPosition(tenMinutes - 30_000)
        repeat(20) { yield() }
        assertEquals(10, assertNotNull(seen.last().upNext).secondsLeft)
        assertNull(seen.last().finished.takeIf { it })

        viewModel.onPlaying(tenMinutes - 30_000)
        val next = viewModel.uiState.first { it.title == "Northbound" && it.stream != null }
        assertNull(next.upNext)
    }

    @Test
    fun `a pause in the middle of the countdown holds it until the video plays again`() = runTest {
        val jellyfin = seriesServer()
        val viewModel = viewModel(countdownStep = 1.milliseconds)
        val seen = playing(viewModel, jellyfin)

        viewModel.onPosition(tenMinutes - 30_000)
        viewModel.uiState.first { (it.upNext?.secondsLeft ?: 10) < 10 }
        viewModel.onPaused(tenMinutes - 25_000)
        repeat(50) { yield() }
        val held = assertNotNull(seen.last().upNext).secondsLeft
        repeat(50) { yield() }
        assertEquals(held, assertNotNull(seen.last().upNext).secondsLeft)

        viewModel.onPlaying(tenMinutes - 25_000)
        val next = viewModel.uiState.first { it.title == "Northbound" && it.stream != null }
        assertNull(next.upNext)
    }

    @Test
    fun `an intro offer is not hidden by the up next card`() = runTest {
        val jellyfin = seriesServer()
        jellyfin.server("http://nas:8096").on("GET", "/MediaSegments/m1", Reply.json(segmentsJson(segmentJson("Recap", tenMinutes - 60_000, tenMinutes - 10_000))))
        val viewModel = viewModel()
        val seen = playing(viewModel, jellyfin)
        jellyfin.awaitRequests("GET", "/MediaSegments/m1")

        viewModel.onPosition(tenMinutes - 25_000)

        assertNotNull(seen.last().upNext)
        assertNotNull(seen.last().skipOffer)
    }

    @Test
    fun `skip sets a seek to the segment end`() = runTest {
        val vm = serverWithSegments(segmentJson("Intro", 10_000, 90_000))
        vm.onPosition(20_000)

        vm.onSkip()
        assertEquals(90_000L, vm.uiState.value.pendingSeekMs)

        vm.onSeekHandled()
        assertNull(vm.uiState.value.pendingSeekMs)
        vm.onPosition(90_500)
        assertNull(vm.uiState.value.skipOffer)
    }

    @Test
    fun `selecting a speed updates state`() = runTest {
        server()
        val vm = viewModel()
        vm.uiState.first { it.stream != null }
        assertEquals(1f, vm.uiState.value.speed)

        vm.onSelectSpeed(1.5f)

        assertEquals(1.5f, vm.uiState.value.speed)
    }

    @Test
    fun `an unsupported speed is ignored`() = runTest {
        server()
        val vm = viewModel()
        vm.uiState.first { it.stream != null }
        vm.onSelectSpeed(2f)

        vm.onSelectSpeed(3f)

        assertEquals(2f, vm.uiState.value.speed)
    }

    @Test
    fun `the speed is kept when the stream is opened again`() = runTest {
        server()
        val vm = viewModel()
        vm.uiState.first { it.stream != null }
        vm.onSelectSpeed(1.25f)

        vm.onFailed(PlaybackFailure.Network)
        val reopened = vm.uiState.first { it.stream?.id == 2 }

        assertEquals(1.25f, reopened.speed)
    }

    @Test
    fun `speed resets to 1 for a new item`() = runTest {
        val jellyfin = seriesServer()
        val vm = viewModel()
        playing(vm, jellyfin)
        vm.onSelectSpeed(2f)

        vm.onPlayNow()
        val next = vm.uiState.first { it.stream?.id == 2 }

        assertEquals(1f, next.speed)
    }

    @Test
    fun `progress reports are unaffected by the speed`() = runTest {
        val jellyfin = server()
        val vm = viewModel()
        vm.uiState.first { it.stream != null }
        vm.onPlaying(0)
        vm.onSelectSpeed(2f)
        vm.onPaused(12_000)

        val progress = bodyOf(jellyfin.awaitRequests("POST", "/Sessions/Playing/Progress").first())
        assertEquals(12_000L * 10_000, progress["PositionTicks"]!!.jsonPrimitive.long)
        assertEquals(1, jellyfin.requestsTo("POST", "/Sessions/Playing").size)
    }

    // Returns once the reporter has handled everything [vm] sent it so far: reports go out one at a time
    // in order, so a seek's Progress reaching the server means a Stopped sent before it would have too.
    // Needs a started session.
    private suspend fun afterEarlierReports(vm: PlaybackViewModel, jellyfin: FakeJellyfin) {
        val before = jellyfin.requestsTo("POST", "/Sessions/Playing/Progress").size
        vm.onSeeked(5_500)
        jellyfin.awaitRequests("POST", "/Sessions/Playing/Progress", count = before + 1)
    }

    @Test
    fun `entering PiP does not send Stopped`() = runTest {
        val jellyfin = server()
        val vm = viewModel()
        vm.uiState.first { it.stream != null }
        vm.onPlaying(0)

        vm.onEnteredPictureInPicture(5_000)
        vm.onBackgrounded(5_000)
        val state = vm.uiState.first { it.inPictureInPicture }

        afterEarlierReports(vm, jellyfin)
        assertTrue(state.inPictureInPicture)
        assertTrue(jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").isEmpty())
    }

    @Test
    fun `progress keeps flowing in PiP`() = runTest {
        val jellyfin = server()
        val vm = viewModel()
        vm.uiState.first { it.stream != null }
        vm.onPlaying(0)
        vm.onEnteredPictureInPicture(5_000)

        vm.onPaused(7_000)

        val progress = bodyOf(jellyfin.awaitRequests("POST", "/Sessions/Playing/Progress").first())
        assertEquals(true, progress.getValue("IsPaused").jsonPrimitive.boolean)
    }

    @Test
    fun `closing the PiP window sends one Stopped`() = runTest {
        val jellyfin = server()
        val vm = viewModel()
        vm.uiState.first { it.stream != null }
        vm.onPlaying(0)
        vm.onEnteredPictureInPicture(5_000)

        vm.onLeftPictureInPicture(9_000, closed = true)
        vm.onClosed(9_000)

        jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped")
        // Playing again starts a new session: its Start is sent only after every report before it.
        vm.onPlaying(9_000)
        jellyfin.awaitRequests("POST", "/Sessions/Playing", count = 2)
        assertEquals(1, jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").size)
        assertFalse(vm.uiState.first { !it.inPictureInPicture }.inPictureInPicture)
    }

    @Test
    fun `expanding from PiP sends nothing and keeps the session`() = runTest {
        val jellyfin = server()
        val vm = viewModel()
        vm.uiState.first { it.stream != null }
        vm.onPlaying(0)
        vm.onEnteredPictureInPicture(5_000)

        vm.onLeftPictureInPicture(6_000, closed = false)

        afterEarlierReports(vm, jellyfin)
        assertTrue(jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").isEmpty())
        // The session is the same one: backgrounding now stops it as usual.
        vm.onBackgrounded(6_500)
        jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped")
        assertEquals(1, jellyfin.requestsTo("POST", "/Sessions/Playing").size)
    }

    @Test
    fun `backgrounding without PiP still sends Stopped`() = runTest {
        val jellyfin = server()
        val vm = viewModel()
        vm.uiState.first { it.stream != null }
        vm.onPlaying(0)

        vm.onBackgrounded(4_000)

        assertEquals(1, jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped").size)
    }

    // "Are you still watching?": the card's countdown is held back once enough episodes started by themselves.

    private suspend fun TestScope.nearTheEnd(viewModel: PlaybackViewModel, jellyfin: FakeJellyfin): List<PlaybackUiState> {
        val seen = playing(viewModel, jellyfin)
        viewModel.onPosition(tenMinutes - 30_000)
        return seen
    }

    @Test
    fun `a countdown that runs out counts as an auto advance`() = runTest {
        val jellyfin = seriesServer()
        val vm = viewModel(countdownStep = 1.milliseconds)
        val seen = playing(vm, jellyfin)

        vm.onPosition(tenMinutes - 30_000)
        vm.uiState.first { it.stream?.id == 2 }

        assertEquals(1, app.binge.count.value)
        assertFalse(seen.last().stillWatching)
    }

    @Test
    fun `play now is an interaction and resets the count`() = runTest {
        val jellyfin = seriesServer()
        app.binge.autoAdvanced()
        val vm = viewModel(countdownStep = 1.hours)
        val seen = nearTheEnd(vm, jellyfin)
        assertNotNull(seen.last().upNext)

        vm.onPlayNow()

        assertEquals(0, app.binge.count.value)
    }

    @Test
    fun `the prompt replaces the countdown after three auto advances`() = runTest {
        val jellyfin = seriesServer()
        repeat(3) { app.binge.autoAdvanced() }
        val vm = viewModel(countdownStep = 1.milliseconds)
        val seen = nearTheEnd(vm, jellyfin)

        vm.uiState.first { it.stillWatching }
        // A countdown would run on virtual time: this plays any that was started to its end.
        advanceUntilIdle()

        assertTrue(seen.last().stillWatching)
        assertNull(seen.last().upNext)
        // Nothing advanced on its own.
        assertEquals(1, seen.last().stream?.id)
    }

    @Test
    fun `threshold 0 never prompts`() = runTest {
        val jellyfin = seriesServer()
        app.profileSettings.setBingeThreshold(0)
        repeat(10) { app.binge.autoAdvanced() }
        val vm = viewModel(countdownStep = 1.hours)
        val seen = nearTheEnd(vm, jellyfin)

        assertNotNull(seen.last().upNext)
        assertFalse(seen.last().stillWatching)
    }

    @Test
    fun `threshold 2 prompts after two`() = runTest {
        val jellyfin = seriesServer()
        app.profileSettings.setBingeThreshold(2)
        app.binge.autoAdvanced()
        val vm = viewModel(countdownStep = 1.hours)
        val seen = nearTheEnd(vm, jellyfin)
        assertNotNull(seen.last().upNext)
        assertFalse(seen.last().stillWatching)
        // One more by itself, in the next episode's player, and it asks.
        app.binge.autoAdvanced()
        val second = viewModel(countdownStep = 1.hours)
        val seenSecond = nearTheEnd(second, jellyfin)

        second.uiState.first { it.stillWatching }
        assertTrue(seenSecond.last().stillWatching)
    }

    @Test
    fun `interaction resets the count`() = runTest {
        server()
        repeat(2) { app.binge.autoAdvanced() }
        val vm = viewModel()
        vm.uiState.first { it.stream != null }

        vm.onUserInteraction()

        assertEquals(0, app.binge.count.value)
    }

    @Test
    fun `the next episode keeps the count`() = runTest {
        // Auto advance happens in the same player, so starting the next episode must not reset the count.
        val jellyfin = seriesServer()
        val vm = viewModel(countdownStep = 1.milliseconds)
        playing(vm, jellyfin)
        vm.onPosition(tenMinutes - 30_000)
        vm.uiState.first { it.stream?.id == 2 }

        vm.onPlaying(0)
        vm.onPosition(5_000)

        assertEquals(1, app.binge.count.value)
    }

    @Test
    fun `closing the player resets the count`() = runTest {
        server()
        repeat(2) { app.binge.autoAdvanced() }
        val vm = viewModel()
        vm.uiState.first { it.stream != null }

        vm.onClosed(1_000)

        assertEquals(0, app.binge.count.value)
    }

    @Test
    fun `continue clears the prompt and the count and the countdown carries on`() = runTest {
        val jellyfin = seriesServer()
        repeat(3) { app.binge.autoAdvanced() }
        val vm = viewModel(countdownStep = 1.hours)
        val seen = nearTheEnd(vm, jellyfin)
        vm.uiState.first { it.stillWatching }

        vm.onContinueWatching()
        vm.onPosition(tenMinutes - 25_000)

        assertEquals(0, app.binge.count.value)
        assertFalse(seen.last().stillWatching)
        assertNotNull(seen.last().upNext)
    }

    @Test
    fun `continue after the episode ended counts down to the next one`() = runTest {
        val jellyfin = seriesServer()
        repeat(3) { app.binge.autoAdvanced() }
        val vm = viewModel(countdownStep = 1.milliseconds)
        val seen = playing(vm, jellyfin)
        // The end came before any countdown (a seek to the end, a short episode): the prompt asks there.
        vm.onEnded(tenMinutes)
        vm.uiState.first { it.stillWatching }

        vm.onContinueWatching()
        vm.uiState.first { it.stream?.id == 2 }

        assertFalse(seen.last().stillWatching)
        assertFalse(seen.last().finished)
        // The count was just reset, and the countdown that ran out counts as one auto advance.
        assertEquals(1, app.binge.count.value)
    }

    @Test
    fun `stop watching ends the session and closes the screen`() = runTest {
        val jellyfin = seriesServer()
        repeat(3) { app.binge.autoAdvanced() }
        val vm = viewModel(countdownStep = 1.hours)
        val seen = nearTheEnd(vm, jellyfin)
        vm.uiState.first { it.stillWatching }

        vm.onStopWatching()

        jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped")
        assertTrue(seen.last().finished)
        assertFalse(seen.last().stillWatching)
        assertEquals(0, app.binge.count.value)
    }

    @Test
    fun `the stream carries the dynamic range of the plan`() = runTest {
        server(info = playbackInfoJson(mediaSourceJson(videoWidth = 3840, videoHeight = 2160, videoRangeType = "DOVIWithHDR10")))

        val stream = assertNotNull(viewModel().uiState.first { it.stream != null }.stream)

        assertEquals(DynamicRange.DolbyVision, stream.dynamicRange)
    }

    @Test
    fun `an SDR or unknown picture is SDR`() = runTest {
        server(info = playbackInfoJson(mediaSourceJson(videoRangeType = "SDR")))
        assertEquals(DynamicRange.Sdr, assertNotNull(viewModel().uiState.first { it.stream != null }.stream).dynamicRange)
    }

    @Test
    fun `a stream with no video information is SDR`() = runTest {
        server()
        assertEquals(DynamicRange.Sdr, assertNotNull(viewModel().uiState.first { it.stream != null }.stream).dynamicRange)
    }

    @Test
    fun `skip with no offer does nothing`() = runTest {
        val vm = serverWithSegments(segmentJson("Intro", 10_000, 90_000))
        vm.onPosition(200_000)

        vm.onSkip()

        assertNull(vm.uiState.value.pendingSeekMs)
    }

    @Test
    fun `no segments means no offer and no error`() = runTest {
        val vm = serverWithSegments()

        vm.onPosition(20_000)

        assertNull(vm.uiState.value.skipOffer)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `a seek past the segment removes the offer`() = runTest {
        val vm = serverWithSegments(segmentJson("Intro", 10_000, 90_000))
        vm.onPosition(20_000)
        assertNotNull(vm.uiState.value.skipOffer)

        vm.onSeeked(300_000)

        assertNull(vm.uiState.value.skipOffer)
    }

    @Test
    fun `Unknown segments never offer`() = runTest {
        val vm = serverWithSegments(segmentJson("Mystery", 10_000, 90_000))

        vm.onPosition(20_000)

        assertNull(vm.uiState.value.skipOffer)
    }

    @Test
    fun `overlapping segments offer the one that ends last`() = runTest {
        val vm = serverWithSegments(segmentJson("Intro", 10_000, 60_000), segmentJson("Recap", 0, 90_000))

        vm.onPosition(20_000)

        assertEquals(SkipOffer("Skip recap", 90_000), vm.uiState.value.skipOffer)
    }

    @Test
    fun `a failing segments call does not stop playback from starting`() = runTest {
        server(segments = Reply.status(HttpStatusCode.InternalServerError))
        val vm = viewModel()

        val state = vm.uiState.first { it.stream != null }
        vm.onPosition(20_000)

        assertNull(state.message)
        assertNull(vm.uiState.value.skipOffer)
    }

    @Test
    fun `segments are fetched once even when the stream is opened again`() = runTest {
        val vm = serverWithSegments(segmentJson("Intro", 10_000, 90_000))

        vm.onFailed(PlaybackFailure.Unauthorized)
        vm.uiState.first { it.stream != null && it.stream.id == 2 }

        assertEquals(1, app.jellyfin.requestsTo("GET", "/MediaSegments/m1").size)
    }

    @Test
    fun `a skip asked for during a network retry is dropped when the stream comes back`() = runTest {
        val vm = serverWithSegments(segmentJson("Intro", 10_000, 90_000))
        vm.onPosition(20_000)

        vm.onFailed(PlaybackFailure.Network)
        vm.onSkip()
        vm.uiState.first { it.stream?.id == 2 }

        assertNull(vm.uiState.value.pendingSeekMs)
    }

    private fun trickplayInfo(width: Int) = """"$width":{"Width":$width,"Height":${width * 9 / 16},"TileWidth":10,"TileHeight":10,"ThumbnailCount":250,"Interval":10000,"Bandwidth":1}"""

    // [item] with trickplay thumbnails for each of [sources] (media source ids), as the server sends them.
    private fun withTrickplay(item: String, vararg sources: String) = item.dropLast(1) + ""","Trickplay":{${sources.joinToString(",") { """"$it":{${trickplayInfo(320)}}""" }}}}"""

    @Test
    fun `trickplay is loaded for the played media source`() = runTest {
        server(item = withTrickplay(itemJson("m1", "Heat", year = 1995), "ms-1", "ms-2"))

        val track = assertNotNull(viewModel().uiState.first { it.trickplay != null }.trickplay)

        assertEquals("ms-1", track.mediaSourceId)
        assertEquals(320, track.width)
        assertEquals(10_000L, track.intervalMs)
    }

    @Test
    fun `no trickplay entry leaves it null`() = runTest {
        server()
        val vm = viewModel()

        vm.uiState.first { it.stream != null }
        repeat(5) { yield() }

        assertNull(vm.uiState.value.trickplay)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `trickplay for another media source is ignored`() = runTest {
        server(item = withTrickplay(itemJson("m1", "Heat", year = 1995), "ms-2"))
        val vm = viewModel()

        vm.uiState.first { it.stream != null }
        repeat(5) { yield() }

        assertNull(vm.uiState.value.trickplay)
    }

    @Test
    fun `the next episode loads its own trickplay and never shows the previous one`() = runTest {
        val jellyfin = seriesServer(first = withTrickplay(episode("m1", 1), "ms-1"))
        val viewModel = viewModel(countdownStep = 1.milliseconds)
        val seen = playing(viewModel, jellyfin)
        assertNotNull(viewModel.uiState.value.trickplay)
        val asked = jellyfin.requestsTo("GET", "/Items").size

        viewModel.onPosition(tenMinutes - 30_000)
        viewModel.uiState.first { it.title == "Northbound" && it.stream != null }
        jellyfin.awaitRequests("GET", "/Items", count = asked + 1)

        // Asked again, for the new episode; and while it was switching nothing from the old one was left.
        assertEquals(1, jellyfin.requestsTo("GET", "/Items").count { it.query["ids"] == "e2" })
        assertTrue(seen.filter { it.title == "Northbound" && it.stream == null }.all { it.trickplay == null })
    }

    @Test
    fun `skipBack seeks back by the profile value`() = runTest {
        val vm = serverWithSettings({ app.profileSettings.setSkipBackSeconds(15) })
        vm.uiState.first { it.skipBackSeconds == 15 }

        vm.onSkipBack(60_000)

        assertEquals(45_000L, vm.uiState.value.pendingSeekMs)
    }

    @Test
    fun `skipBack clamps at zero`() = runTest {
        val vm = serverWithSegments()

        vm.onSkipBack(4_000)

        assertEquals(0L, vm.uiState.value.pendingSeekMs)
    }

    @Test
    fun `skipForward clamps at the duration`() = runTest {
        val vm = serverWithSettings({ app.profileSettings.setSkipForwardSeconds(30) })
        vm.uiState.first { it.skipForwardSeconds == 30 }

        vm.onSkipForward(175_000, 180_000)

        assertEquals(180_000L, vm.uiState.value.pendingSeekMs)
    }

    @Test
    fun `skipForward with an unknown duration does nothing`() = runTest {
        val vm = serverWithSegments()

        vm.onSkipForward(60_000, 0)

        assertNull(vm.uiState.value.pendingSeekMs)
    }

    @Test
    fun `two skips before the player reports stack`() = runTest {
        val vm = serverWithSegments()
        val duration = 600_000L

        // Both calls pass the same position: the second builds on the uncleared pendingSeekMs, not on a position the model advanced.
        vm.onSkipForward(60_000, duration)
        vm.onSkipForward(60_000, duration)

        assertEquals(80_000L, vm.uiState.value.pendingSeekMs)
    }

    @Test
    fun `skips in a row to one side add up`() = runTest {
        val vm = serverWithSegments()
        val duration = 600_000L

        vm.onSkipForward(60_000, duration)
        vm.onSeekHandled()
        vm.onSkipForward(70_000, duration)
        vm.onSeekHandled()
        vm.onSkipForward(80_000, duration)

        assertEquals(SkipRun(forward = true, movedMs = 30_000, taps = 3), vm.uiState.value.skipRun)
        assertEquals(30, vm.uiState.value.skipRun?.seconds)
    }

    @Test
    fun `a run started from the skip buttons keeps the controls and adds up taps on the video`() = runTest {
        val vm = serverWithSegments()
        val duration = 600_000L

        vm.onSkipForward(60_000, duration, fromControls = true)
        vm.onSeekHandled()
        vm.onSkipForward(70_000, duration)

        assertEquals(SkipRun(forward = true, movedMs = 20_000, taps = 2, fromControls = true), vm.uiState.value.skipRun)
    }

    @Test
    fun `a run started by double taps on the video does not keep the controls`() = runTest {
        val vm = serverWithSegments()

        vm.onSkipBack(60_000)

        assertFalse(assertNotNull(vm.uiState.value.skipRun).fromControls)
    }

    @Test
    fun `a skip to the other side starts a new run`() = runTest {
        val vm = serverWithSegments()
        val duration = 600_000L

        vm.onSkipForward(60_000, duration)
        vm.onSkipForward(60_000, duration)
        vm.onSkipBack(60_000)

        assertEquals(SkipRun(forward = false, movedMs = 10_000, taps = 1), vm.uiState.value.skipRun)
    }

    @Test
    fun `a skip run counts only how far it went`() = runTest {
        val vm = serverWithSegments()

        vm.onSkipBack(14_000)
        vm.onSkipBack(14_000)

        assertEquals(SkipRun(forward = false, movedMs = 14_000, taps = 2), vm.uiState.value.skipRun)
    }

    @Test
    fun `a skip run clears after a quiet moment and the next skip starts again`() = runTest {
        val vm = serverWithSegments()
        val duration = 600_000L

        vm.onSkipForward(60_000, duration)
        advanceTimeBy(SKIP_RUN_WINDOW - 100.milliseconds)
        vm.onSkipForward(60_000, duration)
        advanceTimeBy(SKIP_RUN_WINDOW - 100.milliseconds)
        assertEquals(2, vm.uiState.value.skipRun?.taps)

        advanceTimeBy(200.milliseconds)
        assertNull(vm.uiState.value.skipRun)

        vm.onSkipForward(80_000, duration)
        assertEquals(SkipRun(forward = true, movedMs = 10_000, taps = 1), vm.uiState.value.skipRun)
    }

    @Test
    fun `autoSkip jumps over an intro once`() = runTest {
        val vm = serverWithSettings({ app.profileSettings.setAutoSkipSegments(true) }, segmentJson("Intro", 10_000, 40_000))

        vm.onPosition(12_000)
        assertEquals(40_000L, vm.uiState.value.pendingSeekMs)
        vm.onSeekHandled()
        vm.onPosition(15_000)

        assertNull(vm.uiState.value.pendingSeekMs)
        assertNotNull(vm.uiState.value.skipOffer)
    }

    @Test
    fun `autoSkip never skips an outro`() = runTest {
        val vm = serverWithSettings({ app.profileSettings.setAutoSkipSegments(true) }, segmentJson("Outro", 170_000, 180_000))

        vm.onPosition(172_000)

        assertNull(vm.uiState.value.pendingSeekMs)
        assertNotNull(vm.uiState.value.skipOffer)
    }

    @Test
    fun `autoSkip off never seeks`() = runTest {
        val vm = serverWithSegments(segmentJson("Intro", 10_000, 40_000))

        vm.onPosition(12_000)

        assertNotNull(vm.uiState.value.skipOffer)
        assertNull(vm.uiState.value.pendingSeekMs)
    }

    @Test
    fun `autoSkip jumps over a recap once`() = runTest {
        val vm = serverWithSettings({ app.profileSettings.setAutoSkipSegments(true) }, segmentJson("Recap", 10_000, 30_000))

        vm.onPosition(12_000)
        assertEquals(30_000L, vm.uiState.value.pendingSeekMs)
        vm.onSeekHandled()
        vm.onPosition(15_000)

        assertNull(vm.uiState.value.pendingSeekMs)
    }

    @Test
    fun `the next episode auto skips an intro at the same times again`() = runTest {
        val jellyfin = seriesServer()
        val intro = Reply.json(segmentsJson(segmentJson("Intro", 10_000, 40_000)))
        val nas = jellyfin.server("http://nas:8096")
        nas.on("GET", "/MediaSegments/m1", intro)
        nas.on("GET", "/MediaSegments/e2", intro)
        app.profileSettings.setAutoSkipSegments(true)
        val viewModel = viewModel(countdownStep = 1.milliseconds)
        playing(viewModel, jellyfin)
        jellyfin.awaitRequests("GET", "/MediaSegments/m1")
        repeat(5) { yield() }
        viewModel.onPosition(12_000)
        assertEquals(40_000L, viewModel.uiState.value.pendingSeekMs)
        viewModel.onSeekHandled()

        viewModel.onPosition(tenMinutes - 30_000)
        viewModel.uiState.first { it.title == "Northbound" && it.stream != null }
        jellyfin.awaitRequests("GET", "/MediaSegments/e2")
        repeat(5) { yield() }
        viewModel.onPosition(12_000)

        assertEquals(40_000L, viewModel.uiState.value.pendingSeekMs)
    }

    @Test
    fun `resuming inside an intro with autoSkip on seeks to its end`() = runTest {
        val jellyfin = server(
            item = itemJson("m1", "Heat", year = 1995, runtimeTicks = 90 * TICKS_PER_MINUTE, positionTicks = (12_000L + RESUME_REWIND_MS) * 10_000),
            segments = Reply.json(segmentsJson(segmentJson("Intro", 10_000, 40_000))),
        )
        app.profileSettings.setAutoSkipSegments(true)

        val vm = viewModel()
        assertEquals(12_000L, assertNotNull(vm.uiState.first { it.stream != null }.stream).startPositionMs)
        while (jellyfin.requestsTo("GET", "/MediaSegments/m1").isEmpty()) yield()

        repeat(10) { yield() }
        assertEquals(40_000L, vm.uiState.first { it.pendingSeekMs != null }.pendingSeekMs)
    }

    // Signed in with the permission to manage subtitles, or without it.
    private suspend fun serverWithSubtitlePolicy(allowed: Boolean, info: String = playbackInfoJson(mediaSourceJson())): FakeJellyfin {
        val jellyfin = server(info = info)
        app.jellyfin.server("http://nas:8096").on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex","Policy":{"EnableSubtitleManagement":$allowed}}"""))
        return jellyfin
    }

    @Test
    fun `a title with no subtitles still has the list with Off when the user may search`() = runTest {
        serverWithSubtitlePolicy(allowed = true)

        val state = viewModel().uiState.first { it.stream != null && it.offersSubtitleSearch }

        assertEquals("m1", state.subtitleSearchItemId)
        assertTrue(state.hasTrackChoices)
        assertEquals(listOf(SUBTITLES_OFF), state.subtitleChoices.map { it.index })
    }

    @Test
    fun `without the permission the player offers no search`() = runTest {
        serverWithSubtitlePolicy(allowed = false)

        val viewModel = viewModel()
        val state = viewModel.uiState.first { it.stream != null }

        assertNull(withTimeoutOrNull(500.milliseconds) { viewModel.uiState.first { it.offersSubtitleSearch } })
        assertFalse(state.hasTrackChoices)
        assertEquals(emptyList(), state.subtitleChoices)
    }

    @Test
    fun `an added subtitle is turned on from where the video is and the player says so`() = runTest {
        val jellyfin = serverWithSubtitlePolicy(allowed = true)
        val viewModel = viewModel()
        val first = viewModel.uiState.first { it.stream != null }
        viewModel.onPosition(60_000)

        viewModel.onSubtitleAdded(SubtitleAdded("m1", 7, "English"))
        val state = viewModel.uiState.first { it.stream != null && it.stream?.id != first.stream?.id }

        assertEquals(60_000, state.stream?.startPositionMs)
        assertEquals(7, bodyOf(jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo").last()).getValue("SubtitleStreamIndex").jsonPrimitive.int)
        assertEquals("English subtitles added", state.notice)
    }

    @Test
    fun `a subtitle added for another episode leaves this one playing`() = runTest {
        val jellyfin = serverWithSubtitlePolicy(allowed = true)
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }

        viewModel.onSubtitleAdded(SubtitleAdded("other", 7, "English"))

        assertEquals(1, jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo").size)
        assertNull(viewModel.uiState.value.notice)
    }
}

// The collector appends while a test reads, on another thread once a request has answered: a plain list throws when that overlaps.
private class SeenStates : AbstractList<PlaybackUiState>() {
    @Volatile
    private var states = emptyList<PlaybackUiState>()

    override val size: Int get() = states.size

    override fun get(index: Int): PlaybackUiState = states[index]

    fun add(state: PlaybackUiState) {
        states = states + state
    }
}
