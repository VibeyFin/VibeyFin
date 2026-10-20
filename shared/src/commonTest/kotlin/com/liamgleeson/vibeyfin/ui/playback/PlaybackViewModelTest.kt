package com.liamgleeson.vibeyfin.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.liamgleeson.vibeyfin.domain.PlaybackReporter
import com.liamgleeson.vibeyfin.testing.FakeJellyfin
import com.liamgleeson.vibeyfin.testing.HLS_PATH
import com.liamgleeson.vibeyfin.testing.Reply
import com.liamgleeson.vibeyfin.testing.TestApp
import com.liamgleeson.vibeyfin.testing.ViewModelTracker
import com.liamgleeson.vibeyfin.testing.audioStreamJson
import com.liamgleeson.vibeyfin.testing.itemJson
import com.liamgleeson.vibeyfin.testing.itemsJson
import com.liamgleeson.vibeyfin.testing.mediaSourceJson
import com.liamgleeson.vibeyfin.testing.playbackInfoJson
import com.liamgleeson.vibeyfin.testing.subtitleStreamJson
import com.liamgleeson.vibeyfin.ui.common.LoadMessages
import com.liamgleeson.vibeyfin.ui.common.Strings
import com.liamgleeson.vibeyfin.ui.home.HomeExit
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()

    /** The reporter outlives the ViewModel, as in the app, so it has a scope of its own. */
    private val reporterScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        reporterScope.cancel()
        Dispatchers.resetMain()
        app.close()
    }

    /** The waits before the tries at a stream that lost its connection, short so the tests don't wait. */
    private val retryDelays = listOf(1.milliseconds, 1.milliseconds)

    private fun viewModel(id: String = "m1") = viewModels.track(
        PlaybackViewModel(id, app.library, app.playback, PlaybackReporter(app.connection, reporterScope), app.connection, retryDelays),
    )

    /** A signed-in server that offers a direct play of "Heat", answers the reports and, for an episode test, can be given another item. */
    private suspend fun server(
        info: String = playbackInfoJson(mediaSourceJson()),
        item: String = itemJson("m1", "Heat", year = 1995),
    ): FakeJellyfin {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(item)))
        server.on("POST", "/Items/m1/PlaybackInfo", Reply.json(info))
        server.on("POST", "/Sessions/Playing", Reply.noContent())
        server.on("POST", "/Sessions/Playing/Progress", Reply.noContent())
        server.on("POST", "/Sessions/Playing/Stopped", Reply.noContent())
        return app.jellyfin
    }

    private fun bodyOf(request: com.liamgleeson.vibeyfin.testing.RecordedRequest) = Json.parseToJsonElement(request.body).jsonObject

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
    fun showsTheShowAndEpisodeForAnEpisode() = runTest {
        server(
            item = itemJson("m1", "Northbound", type = "Episode", seriesName = "Paper Harbour", seasonNumber = 2, episodeNumber = 4),
        )

        val state = viewModel().uiState.first { it.title.isNotEmpty() }

        assertEquals("Northbound", state.title)
        assertEquals("Paper Harbour  ·  S2 E4", state.subtitle)
    }

    @Test
    fun aMissingTitleDoesNotStopPlayback() = runTest {
        val jellyfin = server()
        app.jellyfin.server("http://nas:8096").on("GET", "/Items", Reply.status(HttpStatusCode.InternalServerError))

        val state = viewModel().uiState.first { it.stream != null }

        assertEquals("", state.title)
        assertNotNull(state.stream)
        assertTrue(jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo").isNotEmpty())
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
            "POST", "/Items/m1/PlaybackInfo",
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

    /** Plays on a device that can't direct play, then fails [times] more times with [failure] on the transcode. */
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
                override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
                    PlaybackViewModel("m1", app.library, app.playback, PlaybackReporter(app.connection, reporterScope), app.connection) as T
            },
        )[PlaybackViewModel::class]
        viewModel.uiState.first { it.stream != null }
        viewModel.onPlaying(0)
        viewModel.onPosition(12_000)

        store.clear()

        val stopped = bodyOf(jellyfin.awaitRequests("POST", "/Sessions/Playing/Stopped").single())
        assertEquals(120_000_000L, stopped.getValue("PositionTicks").jsonPrimitive.long)
    }

    // -- audio and subtitle selection --

    private val tracks = listOf(
        audioStreamJson(1, "English - AAC", language = "eng"),
        audioStreamJson(2, "Spanish - AAC", language = "spa"),
        subtitleStreamJson(3, "English subtitles", language = "eng"),
        subtitleStreamJson(4, "Spanish subtitles", language = "spa"),
        subtitleStreamJson(5, "Image subtitles", delivery = "Encode", codec = "hdmv_pgs_subtitle"),
    )

    /**
     * A server that answers like Jellyfin does: the user's tracks come back as the selected ones only
     * when the request names the media source, otherwise it picks ([audioDefault], [subtitleDefault]).
     * With [transcode], or when direct play is turned off, the source has to be streamed.
     */
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

    /** A stream is on offer and it is not the one with [id]: the player has been given a new one. */
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
}
