package app.vibeyfin.ui.playback

import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import app.vibeyfin.testing.mediaSourceJson
import app.vibeyfin.testing.playbackInfoJson
import app.vibeyfin.testing.segmentsJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class PausedScreenTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()
    private val reporterScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Queued rather than unconfined, so the setting's database update runs on the test's scheduler, not on the database's thread.
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        reporterScope.coroutineContext.job.cancelAndJoin()
        Dispatchers.resetMain()
        app.close()
    }

    // A player showing "Heat", with the profile's dim wait set to [minutes] when given; the state is watched for the whole test.
    private suspend fun TestScope.player(minutes: Int? = null): PlaybackViewModel {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("m1", "Heat", year = 1995))))
        server.on("POST", "/Items/m1/PlaybackInfo", Reply.json(playbackInfoJson(mediaSourceJson())))
        server.on("GET", "/MediaSegments/m1", Reply.json(segmentsJson()))
        server.on("POST", "/Sessions/Playing", Reply.noContent())
        server.on("POST", "/Sessions/Playing/Progress", Reply.noContent())
        server.on("POST", "/Sessions/Playing/Stopped", Reply.noContent())
        minutes?.let { app.profileSettings.setDimWhenPausedMinutes(it) }
        val viewModel = viewModels.track(
            PlaybackViewModel(
                "m1", app.library, app.playback, app.reporter(reporterScope), app.connection, app.watch, app.profileSettings, app.binge,
                app.offlineLibrary, app.offline, app.localReporter(reporterScope), app.subtitles,
            ),
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.uiState.first { it.stream != null }
        viewModel.onPlaying(0)
        return viewModel
    }

    private fun TestScope.screenOf(viewModel: PlaybackViewModel): PlayerScreenState {
        runCurrent()
        return viewModel.uiState.value.screenState
    }

    private fun TestScope.wait(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    @Test
    fun `the screen dims once the player has been paused for the profile's wait and then may sleep`() = runTest {
        val viewModel = player()

        viewModel.onPaused(60_000)
        wait(5.minutes - 1.milliseconds)
        assertEquals(PlayerScreenState.Awake, screenOf(viewModel))

        wait(2.milliseconds)
        assertEquals(PlayerScreenState.Dimmed, screenOf(viewModel))

        wait(DIMMED_BEFORE_SLEEP)
        assertEquals(PlayerScreenState.MaySleep, screenOf(viewModel))
    }

    @Test
    fun `a playing video never dims the screen`() = runTest {
        val viewModel = player()

        wait(30.minutes)

        assertEquals(PlayerScreenState.Awake, screenOf(viewModel))
    }

    @Test
    fun `playing again wakes the screen and stops the wait`() = runTest {
        val viewModel = player()
        viewModel.onPaused(60_000)
        wait(6.minutes)

        viewModel.onPlaying(60_000)
        assertEquals(PlayerScreenState.Awake, screenOf(viewModel))

        wait(30.minutes)
        assertEquals(PlayerScreenState.Awake, screenOf(viewModel))
    }

    @Test
    fun `a touch wakes the screen and starts the wait again`() = runTest {
        val viewModel = player()
        viewModel.onPaused(60_000)
        wait(6.minutes)

        viewModel.onUserInteraction()
        assertEquals(PlayerScreenState.Awake, screenOf(viewModel))

        wait(4.minutes)
        viewModel.onUserInteraction()
        wait(4.minutes)
        assertEquals(PlayerScreenState.Awake, screenOf(viewModel))

        wait(1.minutes + 1.milliseconds)
        assertEquals(PlayerScreenState.Dimmed, screenOf(viewModel))
    }

    @Test
    fun `off never dims the screen`() = runTest {
        val viewModel = player(minutes = 0)

        viewModel.onPaused(60_000)
        wait(120.minutes)

        assertEquals(PlayerScreenState.Awake, screenOf(viewModel))
    }

    @Test
    fun `changing the wait while paused starts it again with the new length`() = runTest {
        val viewModel = player()
        val seen = mutableListOf<Pair<Long, PlayerScreenState>>()
        val wokeAgain = CompletableDeferred<Unit>()
        viewModel.onPaused(60_000)
        wait(5.minutes + 1.milliseconds)
        assertEquals(PlayerScreenState.Dimmed, screenOf(viewModel))
        // The setting arrives from the database outside virtual time, so the states are timed as they come.
        backgroundScope.launch {
            viewModel.uiState.collect {
                seen += testScheduler.currentTime to it.screenState
                if (it.screenState == PlayerScreenState.Awake) wokeAgain.complete(Unit)
            }
        }

        app.profileSettings.setDimWhenPausedMinutes(10)
        wokeAgain.await()
        wait(30.minutes)

        val wokeAt = seen.first { it.second == PlayerScreenState.Awake }.first
        val dimmedAt = seen.first { it.first > wokeAt && it.second == PlayerScreenState.Dimmed }.first
        assertEquals(10.minutes.inWholeMilliseconds, dimmedAt - wokeAt)
    }

    @Test
    fun `the screen dims when the video has ended and nothing follows on its own`() = runTest {
        val viewModel = player(minutes = 1)

        viewModel.onEnded(180_000)
        wait(1.minutes + 1.milliseconds)

        assertEquals(PlayerScreenState.Dimmed, screenOf(viewModel))
    }

    @Test
    fun `a picture-in-picture window never dims`() = runTest {
        val viewModel = player(minutes = 1)
        viewModel.onEnteredPictureInPicture(60_000)

        viewModel.onPaused(60_000)
        wait(10.minutes)

        assertEquals(PlayerScreenState.Awake, screenOf(viewModel))
    }

    @Test
    fun `the state says when to hold the screen on and when to cover the player`() = runTest {
        val viewModel = player()
        viewModel.onPaused(60_000)
        runCurrent()
        assertEquals(true to false, viewModel.uiState.value.let { it.keepsScreenOn to it.screenDimmed })

        wait(5.minutes + 1.milliseconds)
        assertEquals(true to true, viewModel.uiState.value.let { it.keepsScreenOn to it.screenDimmed })

        wait(DIMMED_BEFORE_SLEEP)
        assertEquals(false to true, viewModel.uiState.value.let { it.keepsScreenOn to it.screenDimmed })
    }

    @Test
    fun `coming back to the player wakes the screen and starts the wait again`() = runTest {
        val viewModel = player()
        viewModel.onPaused(60_000)
        wait(6.minutes)

        viewModel.onScreenShown()
        assertEquals(PlayerScreenState.Awake, screenOf(viewModel))

        wait(5.minutes + 1.milliseconds)
        assertEquals(PlayerScreenState.Dimmed, screenOf(viewModel))
    }
}
