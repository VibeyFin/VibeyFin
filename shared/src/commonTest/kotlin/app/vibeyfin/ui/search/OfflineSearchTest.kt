package app.vibeyfin.ui.search

import app.vibeyfin.domain.NetworkState
import app.vibeyfin.domain.model.SearchKind
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.downloaded
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

/** Search with no connection: it covers what is downloaded. */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSearchTest {
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

    private fun viewModel() = viewModels.track(SearchViewModel(app.library, app.searchHistory, app.connection, app.offline, app.offlineLibrary, app.seerr, app.seerrRepository))

    private suspend fun offlineWithDownloads() {
        val server = app.signedIn()
        server.on("GET", "/Search/Hints", Reply.json("""{"SearchHints":[],"TotalRecordCount":0}"""))
        app.downloaded("m1", "Heat", cast = listOf("Al Pacino"))
        app.downloaded("e1", "Pilot", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 1, episode = 1)
        app.network.set(NetworkState.None)
        server.refuseConnections()
    }

    @Test
    fun offlineItSearchesWhatIsDownloadedWithoutAskingTheServer() = runTest {
        offlineWithDownloads()
        val viewModel = viewModel()

        viewModel.onQueryChange("heat")
        val state = viewModel.uiState.first { it.searched == "heat" }

        assertTrue(state.offline)
        assertEquals(listOf("m1"), state.groups.single().results.map { it.id })
        assertEquals(SearchKind.Movie, state.groups.single().kind)
        assertEquals(emptyList(), app.jellyfin.callLog().filter { it.contains("Search") })
    }

    @Test
    fun aShowsNameFindsItsEpisodes() = runTest {
        offlineWithDownloads()
        val viewModel = viewModel()

        viewModel.onQueryChange("saga")
        val state = viewModel.uiState.first { it.searched == "saga" }

        assertEquals(SearchKind.Episode, state.groups.single().kind)
        assertEquals("Saga · S1 E1", state.groups.single().results.single().subtitle)
    }

    @Test
    fun nothingDownloadedThatMatchesIsNoResults() = runTest {
        offlineWithDownloads()
        val viewModel = viewModel()

        viewModel.onQueryChange("zzz")

        assertTrue(viewModel.uiState.first { it.searched == "zzz" }.noResults)
    }

    @Test
    fun theFilterButtonsOnlyOfferWhatCanBeFoundOffline() = runTest {
        offlineWithDownloads()
        val viewModel = viewModel()

        val offline = viewModel.uiState.first { it.offline }.chips.map { it.filter }

        assertEquals(listOf(SearchFilter.All, SearchFilter.Movies, SearchFilter.Episodes), offline)
    }

    @Test
    fun onlineTheFullSetOfButtonsIsThere() = runTest {
        app.signedIn()

        val chips = viewModel().uiState.first { !it.offline }.chips

        assertEquals(6, chips.size)
        assertFalse(chips.isEmpty())
    }

    @Test
    fun goingOfflineSearchesTheSameTextOnTheDeviceAgain() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Search/Hints", Reply.json("""{"SearchHints":[],"TotalRecordCount":0}"""))
        app.downloaded("m1", "Heat")
        val viewModel = viewModel()
        viewModel.onQueryChange("heat")
        viewModel.uiState.first { it.searched == "heat" && it.groups.isEmpty() }

        app.offline.setManual(true)

        val state = viewModel.uiState.first { it.offline && it.groups.isNotEmpty() }
        assertEquals(listOf("m1"), state.groups.single().results.map { it.id })
    }
}
