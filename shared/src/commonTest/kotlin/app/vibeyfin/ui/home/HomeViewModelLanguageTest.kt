package app.vibeyfin.ui.home

import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelLanguageTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        TestLanguages.reset()
        viewModels.stopAll()
        Dispatchers.resetMain()
        app.close()
    }

    private fun viewModel() = viewModels.track(HomeViewModel(app.connection, app.library, app.watch, app.watchChanges, app.clock, app.profileSettings, app.searchHistory, app.pins, app.auth, app.downloadRepository))

    @Test
    fun theCategoryButtonsFollowTheLanguage() = runTest {
        TestLanguages.installGerman("filter_shows" to "Serien")
        val viewModel = viewModel()
        assertEquals(Strings.filterShows, viewModel.uiState.first().buttons.first().label)

        Texts.select("de")

        assertEquals("Serien", viewModel.uiState.first { it.buttons.first().label == "Serien" }.buttons.first().label)
    }
}
