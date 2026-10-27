package app.vibeyfin.ui.home

import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.downloaded
import app.vibeyfin.testing.downloadedShow
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
import kotlin.test.assertTrue

/** Home from the device alone. */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineHomeViewModelTest {
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

    private fun viewModel() = viewModels.track(OfflineHomeViewModel(app.connection, app.offlineLibrary))

    @Test
    fun withNothingDownloadedItSaysSo() = runTest {
        app.signedIn()

        val state = viewModel().uiState.first { !it.loading }

        assertTrue(state.isEmpty)
        assertEquals("Alex", state.userName)
    }

    @Test
    fun itHasContinueWatchingAndTheMoviesAndShowsOnTheDevice() = runTest {
        app.signedIn()
        app.downloaded("half", "Half Seen", positionMs = 30_000)
        app.downloaded("m1", "Heat")
        app.downloaded("e1", "Pilot", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 1, episode = 1)
        app.downloadedShow("s1", "Saga")

        val state = viewModel().uiState.first { it.rows.size == 2 }

        assertEquals(listOf("half"), state.continueRow.cards.map { it.id })
        assertEquals(listOf("Movies", "Shows"), state.rows.map { it.title })
        assertEquals(listOf("Half Seen", "Heat"), state.rows[0].items.map { it.title })
        assertEquals(listOf("Saga"), state.rows[1].items.map { it.title })
        assertEquals(false, state.isEmpty)
    }

    @Test
    fun aRowWithNothingInItIsNotThere() = runTest {
        app.signedIn()
        app.downloaded("m1", "Heat")

        val state = viewModel().uiState.first { it.rows.isNotEmpty() }

        assertEquals(listOf("Movies"), state.rows.map { it.title })
        assertEquals(false, state.continueRow.isVisible)
    }

    @Test
    fun theListFollowsWhatIsDownloaded() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.isEmpty }

        app.downloaded("m1", "Heat")

        assertEquals(listOf("Heat"), viewModel.uiState.first { it.rows.isNotEmpty() }.rows.single().items.map { it.title })
    }
}
