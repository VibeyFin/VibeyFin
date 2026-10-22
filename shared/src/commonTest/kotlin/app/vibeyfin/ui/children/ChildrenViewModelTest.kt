package app.vibeyfin.ui.children

import app.vibeyfin.testing.FakeServer
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.whoswatching.SignInMessages
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChildrenViewModelTest {
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

    /** The requests for a page of a list; a season's list also reads its own item, which is not a page. */
    private fun pageRequests() = app.jellyfin.requestsTo("GET", "/Items").filter { it.query["parentId"] != null }

    private fun viewModel(parentId: String = "folder-1") =
        viewModels.track(ChildrenViewModel(parentId, app.library, app.connection))

    /** Answers `/Items` from [all], [pageSize] at a time, like the server does. */
    private fun FakeServer.listOf(all: List<String>, pageSize: Int) =
        on("GET", "/Items") { request ->
            val start = request.query["startIndex"]!!.toInt()
            val page = all.drop(start).take(pageSize)
            Reply.json(itemsJson(*page.map { itemJson(it) }.toTypedArray(), total = all.size, startIndex = start))
        }

    @Test
    fun startsLoadingAndThenShowsTheFirstPage() = runTest {
        val server = app.signedIn()
        server.listOf(listOf("a", "b"), pageSize = 60)

        val viewModel = viewModel("series-1")
        val state = viewModel.uiState.first { !it.loading }

        assertEquals(listOf("a", "b"), state.items.map { it.id })
        assertFalse(state.hasMore)
        assertNull(state.message)
        assertEquals("series-1", pageRequests().single().query["parentId"])
    }

    @Test
    fun anEmptyFolderIsSaidToBeEmptyNotLoading() = runTest {
        val server = app.signedIn()
        server.listOf(emptyList(), pageSize = 60)

        val state = viewModel().uiState.first { !it.loading }

        assertTrue(state.isEmpty)
        assertFalse(state.hasMore)
    }

    @Test
    fun theNextPageIsAddedWhenAsked() = runTest {
        val server = app.signedIn()
        server.listOf(listOf("a", "b", "c"), pageSize = 2)
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.first { !it.loading }.hasMore)

        viewModel.onLoadMore()
        val state = viewModel.uiState.first { !it.loadingMore && it.items.size == 3 }

        assertEquals(listOf("a", "b", "c"), state.items.map { it.id })
        assertFalse(state.hasMore)
        assertEquals(listOf("0", "2"), pageRequests().map { it.query["startIndex"] })
    }

    @Test
    fun askingForMoreTwiceOrWithNothingLeftAsksOnce() = runTest {
        val server = app.signedIn()
        server.listOf(listOf("a", "b", "c"), pageSize = 2)
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }

        viewModel.onLoadMore()
        viewModel.onLoadMore()
        viewModel.uiState.first { !it.loadingMore && it.items.size == 3 }
        viewModel.onLoadMore()

        assertEquals(2, pageRequests().size)
    }

    @Test
    fun aFirstPageThatFailsSaysWhatHappenedAndRetries() = runTest {
        val server = app.signedIn()
        server.listOf(listOf("a"), pageSize = 60)
        server.refuseConnections()
        val viewModel = viewModel()

        val failed = viewModel.uiState.first { !it.loading }
        assertEquals(SignInMessages.UNREACHABLE, failed.message)
        assertTrue(failed.items.isEmpty())
        assertFalse(failed.isEmpty)

        server.refuseConnections(false)
        viewModel.onRetry()
        val loaded = viewModel.uiState.first { !it.loading && it.message == null }

        assertEquals(listOf("a"), loaded.items.map { it.id })
    }

    @Test
    fun aNextPageThatFailsKeepsWhatIsThereAndRetries() = runTest {
        val server = app.signedIn()
        server.listOf(listOf("a", "b", "c"), pageSize = 2)
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }

        server.refuseConnections()
        viewModel.onLoadMore()
        val failed = viewModel.uiState.first { it.loadMoreFailed }
        assertEquals(listOf("a", "b"), failed.items.map { it.id })
        assertNull(failed.message)
        assertTrue(failed.hasMore)
        assertFalse(failed.loadingMore)

        server.refuseConnections(false)
        viewModel.onRetry()
        val loaded = viewModel.uiState.first { it.items.size == 3 }

        assertFalse(loaded.loadMoreFailed)
        assertFalse(loaded.hasMore)
    }

    @Test
    fun aServerErrorIsNamedWithItsStatus() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.status(HttpStatusCode.BadGateway))

        val state = viewModel().uiState.first { !it.loading }

        assertEquals(LoadMessages.forError(ApiError.Server(502)), state.message)
        assertTrue(state.message!!.contains("502"))
    }

    @Test
    fun aRejectedTokenSendsTheUserBackToWhoIsWatching() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.status(HttpStatusCode.Unauthorized))

        val state = viewModel().uiState.first { it.exit != null }

        assertEquals(HomeExit.WhoIsWatching("srv-1"), state.exit)
    }

    @Test
    fun beforeAnythingIsLoadedTheScreenIsLoading() = runTest {
        app.signedIn()

        // Nothing answers /Items yet; the first state is still the loading one.
        assertTrue(ChildrenUiState().loading)
        assertFalse(ChildrenUiState().isEmpty)
    }

    // A season's list leads back to its show.

    private fun FakeServer.parent(item: String) = on("GET", "/Items") { request ->
        if (request.query["ids"] != null) Reply.json(itemsJson(item)) else Reply.json(itemsJson(itemJson("e1", type = "Episode")))
    }

    @Test
    fun aSeasonsListHasAWayToItsShow() = runTest {
        app.signedIn().parent(itemJson("season-1", "Season 1", type = "Season", isFolder = true, seriesId = "show-1", seriesName = "Severance", episodeNumber = 1))

        val state = viewModel("season-1").uiState.first { !it.loading && it.series != null }

        assertEquals(SeriesLink("show-1", "Severance"), state.series)
    }

    @Test
    fun aLibraryOrFolderHasNoSuchLink() = runTest {
        val server = app.signedIn()
        server.parent(itemJson("lib-1", "Movies", type = "CollectionFolder", isFolder = true))
        val viewModel = viewModel("lib-1")

        val state = viewModel.uiState.first { !it.loading }
        // The lookup has answered by the time the second request is in.
        app.jellyfin.awaitRequests("GET", "/Items", count = 2)

        assertNull(state.series)
        assertNull(viewModel.uiState.value.series)
    }
}
