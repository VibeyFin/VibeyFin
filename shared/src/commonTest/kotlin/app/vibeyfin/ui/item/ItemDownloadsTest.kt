package app.vibeyfin.ui.item

import app.vibeyfin.data.db.DOWNLOAD_PAUSED
import app.vibeyfin.data.db.DOWNLOAD_QUEUED
import app.vibeyfin.data.db.DOWNLOAD_RUNNING
import app.vibeyfin.domain.DownloadNetwork
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.sagaServer
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.downloads.DownloadAction
import app.vibeyfin.ui.downloads.DownloadBadge
import app.vibeyfin.ui.downloads.actions
import app.vibeyfin.ui.whoswatching.SignInMessages
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Download button, its menu and its questions on an item's page. */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemDownloadsTest {
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

    private fun viewModel(id: String) = viewModels.track(ItemViewModel(id, app.library, app.connection, app.watch, app.watchChanges, app.downloadRepository, app.playback, app.seerrRepository, app.seerr, app.subtitles))

    @Test
    fun theDownloadButtonQueuesAMovie() = runTest {
        app.sagaServer()
        val viewModel = viewModel("m1")
        assertTrue(viewModel.uiState.first { !it.loading }.offersDownload)

        viewModel.onDownload()

        val state = viewModel.uiState.first { it.download != null }
        assertEquals("Heat", state.download!!.item.title)
        assertEquals(DOWNLOAD_QUEUED, app.downloads.find("srv-1", "u1", "m1")!!.state)
        assertEquals(listOf(DownloadNetwork.None), app.scheduler.requests)
    }

    @Test
    fun theButtonFollowsTheDownloadFromWaitingToRunningWithItsRing() = runTest {
        app.sagaServer()
        val viewModel = viewModel("m1")
        viewModel.uiState.first { !it.loading }
        viewModel.onDownload()
        assertEquals(DownloadBadge.Waiting, viewModel.uiState.first { it.download != null }.downloadBadge)

        app.downloads.setState("srv-1", "u1", "m1", DOWNLOAD_RUNNING, null, 1)
        viewModel.uiState.first { it.downloadBadge == DownloadBadge.Running }
        app.downloads.setProgress("srv-1", "u1", "m1", 25, 100, 2)
        val moved = viewModel.uiState.first { it.downloadProgress == 0.25f }

        assertEquals(DownloadBadge.Running, moved.downloadBadge)
    }

    @Test
    fun aShowAsksHowManyEpisodesAndHowMuchRoomBeforeDownloading() = runTest {
        app.sagaServer()
        val viewModel = viewModel("s1")
        viewModel.uiState.first { !it.loading }

        viewModel.onDownloadSeries()
        val prompt = viewModel.uiState.first { it.downloadPrompt?.plan != null }.downloadPrompt!!

        assertEquals(2, prompt.count)
        assertEquals(300L, prompt.bytes)
        assertTrue(prompt.canChooseUnwatched, "one of them is watched")
        assertEquals(0, app.downloads.observeAll("srv-1", "u1").first().size, "nothing is queued before the user says yes")
    }

    @Test
    fun leavingOutWatchedEpisodesChangesWhatIsQueued() = runTest {
        app.sagaServer()
        val viewModel = viewModel("s1")
        viewModel.uiState.first { !it.loading }
        viewModel.onDownloadSeries()
        viewModel.uiState.first { it.downloadPrompt?.plan != null }

        viewModel.onToggleUnwatchedOnly()
        val prompt = viewModel.uiState.first { it.downloadPrompt?.unwatchedOnly == true }.downloadPrompt!!
        assertEquals(1, prompt.count)
        assertEquals(200L, prompt.bytes)

        viewModel.onConfirmDownload()

        assertNull(viewModel.uiState.first { it.downloadPrompt == null }.downloadPrompt)
        assertEquals(listOf("e2"), app.downloads.observeAll("srv-1", "u1").first { it.isNotEmpty() }.map { it.itemId })
    }

    @Test
    fun saysNoToTheQuestionAndNothingIsQueued() = runTest {
        app.sagaServer()
        val viewModel = viewModel("s1")
        viewModel.uiState.first { !it.loading }
        viewModel.onDownloadSeries()
        viewModel.uiState.first { it.downloadPrompt?.plan != null }

        viewModel.onDismissDownloadPrompt()

        assertNull(viewModel.uiState.value.downloadPrompt)
        assertEquals(0, app.downloads.observeAll("srv-1", "u1").first().size)
    }

    @Test
    fun aSeasonAsksToo() = runTest {
        app.sagaServer()
        val viewModel = viewModel("s1")
        viewModel.uiState.first { it.seasons.seasons.isNotEmpty() }

        viewModel.onDownloadSeason("se1")
        val prompt = viewModel.uiState.first { it.downloadPrompt?.plan != null }.downloadPrompt!!

        assertEquals(2, prompt.count)
        assertEquals("se1", app.jellyfin.requestsTo("GET", "/Shows/s1/Episodes").last { it.query["seasonId"] != null }.query["seasonId"])
    }

    @Test
    fun anEpisodeInTheListCanBeDownloadedOnItsOwn() = runTest {
        app.sagaServer()
        val viewModel = viewModel("s1")
        viewModel.uiState.first { it.seasons.episodes.items.isNotEmpty() }

        viewModel.onDownloadEpisode("e1")

        assertEquals(listOf("e1"), viewModel.uiState.first { it.downloads.isNotEmpty() }.downloads.keys.toList())
        assertEquals("s1", viewModel.uiState.value.seriesDownloads.single().seriesId)
    }

    @Test
    fun aUserWhoMayNotDownloadHasNoButton() = runTest {
        val server = app.sagaServer()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Policy":{"EnableContentDownloading":false}}"""))

        val state = viewModel("m1").uiState.first { !it.loading && !it.canDownload }

        assertFalse(state.offersDownload)
    }

    @Test
    fun pausingResumingAndRemovingFollowTheDownloadsState() = runTest {
        app.sagaServer()
        val viewModel = viewModel("m1")
        viewModel.uiState.first { !it.loading }
        viewModel.onDownload()
        assertEquals(listOf(DownloadAction.Pause, DownloadAction.Remove), viewModel.uiState.first { it.download != null }.download!!.actions)

        viewModel.onDownloadAction("m1", DownloadAction.Pause)
        val paused = viewModel.uiState.first { it.download?.actions?.first() == DownloadAction.Resume }
        assertEquals(DOWNLOAD_PAUSED, app.downloads.find("srv-1", "u1", "m1")!!.state)
        assertNotNull(paused.download)

        viewModel.onDownloadAction("m1", DownloadAction.Resume)
        viewModel.uiState.first { it.download?.actions?.first() == DownloadAction.Pause }
    }

    @Test
    fun removingOneDownloadDeletesItAtOnce() = runTest {
        app.sagaServer()
        val viewModel = viewModel("m1")
        viewModel.uiState.first { !it.loading }
        viewModel.onDownload()
        viewModel.uiState.first { it.download != null }
        app.downloads.complete("srv-1", "u1", "m1", 50, "srv-1/u1/m1/video.mkv", 5)
        viewModel.uiState.first { it.download?.bytesDone == 50L }

        viewModel.onDownloadAction("m1", DownloadAction.Remove)

        viewModel.uiState.first { it.download == null }
        assertNull(app.downloads.find("srv-1", "u1", "m1"))
    }

    @Test
    fun aShowsDownloadsCanAllBeRemovedAtOnce() = runTest {
        app.sagaServer()
        val viewModel = viewModel("s1")
        viewModel.uiState.first { !it.loading }
        viewModel.onDownloadSeries()
        viewModel.uiState.first { it.downloadPrompt?.plan != null }
        viewModel.onConfirmDownload()
        viewModel.uiState.first { it.seriesDownloads.size == 2 }

        viewModel.onRemoveSeriesDownloads()
        assertEquals(2, viewModel.uiState.first { it.removePrompt != null }.removePrompt!!.count)
        viewModel.onConfirmRemove()

        viewModel.uiState.first { it.seriesDownloads.isEmpty() }
    }

    @Test
    fun aDownloadThatCouldNotBeAskedForIsSaidAndCanBeTriedAgain() = runTest {
        val server = app.sagaServer()
        val viewModel = viewModel("m1")
        viewModel.uiState.first { !it.loading }
        server.refuseConnections()

        viewModel.onDownload()
        assertEquals(SignInMessages.UNREACHABLE, viewModel.uiState.first { it.actionMessage != null }.actionMessage)

        server.refuseConnections(false)
        viewModel.onRetryAction()

        viewModel.uiState.first { it.download != null }
        assertNull(viewModel.uiState.value.actionMessage)
    }

    @Test
    fun anItemWithNoFileSaysSo() = runTest {
        val server = app.sagaServer()
        val viewModel = viewModel("m1")
        viewModel.uiState.first { !it.loading }
        server.on("GET", "/Items") { Reply.json("""{"Items":[],"TotalRecordCount":0}""") }

        viewModel.onDownload()

        assertEquals(LoadMessages.DOWNLOAD_NO_FILE, viewModel.uiState.first { it.actionMessage != null }.actionMessage)
        assertEquals(alex, ProfileRef("srv-1", "u1"))
    }
}
