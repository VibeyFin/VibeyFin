package app.vibeyfin.ui.downloads

import app.vibeyfin.data.db.DOWNLOAD_RUNNING
import app.vibeyfin.domain.DownloadAttempt
import app.vibeyfin.domain.DownloadRepository
import app.vibeyfin.domain.DownloadRunner
import app.vibeyfin.domain.DownloadState
import app.vibeyfin.domain.DownloadTarget
import app.vibeyfin.domain.NetworkState
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.sagaServer
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()
    private val alex = ProfileRef("srv-1", "u1")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        Dispatchers.resetMain()
        app.close()
    }

    private fun viewModel() = viewModels.track(DownloadsViewModel(app.connection, app.downloadRepository, app.offline, app.profileSettings))

    private suspend fun queue(target: DownloadTarget, repository: DownloadRepository = app.downloadRepository) {
        val plan = (repository.plan(alex, target) as DownloadAttempt.Success).value
        repository.enqueue(alex, plan)
    }

    @Test
    fun withNothingDownloadedTheScreenSaysSo() = runTest {
        app.sagaServer()

        val state = viewModel().uiState.first { !it.loading }

        assertTrue(state.isEmpty)
        assertNull(state.storageLine)
    }

    @Test
    fun movieAndShowsAreListedSeparatelyWithTheEpisodesInOrder() = runTest {
        app.sagaServer()
        queue(DownloadTarget.Item("m1"))
        queue(DownloadTarget.Series("s1"))

        val state = viewModel().uiState.first { it.shows.isNotEmpty() && it.movies.isNotEmpty() }

        assertEquals(listOf("Heat"), state.movies.map { it.item.title })
        val show = state.shows.single()
        assertEquals("Saga", show.title)
        assertEquals(listOf("e1", "e2"), show.episodes.map { it.itemId })
        assertEquals("2 episodes · 300 B", show.line)
        assertEquals(350L, state.bytesUsed.coerceAtLeast(0).let { show.bytes + state.movies.sumOf { m -> m.bytes } })
    }

    @Test
    fun theStatusLineSaysWhereEachDownloadIs() = runTest {
        app.sagaServer()
        queue(DownloadTarget.Item("m1"))
        val viewModel = viewModel()

        val queued = viewModel.uiState.first { it.movies.isNotEmpty() }.movies.single()
        assertEquals(Strings.downloadQueued, queued.statusLine(DownloadRunner.Waiting.Network))

        app.downloads.setState("srv-1", "u1", "m1", DOWNLOAD_RUNNING, null, 1)
        app.downloads.setProgress("srv-1", "u1", "m1", 20, 50, 2)
        val running = viewModel.uiState.first { it.movies.singleOrNull()?.state == DownloadState.Running && it.movies.single().bytesDone == 20L }.movies.single()
        assertEquals("Downloading · 20 B of 50 B", running.statusLine(DownloadRunner.Waiting.Network))

        // The finished video is being made playable on the device: the line says so, and the ring follows the conversion.
        app.speeds.setConverting("srv-1/u1/m1", 0.4f)
        val converting = viewModel.uiState.first { it.movies.singleOrNull()?.conversion != null }.movies.single()
        assertEquals("Converting for this device · 40%", converting.statusLine(DownloadRunner.Waiting.Network))
        assertEquals(Strings.converting, converting.stateWord)
        assertEquals(0.4f, converting.progress)
        app.speeds.clearConverting("srv-1/u1/m1")

        app.downloads.complete("srv-1", "u1", "m1", 50, "srv-1/u1/m1/video.mkv", 3)
        val done = viewModel.uiState.first { it.movies.singleOrNull()?.state == DownloadState.Done }.movies.single()
        assertEquals("Downloaded · 50 B", done.statusLine(DownloadRunner.Waiting.Network))
        assertEquals(listOf(DownloadAction.Remove), done.actions)
    }

    @Test
    fun aFailedDownloadSaysWhyAndCanBeResumed() = runTest {
        app.sagaServer()
        queue(DownloadTarget.Item("m1"))
        app.downloads.setState("srv-1", "u1", "m1", "failed", "NoSpace", 1)

        val failed = viewModel().uiState.first { it.movies.singleOrNull()?.state == DownloadState.Failed }.movies.single()

        assertEquals(Strings.downloadFailed, failed.statusLine(DownloadRunner.Waiting.Network))
        assertEquals(LoadMessages.DOWNLOAD_NO_SPACE, failed.failureMessage)
        assertEquals(listOf(DownloadAction.Resume, DownloadAction.Remove), failed.actions)
    }

    @Test
    fun whatAWaitingDownloadWaitsForFollowsTheNetworkAndTheSetting() = runTest {
        app.sagaServer()
        val viewModel = viewModel()

        app.network.set(NetworkState.None)
        assertEquals(DownloadRunner.Waiting.Network, viewModel.uiState.first { it.waiting == DownloadRunner.Waiting.Network }.waiting)

        app.network.set(NetworkState.Metered)
        assertEquals(DownloadRunner.Waiting.Wifi, viewModel.uiState.first { it.waiting == DownloadRunner.Waiting.Wifi }.waiting)

        app.profileSettings.setDownloadOverMobile(true)
        assertEquals(DownloadRunner.Waiting.Server, viewModel.uiState.first { it.waiting == DownloadRunner.Waiting.Server }.waiting)
    }

    @Test
    fun removingAShowAsksAndThenDeletesAllItsEpisodes() = runTest {
        app.sagaServer()
        queue(DownloadTarget.Series("s1"))
        val viewModel = viewModel()
        val show = viewModel.uiState.first { it.shows.isNotEmpty() }.shows.single()

        viewModel.onRemoveShow(show)
        val prompt = viewModel.uiState.first { it.removal != null }.removal!!
        assertEquals(2, prompt.count)
        assertEquals("Saga", prompt.title)

        viewModel.onConfirmRemove()

        assertTrue(viewModel.uiState.first { it.removal == null && it.shows.isEmpty() }.isEmpty)
    }

    @Test
    fun removingEverythingAsksFirstAndCanBeRefused() = runTest {
        app.sagaServer()
        queue(DownloadTarget.Item("m1"))
        queue(DownloadTarget.Series("s1"))
        val viewModel = viewModel()
        viewModel.uiState.first { it.shows.isNotEmpty() && it.movies.isNotEmpty() }

        viewModel.onRemoveAll()
        assertEquals(3, viewModel.uiState.first { it.removal != null }.removal!!.count)

        viewModel.onDismissRemoval()
        assertNull(viewModel.uiState.first { it.removal == null }.removal)
        assertEquals(3, app.downloads.observeAll("srv-1", "u1").first().size)

        viewModel.onRemoveAll()
        viewModel.uiState.first { it.removal != null }
        viewModel.onConfirmRemove()
        viewModel.uiState.first { it.isEmpty }
    }

    @Test
    fun theStorageLineTotalsWhatTheDownloadsTake() = runTest {
        app.sagaServer()
        queue(DownloadTarget.Item("m1"))
        app.downloads.setState("srv-1", "u1", "m1", DOWNLOAD_RUNNING, null, 1)
        app.downloads.setProgress("srv-1", "u1", "m1", 40, 50, 2)

        val state = viewModel().uiState.first { it.storageLine != null }

        assertEquals("40 B used", state.storageLine)
    }

    @Test
    fun aRunningDownloadSaysHowLongIsLeft() = runTest {
        app.sagaServer()
        queue(DownloadTarget.Item("m1"))
        val viewModel = viewModel()
        app.downloads.setState("srv-1", "u1", "m1", DOWNLOAD_RUNNING, null, 1)
        app.downloads.setProgress("srv-1", "u1", "m1", 1_000, 50_000, 2)

        // 49,000 bytes left at 100 B/s: about eight minutes.
        app.speeds.set("srv-1/u1/m1", 100)
        val running = viewModel.uiState.first { it.movies.singleOrNull()?.timeLeft != null }.movies.single()

        assertEquals("Downloading · 1 KB of 50 KB · 8m left", running.statusLine(DownloadRunner.Waiting.Network))
        assertEquals(49_000L / 100, running.timeLeft!!.inWholeSeconds)
    }

    @Test
    fun aDownloadThatIsNotRunningHasNoTimeLeft() = runTest {
        app.sagaServer()
        queue(DownloadTarget.Item("m1"))
        app.speeds.set("srv-1/u1/m1", 100)

        val queued = viewModel().uiState.first { it.movies.isNotEmpty() }.movies.single()

        assertNull(queued.timeLeft)
    }

    @Test
    fun aShortWaitIsNotRoundedUpToAMinute() {
        assertEquals("Under 1m left", Strings.downloadTimeLeft(20))
        assertEquals("2m left", Strings.downloadTimeLeft(120))
        assertEquals("1h 20m left", Strings.downloadTimeLeft(80 * 60))
    }
}
