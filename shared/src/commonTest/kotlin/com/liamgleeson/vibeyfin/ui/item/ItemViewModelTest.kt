package com.liamgleeson.vibeyfin.ui.item

import com.liamgleeson.vibeyfin.testing.Reply
import com.liamgleeson.vibeyfin.testing.TICKS_PER_MINUTE
import com.liamgleeson.vibeyfin.testing.TestApp
import com.liamgleeson.vibeyfin.testing.ViewModelTracker
import com.liamgleeson.vibeyfin.testing.itemJson
import com.liamgleeson.vibeyfin.testing.itemsJson
import com.liamgleeson.vibeyfin.ui.common.LoadMessages
import com.liamgleeson.vibeyfin.ui.home.HomeExit
import com.liamgleeson.vibeyfin.ui.whoswatching.SignInMessages
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ItemViewModelTest {
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

    private fun viewModel(id: String = "m1") = viewModels.track(ItemViewModel(id, app.library, app.connection))

    @Test
    fun showsTheItemWithItsFacts() = runTest {
        val server = app.signedIn()
        server.on(
            "GET", "/Items",
            Reply.json(
                itemsJson(
                    itemJson(
                        "m1", "Caminandes", year = 2013, runtimeTicks = 84 * TICKS_PER_MINUTE, overview = "A llama.",
                        genres = listOf("Animation"), communityRating = 7.3,
                    ),
                ),
            ),
        )

        val state = viewModel().uiState.first { !it.loading }

        assertEquals("Caminandes", state.item?.title)
        assertEquals("A llama.", state.item?.overview)
        assertEquals(listOf("Animation"), state.item?.genres)
        assertEquals(listOf("2013", "1 h 24 min", "★ 7.3"), state.facts)
        assertNull(state.message)
        assertEquals("m1", app.jellyfin.requestsTo("GET", "/Items").single().query["ids"])
    }

    @Test
    fun isLoadingUntilTheServerAnswers() {
        assertTrue(ItemUiState().loading)
        assertNull(ItemUiState().item)
    }

    @Test
    fun anItemThatIsGoneSaysSoInsteadOfShowingNothing() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson()))

        val state = viewModel("gone").uiState.first { !it.loading }

        assertNull(state.item)
        assertEquals(LoadMessages.NOT_FOUND, state.message)
    }

    @Test
    fun aFailureCanBeRetried() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("m1", "Caminandes"))))
        server.refuseConnections()
        val viewModel = viewModel()
        assertEquals(SignInMessages.UNREACHABLE, viewModel.uiState.first { !it.loading }.message)

        server.refuseConnections(false)
        viewModel.onRetry()
        val state = viewModel.uiState.first { !it.loading && it.item != null }

        assertEquals("Caminandes", state.item?.title)
        assertNull(state.message)
    }

    @Test
    fun aRejectedTokenSendsTheUserBackToWhoIsWatching() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.status(HttpStatusCode.Unauthorized))

        val state = viewModel().uiState.first { it.exit != null }

        assertEquals(HomeExit.WhoIsWatching("srv-1"), state.exit)
    }
}
