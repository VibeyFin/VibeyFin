package app.vibeyfin.ui.filtered

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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The page a category button or dropdown entry opens. */
@OptIn(ExperimentalCoroutinesApi::class)
class FilteredListViewModelTest {
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

    private fun viewModel(filter: HomeFilter) =
        viewModels.track(FilteredListViewModel(filter, app.library, app.connection))

    private fun FakeServer.listOf(all: List<String>, pageSize: Int) =
        on("GET", "/Items") { request ->
            val start = request.query["startIndex"]!!.toInt()
            val page = all.drop(start).take(pageSize)
            Reply.json(itemsJson(*page.map { itemJson(it) }.toTypedArray(), total = all.size, startIndex = start))
        }

    @Test
    fun theTitleIsTheButtonOrTheGenreName() = runTest {
        app.signedIn().listOf(emptyList(), 60)

        assertEquals("Shows", viewModel(HomeFilter.Shows).title)
        assertEquals("Movies", viewModel(HomeFilter.Movies).title)
        assertEquals("New", viewModel(HomeFilter.New).title)
        assertEquals("Action", viewModel(HomeFilter.Genre("g1", "Action")).title)
    }

    @Test
    fun aGenrePageShowsThatGenresMedia() = runTest {
        val server = app.signedIn()
        server.listOf(listOf("a", "b"), 60)

        val state = viewModel(HomeFilter.Genre("g-action", "Action")).uiState.first { !it.loading }

        assertEquals(listOf("a", "b"), state.items.map { it.id })
        assertNull(state.message)
        assertEquals("g-action", app.jellyfin.requestsTo("GET", "/Items").single().query["genreIds"])
    }

    @Test
    fun morePagesLoadAsTheEndComesIntoView() = runTest {
        val server = app.signedIn()
        server.listOf(listOf("a", "b", "c"), pageSize = 2)
        val viewModel = viewModel(HomeFilter.Movies)
        val first = viewModel.uiState.first { !it.loading }
        assertEquals(2, first.items.size)
        assertTrue(first.hasMore)

        viewModel.onLoadMore()
        val all = viewModel.uiState.first { it.items.size == 3 }

        assertFalse(all.hasMore)
        assertFalse(all.loadingMore)
    }

    @Test
    fun aPageWithNothingInItIsEmptyNotLoading() = runTest {
        app.signedIn().listOf(emptyList(), 60)

        val state = viewModel(HomeFilter.Genre("g-empty", "Western")).uiState.first { !it.loading }

        assertTrue(state.isEmpty)
    }

    @Test
    fun aFailedFirstPageShowsAMessageAndRetries() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel(HomeFilter.Shows)
        assertNotNull(viewModel.uiState.first { !it.loading }.message)

        server.listOf(listOf("a"), 60)
        viewModel.onRetry()
        val state = viewModel.uiState.first { it.items.isNotEmpty() }

        assertNull(state.message)
    }

    @Test
    fun aRejectedTokenLeavesThePage() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.status(HttpStatusCode.Unauthorized))

        val state = viewModel(HomeFilter.Movies).uiState.first { it.exit != null }

        assertNotNull(state.exit)
    }
}
