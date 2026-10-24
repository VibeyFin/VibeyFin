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
    fun `a new profile starts with the top five genre rows and can change it`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        assertEquals(5, viewModel.uiState.first { it.hasProfile }.homeGenreRows)

        viewModel.onHomeGenreRowsChanged(0)
        assertEquals(0, viewModel.uiState.first { it.homeGenreRows == 0 }.homeGenreRows)
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
            assertEquals(5, app.profileSettings.homeGenreRows.first())
        }
    }

    @Test
    fun `the home hero is on until the profile turns it off`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.first { it.hasProfile }.showHomeHero)

        viewModel.onShowHomeHeroChanged(false)
        assertFalse(viewModel.uiState.first { !it.showHomeHero }.showHomeHero)
        viewModel.onShowHomeHeroChanged(true)
        assertTrue(viewModel.uiState.first { it.showHomeHero }.showHomeHero)
    }

    @Test
    fun theStatsToggleIsSavedForTheProfile() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        viewModel.onShowPlaybackStatsChanged(true)

        assertTrue(viewModel.uiState.first { it.showPlaybackStats }.showPlaybackStats)
    }

    @Test
    fun `skip settings default to 10 and 10 and auto-skip off`() = runTest {
        app.signedIn()
        val state = viewModel().uiState.first { it.hasProfile }

        assertEquals(10, state.skipBackSeconds)
        assertEquals(10, state.skipForwardSeconds)
        assertFalse(state.autoSkipSegments)
    }

    @Test
    fun `each skip handler is reflected in the state`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        viewModel.onSkipBackChanged(30)
        assertEquals(30, viewModel.uiState.first { it.skipBackSeconds == 30 }.skipBackSeconds)
        viewModel.onSkipForwardChanged(45)
        assertEquals(45, viewModel.uiState.first { it.skipForwardSeconds == 45 }.skipForwardSeconds)
        viewModel.onAutoSkipChanged(true)
        assertTrue(viewModel.uiState.first { it.autoSkipSegments }.autoSkipSegments)
    }

    @Test
    fun `buffer ahead defaults to 20 and follows its handler`() = runTest {
        app.signedIn()
        val viewModel = viewModel()
        assertEquals(20, viewModel.uiState.first { it.hasProfile }.bufferAheadSeconds)

        viewModel.onBufferAheadChanged(60)
        assertEquals(60, viewModel.uiState.first { it.bufferAheadSeconds == 60 }.bufferAheadSeconds)
    }

    @Test
    fun `switching profile shows that profile's skip settings or the defaults`() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u2","Name":"Sam"}"""))
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        val viewModel = viewModel()

        app.connection.activate("srv-1", "u1")
        viewModel.uiState.first { it.hasProfile }
        viewModel.onSkipBackChanged(30)
        viewModel.onSkipForwardChanged(45)
        viewModel.onAutoSkipChanged(true)
        viewModel.uiState.first { it.skipBackSeconds == 30 && it.skipForwardSeconds == 45 && it.autoSkipSegments }

        app.connection.activate("srv-1", "u2")
        val other = viewModel.uiState.first { it.skipBackSeconds == 10 }
        assertEquals(10, other.skipForwardSeconds)
        assertFalse(other.autoSkipSegments)

        app.connection.activate("srv-1", "u1")
        val back = viewModel.uiState.first { it.skipBackSeconds == 30 }
        assertEquals(45, back.skipForwardSeconds)
        assertTrue(back.autoSkipSegments)
    }
}
