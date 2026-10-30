package app.vibeyfin.ui.children

import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.downloadItemJson
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import app.vibeyfin.testing.itemsPage
import app.vibeyfin.testing.parentJson
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
import kotlin.test.assertTrue

/** "Download season" on a season's list. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChildrenDownloadsTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()

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

    private fun viewModel(parentId: String) = viewModels.track(ChildrenViewModel(parentId, app.library, app.connection, app.downloadRepository))

    private fun episode(id: String, size: Long, played: Boolean, number: Int) = downloadItemJson(id, "Episode $number", "Episode", size = size, seriesId = "s1", seriesName = "Saga", seasonId = "se1", seasonNumber = 1, episodeNumber = number, played = played)

    // Season se1 of Saga, with two episodes, and a plain folder "folder-1" with one movie.
    private suspend fun seasonServer() {
        val server = app.signedIn()
        server.on("GET", "/Items") { request ->
            when {
                request.query["parentId"] == "se1" ->
                    Reply.json(itemsJson(itemJson("e1", "Episode 1", type = "Episode", seriesId = "s1"), itemJson("e2", "Episode 2", type = "Episode", seriesId = "s1")))

                request.query["parentId"] == "folder-1" -> Reply.json(itemsJson(itemJson("m1", "Heat")))

                request.query["ids"] == "se1" -> Reply.json(itemsJson(itemJson("se1", "Season 1", type = "Season", isFolder = true, seriesId = "s1", seriesName = "Saga")))

                request.query["ids"] == "s1,se1" -> Reply.json(itemsPage(parentJson("s1", "Saga", "Series"), parentJson("se1", "Season 1", "Season", number = 1, seriesId = "s1")))

                else -> Reply.json(itemsPage())
            }
        }
        server.on("GET", "/Shows/s1/Episodes") { Reply.json(itemsPage(episode("e1", 100, played = true, number = 1), episode("e2", 200, played = false, number = 2))) }
    }

    @Test
    fun aSeasonsListOffersToDownloadTheSeasonAndAPlainFolderDoesNot() = runTest {
        seasonServer()

        val season = viewModel("se1").uiState.first { !it.loading && it.series != null }
        val folder = viewModel("folder-1").uiState.first { !it.loading }

        assertTrue(season.offersSeasonDownload)
        assertFalse(folder.offersSeasonDownload)
    }

    @Test
    fun theSeasonAsksThenQueuesItsEpisodesAndCountsWhatIsDownloaded() = runTest {
        seasonServer()
        val viewModel = viewModel("se1")
        viewModel.uiState.first { !it.loading && it.series != null }

        viewModel.onDownloadSeason()
        val prompt = viewModel.uiState.first { it.downloadPrompt?.plan != null }.downloadPrompt!!
        assertEquals(2, prompt.count)
        assertEquals(300L, prompt.bytes)

        viewModel.onConfirmDownload()
        viewModel.uiState.first { it.downloads.size == 2 }
        assertEquals(0, viewModel.uiState.value.seasonDownloaded, "queued, not downloaded yet")

        app.downloads.complete("srv-1", "u1", "e1", 100, "srv-1/u1/e1/video.mkv", 5)
        assertEquals(1, viewModel.uiState.first { it.seasonDownloaded == 1 }.seasonDownloaded)
        assertEquals(2, viewModel.uiState.value.seasonDownloads.size)
    }

    @Test
    fun removingTheSeasonsDownloadsAsksFirst() = runTest {
        seasonServer()
        val viewModel = viewModel("se1")
        viewModel.uiState.first { !it.loading && it.series != null }
        viewModel.onDownloadSeason()
        viewModel.uiState.first { it.downloadPrompt?.plan != null }
        viewModel.onConfirmDownload()
        viewModel.uiState.first { it.downloads.size == 2 }

        viewModel.onRemoveSeasonDownloads()
        assertEquals(2, viewModel.uiState.first { it.removePrompt != null }.removePrompt!!.count)
        viewModel.onConfirmRemove()

        viewModel.uiState.first { it.downloads.isEmpty() }
    }
}
