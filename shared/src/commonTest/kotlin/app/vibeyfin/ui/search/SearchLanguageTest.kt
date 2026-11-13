package app.vibeyfin.ui.search

import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SearchLanguageTest {
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

    private fun viewModel() = viewModels.track(SearchViewModel(app.library, app.searchHistory, app.connection, app.offline, app.offlineLibrary, app.seerr, app.seerrRepository))

    private fun hints(vararg items: String) = """{"SearchHints":[${items.joinToString(",")}],"TotalRecordCount":${items.size}}"""

    private fun hint(id: String, name: String, type: String, year: Int? = null) = """{"ItemId":"$id","Id":"$id","Name":"$name","Type":"$type","ProductionYear":${year ?: "null"},"Series":null,"ParentIndexNumber":null,"IndexNumber":null,"PrimaryImageTag":null}"""

    private suspend fun server(body: String): FakeServer = app.signedIn().also { it.on("GET", "/Search/Hints", Reply.json(body)) }

    @Test
    fun theGroupTitlesAreWrittenInTheLanguageSelectedAfterTheSearch() = runTest {
        TestLanguages.installGerman("search_movies" to "Filme")
        server(hints(hint("m1", "Neon Night", "Movie", year = 2026)))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onQueryChange("neon")
        assertEquals(listOf("Movies"), viewModel.uiState.first { it.searched == "neon" && !it.loading }.groups.map { it.title })

        Texts.select("de")
        assertEquals(listOf("Filme"), viewModel.uiState.first { state -> state.groups.map { it.title } == listOf("Filme") }.groups.map { it.title })
    }
}
