package com.liamgleeson.vibeyfin.ui.settings

import com.liamgleeson.vibeyfin.testing.TestApp
import com.liamgleeson.vibeyfin.testing.ViewModelTracker
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
    fun transcodingStartsOffForANewProfile() = runTest {
        app.signedIn(allowTranscoding = false)
        // `saveSession` makes the settings row; nothing but the user turns this on.
        val state = viewModel().uiState.first { it.hasProfile }

        assertFalse(state.allowTranscoding)
    }

    @Test
    fun theTranscodingToggleIsSavedForTheProfile() = runTest {
        app.signedIn(allowTranscoding = false)
        val viewModel = viewModel()
        viewModel.uiState.first { it.hasProfile }

        viewModel.onAllowTranscodingChanged(true)

        assertTrue(viewModel.uiState.first { it.allowTranscoding }.allowTranscoding)
        assertTrue(app.profileSettings.transcodingAllowed())
    }
}
