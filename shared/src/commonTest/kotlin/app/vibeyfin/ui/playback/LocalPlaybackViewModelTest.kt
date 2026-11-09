package app.vibeyfin.ui.playback

import app.vibeyfin.domain.EngineCapabilities
import app.vibeyfin.domain.NetworkState
import app.vibeyfin.domain.PlaybackCapabilities
import app.vibeyfin.domain.PlaybackEngineChoice
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.downloaded
import app.vibeyfin.ui.common.LoadMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Playing what is downloaded: from the file, with or without the server, and what that does to where the user is. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalPlaybackViewModelTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()
    private val reporterScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val alex = ProfileRef("srv-1", "u1")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        reporterScope.coroutineContext.job.cancelAndJoin()
        Dispatchers.resetMain()
        app.close()
    }

    private fun viewModel(id: String = "m1", startOver: Boolean = false, countdownStep: Duration = 1.milliseconds) = viewModels.track(
        PlaybackViewModel(
            id, app.library, app.playback, app.reporter(reporterScope), app.connection, app.watch, app.profileSettings, app.binge,
            app.offlineLibrary, app.offline, app.localReporter(reporterScope, interval = 20.milliseconds), app.subtitles,
            listOf(1.milliseconds, 1.milliseconds), startOver, true, countdownStep,
        ),
    )

    // Signed in as Alex, the server there but with nothing for playback; then the network is taken away when [offline].
    private suspend fun signedIn(offline: Boolean): FakeServer {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json("""{"Items":[],"TotalRecordCount":0}"""))
        server.on("POST", "/UserItems/m1/UserData", Reply.noContent())
        server.on("POST", "/UserPlayedItems/m1", Reply.json("""{"Played":true}"""))
        if (offline) {
            app.network.set(NetworkState.None)
            server.refuseConnections()
        }
        return server
    }

    // What was asked of the server since the profile was signed in (that sign-in is the one `/Users/Me`).
    private suspend fun requests() = app.jellyfin.callLog().filter { it != "GET /Users/Me" }

    // Waits, in real time, for [condition]: the reporter works on its own threads.
    private suspend fun until(condition: suspend () -> Boolean) {
        withContext(Dispatchers.Default) {
            repeat(300) {
                if (condition()) return@withContext
                delay(10)
            }
        }
        assertTrue(condition(), "never became true")
    }

    @Test
    fun aDownloadPlaysFromItsFileWithNoServerAtAll() = runTest {
        val server = signedIn(offline = true)
        app.downloaded("m1", "Heat", subtitle = true)

        val state = viewModel().uiState.first { !it.loading }

        val stream = assertNotNull(state.stream)
        assertEquals("file:///fake/srv-1/u1/m1/video.mkv", stream.url)
        assertTrue(stream.headers.isEmpty())
        assertTrue(stream.audioInPlayer)
        assertEquals("file:///fake/srv-1/u1/m1/subs/3.srt", stream.subtitleFiles.single().url)
        assertEquals("Heat", state.title)
        assertEquals(listOf("English", "German"), state.audioTracks.map { it.label })
        assertEquals(listOf(SUBTITLES_OFF, 3), state.subtitleTracks.map { it.index })
        assertEquals(emptyList(), requests(), "not one request (server ${server.address})")
    }

    @Test
    fun aDownloadOnlyTheSecondPlayerOpensPlaysInIt() = runTest {
        signedIn(offline = true)
        val avPlayer = PlaybackCapabilities(containers = listOf("mp4"), videoCodecs = listOf("h264"), audioCodecs = listOf("aac"))
        val aether = PlaybackCapabilities(containers = listOf("mp4", "mkv"), videoCodecs = listOf("h264"), audioCodecs = listOf("aac"))
        app.capabilities.engines = listOf(EngineCapabilities(PlaybackEngineChoice.Native, avPlayer), EngineCapabilities(PlaybackEngineChoice.Aether, aether))
        app.downloaded("m1")

        val stream = viewModel().uiState.first { it.stream != null }.stream!!

        assertEquals(PlaybackEngineChoice.Aether, stream.engine)
    }

    @Test
    fun itStartsWhereTheDeviceSaysTheUserStoppedFiveSecondsBack() = runTest {
        signedIn(offline = true)
        app.downloaded("m1", positionMs = 42_000)

        val stream = viewModel().uiState.first { it.stream != null }.stream!!

        assertEquals(37_000L, stream.startPositionMs)
    }

    @Test
    fun startOverStartsAtTheBeginning() = runTest {
        signedIn(offline = true)
        app.downloaded("m1", positionMs = 42_000)

        assertEquals(0L, viewModel(startOver = true).uiState.first { it.stream != null }.stream!!.startPositionMs)
    }

    @Test
    fun somethingThatIsNotDownloadedSaysSoWhenOffline() = runTest {
        signedIn(offline = true)

        val state = viewModel("m1").uiState.first { !it.loading }

        assertEquals(LoadMessages.NOT_DOWNLOADED, state.message)
        assertEquals(null, state.stream)
    }

    @Test
    fun onlineTheServersRecordOfWhereTheUserStoppedWinsButTheFileIsStillPlayed() = runTest {
        val server = signedIn(offline = false)
        server.on(
            "GET",
            "/Items",
            Reply.json("""{"Items":[{"Id":"m1","Name":"Heat","Type":"Movie","RunTimeTicks":1000000000,"UserData":{"PlaybackPositionTicks":600000000,"Played":false}}],"TotalRecordCount":1}"""),
        )
        app.downloaded("m1", "Heat", positionMs = 10_000)

        val stream = viewModel().uiState.first { it.stream != null }.stream!!

        assertEquals(55_000L, stream.startPositionMs, "the server says 60 s, five back")
        assertTrue(stream.url.startsWith("file:///"))
        assertEquals(0, app.jellyfin.requestsTo("POST", "/Items/m1/PlaybackInfo").size, "nothing to negotiate: the file is here")
    }

    @Test
    fun onlineButUnreachablePlaysFromTheDevicesOwnRecord() = runTest {
        val server = signedIn(offline = false)
        server.refuseConnections()
        app.downloaded("m1", positionMs = 42_000)

        val stream = viewModel().uiState.first { it.stream != null }.stream!!

        assertEquals(37_000L, stream.startPositionMs)
    }

    @Test
    fun stoppingSavesWhereTheUserGotToOnTheDevice() = runTest {
        signedIn(offline = true)
        app.downloaded("m1", runtimeMs = 100_000)
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }

        viewModel.onPlaying(0)
        viewModel.onClosed(50_000)

        until { app.pending.progress(alex, "m1") != null }
        val record = app.pending.progress(alex, "m1")!!
        assertEquals(500_000_000L, record.positionTicks)
        assertEquals(false, record.played)
        assertEquals(50_000L, app.offlineLibrary.playable(alex, "m1")!!.item.resumePositionMs)
        until { app.progressScheduler.scheduled > 0 }
    }

    @Test
    fun theProgressGoesOnWhilePlayingSoACrashLosesLittle() = runTest {
        signedIn(offline = true)
        app.downloaded("m1", runtimeMs = 100_000)
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }

        viewModel.onPlaying(0)
        viewModel.onPosition(30_000)

        // Not stopped yet: the ticker saves it on its own.
        until { app.pending.progress(alex, "m1")?.positionTicks == 300_000_000L }
    }

    @Test
    fun playingToTheEndMarksItWatched() = runTest {
        signedIn(offline = true)
        app.downloaded("m1", runtimeMs = 100_000)
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }

        viewModel.onPlaying(0)
        viewModel.onEnded(100_000)

        until { app.pending.progress(alex, "m1")?.played == true }
        assertEquals(0L, app.pending.progress(alex, "m1")!!.positionTicks)
    }

    @Test
    fun withTheServerThereTheProgressIsSentAtOnce() = runTest {
        val server = signedIn(offline = false)
        server.on("GET", "/Items", Reply.json("""{"Items":[{"Id":"m1","Name":"Heat","Type":"Movie","UserData":{"PlaybackPositionTicks":0,"Played":false}}],"TotalRecordCount":1}"""))
        app.downloaded("m1", runtimeMs = 100_000)
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }

        viewModel.onPlaying(0)
        viewModel.onClosed(50_000)

        assertEquals(1, app.jellyfin.awaitRequests("POST", "/UserItems/m1/UserData").size)
        until { app.pending.progress(alex, "m1") == null }
    }

    @Test
    fun nothingIsReportedToTheServerAsAPlaySession() = runTest {
        signedIn(offline = false)
        app.downloaded("m1", runtimeMs = 100_000)
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }

        viewModel.onPlaying(0)
        viewModel.onPosition(10_000)
        viewModel.onClosed(10_000)
        // The position is saved, then synced at once: the sync looks the item up first (the fake has none, so it drops the record).
        // The record is only there for a moment, so the lookup is what shows the whole stop was handled.
        until { app.jellyfin.requestsTo("GET", "/Items").isNotEmpty() }

        assertEquals(0, app.jellyfin.requestsTo("POST", "/Sessions/Playing").size)
        assertEquals(0, app.jellyfin.requestsTo("POST", "/Sessions/Playing/Progress").size)
        assertEquals(0, app.jellyfin.requestsTo("POST", "/Sessions/Playing/Stopped").size)
    }

    @Test
    fun anEpisodeGoesOnIntoTheNextOneOnTheDevice() = runTest {
        signedIn(offline = true)
        app.downloaded("e1", "One", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 1, episode = 1, runtimeMs = 600_000)
        app.downloaded("e3", "Three", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 1, episode = 3, runtimeMs = 600_000)
        val viewModel = viewModel("e1")
        viewModel.uiState.first { it.stream != null }
        viewModel.onPlaying(0)

        viewModel.onEnded(600_000)

        val next = viewModel.uiState.first { it.stream?.url?.endsWith("/e3/video.mkv") == true }
        assertEquals("Three", next.title)
        until { app.pending.progress(alex, "e1")?.played == true }
    }

    @Test
    fun theLastDownloadedEpisodeEndsThePlayer() = runTest {
        signedIn(offline = true)
        app.downloaded("e1", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 1, episode = 1, runtimeMs = 600_000)
        val viewModel = viewModel("e1")
        viewModel.uiState.first { it.stream != null }
        viewModel.onPlaying(0)

        viewModel.onEnded(600_000)

        assertTrue(viewModel.uiState.first { it.finished }.finished)
    }

    @Test
    fun aFileThatWillNotPlayIsAnErrorNotATranscode() = runTest {
        signedIn(offline = true)
        app.downloaded("m1")
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }

        viewModel.onFailed(PlaybackFailure.Unsupported)

        val state = viewModel.uiState.first { it.message != null }
        assertEquals(LoadMessages.PLAYBACK_FAILED, state.message)
        assertEquals(emptyList(), requests())
    }

    @Test
    fun aSubtitleFromTheFileIsSwitchedInThePlayer() = runTest {
        signedIn(offline = true)
        app.downloaded("m1", subtitle = true)
        val viewModel = viewModel()
        viewModel.uiState.first { it.stream != null }

        viewModel.onSelectSubtitle(3)

        val state = viewModel.uiState.first { it.subtitleTracks.firstOrNull { track -> track.selected }?.index == 3 }
        assertEquals(1, state.stream!!.id, "the same stream: nothing was opened again")
        assertEquals(emptyList(), requests())
    }
}
