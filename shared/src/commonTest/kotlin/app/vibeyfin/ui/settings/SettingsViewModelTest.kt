package app.vibeyfin.ui.settings

import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
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

    private fun viewModel() = viewModels.track(SettingsViewModel(app.preferences, app.profileSettings))

    @Test
    fun alwaysAskStartsOff() = runTest {
        assertFalse(viewModel().uiState.first().alwaysAskWhoIsWatching)
    }

    @Test
    fun theToggleIsSavedOnTheDevice() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.first()

        viewModel.onAlwaysAskChanged(true)

        assertTrue(viewModel.uiState.first { it.alwaysAskWhoIsWatching }.alwaysAskWhoIsWatching)
        assertTrue(app.preferences.alwaysAskWhoIsWatching.first())
    }

    @Test
    fun theToggleCanBeTurnedOffAgain() = runTest {
        app.preferences.setAlwaysAskWhoIsWatching(true)
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.first { it.alwaysAskWhoIsWatching }.alwaysAskWhoIsWatching)

        viewModel.onAlwaysAskChanged(false)

        assertEquals(false, viewModel.uiState.first { !it.alwaysAskWhoIsWatching }.alwaysAskWhoIsWatching)
    }

    @Test
    fun withNobodySignedInThereAreNoProfileSettings() = runTest {
        assertFalse(viewModel().uiState.first().hasProfile)
    }

    @Test
    fun `hasProfile is true for a signed-in profile`() = runTest {
        app.signedIn()

        assertTrue(viewModel().uiState.first { it.hasProfile }.hasProfile)
    }

    @Test
    fun `defaults are stats off and three in a row`() = runTest {
        app.signedIn()
        val state = viewModel().uiState.first { it.hasProfile }

        assertFalse(state.showPlaybackStats)
        assertEquals(3, state.bingeThreshold)
    }

    @Test
    fun `changing the threshold is stored per profile`() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u2","Name":"Sam"}"""))
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        val viewModel = viewModel()

        app.connection.activate("srv-1", "u1")
        viewModel.uiState.first { it.hasProfile }
        viewModel.onBingeThresholdChanged(5)
        viewModel.uiState.first { it.bingeThreshold == 5 }

        app.connection.activate("srv-1", "u2")
        assertEquals(3, app.profileSettings.bingeThreshold.first())
        viewModel.onBingeThresholdChanged(0)
        viewModel.uiState.first { it.bingeThreshold == 0 }

        app.connection.activate("srv-1", "u1")
        assertEquals(5, app.profileSettings.bingeThreshold.first())
    }

    @Test
    fun `a threshold outside 0 or 2 to 5 is ignored`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        for (invalid in listOf(1, 6, -1, 100)) {
            viewModel.onBingeThresholdChanged(invalid)
            assertEquals(3, app.profileSettings.bingeThreshold.first())
        }
    }

    @Test
    fun `home shows every genre until the profile limits them`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        assertEquals(0, viewModel.uiState.first { it.hasProfile }.homeGenreRows)

        viewModel.onHomeGenreRowsChanged(5)
        assertEquals(5, viewModel.uiState.first { it.homeGenreRows == 5 }.homeGenreRows)
        viewModel.onHomeGenreRowsChanged(-1)
        assertEquals(-1, viewModel.uiState.first { it.homeGenreRows == -1 }.homeGenreRows)
    }

    @Test
    fun `a genre row count that is not one of the choices is ignored`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        for (invalid in listOf(1, 4, 7, 21, -2)) {
            viewModel.onHomeGenreRowsChanged(invalid)
            assertEquals(0, app.profileSettings.homeGenreRows.first())
        }
    }

    @Test
    fun theStatsToggleIsSavedForTheProfile() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        viewModel.onShowPlaybackStatsChanged(true)

        assertTrue(viewModel.uiState.first { it.showPlaybackStats }.showPlaybackStats)
    }
}
