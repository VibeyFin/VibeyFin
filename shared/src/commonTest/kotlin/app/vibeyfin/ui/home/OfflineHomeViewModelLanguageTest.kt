package app.vibeyfin.ui.home

import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.downloaded
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
class OfflineHomeViewModelLanguageTest {
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

    private fun viewModel() = viewModels.track(OfflineHomeViewModel(app.connection, app.offlineLibrary))

    @Test
    fun theRowTitleFollowsTheLanguage() = runTest {
        app.signedIn()
        app.downloaded("m1", "Heat")
        TestLanguages.installGerman("filter_movies" to "Filme")
        val viewModel = viewModel()
        assertEquals(Strings.filterMovies, viewModel.uiState.first { it.rows.isNotEmpty() }.rows.single().title)

        Texts.select("de")

        assertEquals("Filme", viewModel.uiState.first { it.rows.singleOrNull()?.title == "Filme" }.rows.single().title)
    }
}
