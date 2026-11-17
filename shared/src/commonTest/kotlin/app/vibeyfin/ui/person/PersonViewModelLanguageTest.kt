package app.vibeyfin.ui.person

import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
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
class PersonViewModelLanguageTest {
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

    @Test
    fun theRowTitlesAreWordedAgainWhenTheLanguageChanges() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("p1", "Alexander Burger", type = "Person"))))
        TestLanguages.installGerman("collection_movies" to "Filme")
        val viewModel = viewModels.track(PersonViewModel("p1", app.library, app.connection))

        assertEquals("Movies", viewModel.uiState.first { !it.loading }.movies.title)

        Texts.select("de")
        assertEquals("Filme", viewModel.uiState.first { it.movies.title == "Filme" }.movies.title)
    }
}
