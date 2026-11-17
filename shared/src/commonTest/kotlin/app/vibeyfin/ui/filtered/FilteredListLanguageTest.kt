package app.vibeyfin.ui.filtered

import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
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
class FilteredListLanguageTest {
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

    private fun viewModel(filter: HomeFilter) = viewModels.track(FilteredListViewModel(filter, app.library, app.connection, app.watchChanges))

    private fun FakeServer.listOf(all: List<String>, pageSize: Int) = on("GET", "/Items") { request ->
        val start = request.query["startIndex"]!!.toInt()
        val page = all.drop(start).take(pageSize)
        Reply.json(itemsJson(*page.map { itemJson(it) }.toTypedArray(), total = all.size, startIndex = start))
    }

    private fun FilteredListUiState.movieChipLabel(): String? = chips.firstOrNull { it.kind == ListChipKind.Movies }?.label

    @Test
    fun theChipLabelsAreWrittenInTheLanguageSelectedAfterTheyWereShown() = runTest {
        TestLanguages.installGerman("filter_movies" to "Filme")
        app.signedIn().listOf(emptyList(), 60)
        val viewModel = viewModel(HomeFilter.New)
        backgroundScope.launch { viewModel.uiState.collect {} }
        assertEquals("Movies", viewModel.uiState.first { it.movieChipLabel() != null }.movieChipLabel())

        Texts.select("de")
        assertEquals("Filme", viewModel.uiState.first { it.movieChipLabel() == "Filme" }.movieChipLabel())
    }

    @Test
    fun anOpenFiltersSheetIsWrittenInTheLanguageSelectedWhileItIsOpen() = runTest {
        TestLanguages.installGerman("watched_title" to "Gesehen", "filter_any" to "Alle")
        val server = app.signedIn()
        server.listOf(emptyList(), 60)
        server.on("GET", "/UserViews", Reply.json(itemsJson()))
        server.on("GET", "/Items/Filters2", Reply.json("{}"))
        server.on("GET", "/Studios", Reply.json(itemsJson()))
        server.on("GET", "/Localization/ParentalRatings", Reply.json("[]"))
        val viewModel = viewModel(HomeFilter.Library)
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.uiState.first { !it.list.loading }
        viewModel.onChip(ListChipKind.Filters)
        viewModel.uiState.first { it.sheet?.kind == ListSheetKind.Filters && it.sheet.loading == false }

        Texts.select("de")
        val watched = viewModel.uiState.first { state -> state.sheet?.rows?.any { it.label == "Gesehen" } == true }.sheet?.rows?.first { it.id == "Watched" }

        assertEquals("Alle", watched?.detail)
    }
}
