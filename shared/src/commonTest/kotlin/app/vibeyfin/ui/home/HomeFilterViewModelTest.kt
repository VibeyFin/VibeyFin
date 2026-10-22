package app.vibeyfin.ui.home

import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import io.ktor.http.HttpStatusCode
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Home's category buttons: Shows, Movies and New open their page; Categories opens a dropdown first. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeFilterViewModelTest {
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

    private fun viewModel() = viewModels.track(HomeViewModel(app.connection, app.library, app.watch, app.watchChanges, app.clock))

    private suspend fun server(
        genres: Reply = Reply.json(itemsJson(itemJson("g2", "Thriller", type = "Genre"), itemJson("g1", "Drama", type = "Genre"))),
    ): FakeServer {
        val server = app.signedIn()
        server.on("GET", "/UserViews", Reply.json(itemsJson(itemJson("lib", "Movies", type = "CollectionFolder", isFolder = true))))
        server.on("GET", "/UserItems/Resume", Reply.json(itemsJson()))
        server.on("GET", "/Shows/NextUp", Reply.json(itemsJson()))
        server.on("GET", "/Genres", genres)
        return server
    }

    private suspend fun HomeViewModel.loaded() = uiState.first { it.serverId != null && !it.loadingLibraries && !it.continueRow.loading }

    @Test
    fun homeShowsTheFourButtonsInOrderAndOnlyTwoOpenADropdown() = runTest {
        server()

        val state = viewModel().loaded()

        assertEquals(listOf("Shows", "Movies", "New", "Categories"), state.buttons.map { it.label })
        assertEquals(listOf(false, false, false, true), state.buttons.map { it.opensDropdown })
        assertNull(state.dropdown)
        assertNull(state.openList)
    }

    @Test
    fun showsMoviesAndNewOpenTheirPageAtOnce() = runTest {
        server()
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onCategory(CategoryKind.Shows)
        assertEquals(HomeFilter.Shows, viewModel.uiState.value.openList)
        viewModel.onListOpened()
        assertNull(viewModel.uiState.value.openList)

        viewModel.onCategory(CategoryKind.Movies)
        assertEquals(HomeFilter.Movies, viewModel.uiState.value.openList)
        viewModel.onListOpened()

        viewModel.onCategory(CategoryKind.New)
        assertEquals(HomeFilter.New, viewModel.uiState.value.openList)
        assertNull(viewModel.uiState.value.dropdown)
    }

    @Test
    fun theCategoriesDropdownListsGenresByNameAndPickingOneOpensItsPage() = runTest {
        server()
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onCategory(CategoryKind.Categories)
        assertNull(viewModel.uiState.value.openList)
        val dropdown = assertNotNull(viewModel.uiState.first { it.dropdown?.loading == false }.dropdown)

        assertEquals("Categories", dropdown.title)
        assertEquals(listOf("Drama", "Thriller"), dropdown.options.map { it.name })

        viewModel.onPickOption("g1")
        val state = viewModel.uiState.value

        assertEquals(HomeFilter.Genre("g1", "Drama"), state.openList)
        assertNull(state.dropdown)
    }

    @Test
    fun theCategoriesDropdownOpensAGenrePageAndIsFetchedOnce() = runTest {
        val server = server()
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onCategory(CategoryKind.Categories)
        viewModel.uiState.first { it.dropdown?.loading == false }
        viewModel.onPickOption("g1")
        assertEquals(HomeFilter.Genre("g1", "Drama"), viewModel.uiState.value.openList)
        viewModel.onListOpened()

        viewModel.onCategory(CategoryKind.Categories)
        // Opened again, the list comes from memory: it is already there and the server was asked once.
        assertEquals(listOf("Drama", "Thriller"), viewModel.uiState.value.dropdown?.options?.map { it.name })
        assertEquals(1, app.jellyfin.requestsTo("GET", "/Genres").size)
        assertTrue(server.address.isNotEmpty())
    }

    @Test
    fun aDropdownThatFailsToLoadSaysSoAndRetries() = runTest {
        val server = server(genres = Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onCategory(CategoryKind.Categories)
        val failed = assertNotNull(viewModel.uiState.first { it.dropdown?.loading == false }.dropdown)

        assertNotNull(failed.message)

        server.on("GET", "/Genres", Reply.json(itemsJson(itemJson("g1", "Drama", type = "Genre"))))
        viewModel.onRetryDropdown()
        val retried = assertNotNull(viewModel.uiState.first { it.dropdown?.options?.isNotEmpty() == true }.dropdown)

        assertNull(retried.message)
        assertEquals(listOf("Drama"), retried.options.map { it.name })
    }

    @Test
    fun anEmptyGenresListSaysSo() = runTest {
        server(genres = Reply.json(itemsJson()))
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onCategory(CategoryKind.Categories)
        val dropdown = assertNotNull(viewModel.uiState.first { it.dropdown?.loading == false }.dropdown)

        assertTrue(dropdown.isEmpty)
        assertEquals("This server has no categories.", dropdown.emptyText)
    }

    @Test
    fun dismissingTheDropdownOpensNothing() = runTest {
        server()
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onCategory(CategoryKind.Categories)
        viewModel.onDismissDropdown()

        assertNull(viewModel.uiState.value.dropdown)
        assertNull(viewModel.uiState.value.openList)
    }

    @Test
    fun homeItselfIsNeverFiltered() = runTest {
        server()
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onCategory(CategoryKind.Shows)
        val state = viewModel.uiState.value

        // The libraries and Continue watching stay as they are: the page opens over Home.
        assertEquals(1, state.libraries.size)
    }
}
