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
        assertEquals("series-1", app.jellyfin.requestsTo("GET", "/Items").single().query["parentId"])
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
        assertEquals(listOf("0", "2"), app.jellyfin.requestsTo("GET", "/Items").map { it.query["startIndex"] })
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

        assertEquals(2, app.jellyfin.requestsTo("GET", "/Items").size)
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
}
