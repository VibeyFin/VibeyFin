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
import kotlin.test.assertFalse
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

    private fun viewModel(id: String = "m1") = viewModels.track(ItemViewModel(id, app.library, app.connection, app.watchChanges))

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

    private suspend fun itemServer(item: String) =
        app.signedIn().also { it.on("GET", "/Items", Reply.json(itemsJson(item))) }

    @Test
    fun anItemInProgressOffersResumeFromWhereTheUserStoppedAndStartOver() = runTest {
        itemServer(itemJson("m1", runtimeTicks = 90 * TICKS_PER_MINUTE, positionTicks = (41 * 60 + 12) * 10_000_000L))

        val state = viewModel().uiState.first { !it.loading }

        assertEquals("Resume from 41:12", state.playLabel)
        assertTrue(state.canStartOver)
    }

    @Test
    fun anItemNotStartedOffersPlayOnly() = runTest {
        itemServer(itemJson("m1", runtimeTicks = 90 * TICKS_PER_MINUTE))

        val state = viewModel().uiState.first { !it.loading }

        assertEquals("Play", state.playLabel)
        assertFalse(state.canStartOver)
    }

    @Test
    fun aWatchedItemOffersPlayOnly() = runTest {
        itemServer(itemJson("m1", runtimeTicks = 90 * TICKS_PER_MINUTE, positionTicks = 600_000_000, played = true))

        val state = viewModel().uiState.first { !it.loading }

        assertEquals("Play", state.playLabel)
        assertFalse(state.canStartOver)
    }

    @Test
    fun theButtonsFollowWhatWasWatchedWithoutALoadingScreen() = runTest {
        val server = itemServer(itemJson("m1", runtimeTicks = 90 * TICKS_PER_MINUTE))
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }

        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("m1", runtimeTicks = 90 * TICKS_PER_MINUTE, positionTicks = 1_200_000_000))))
        app.watchChanges.changed()
        val state = viewModel.uiState.first { it.canStartOver }

        assertEquals("Resume from 2:00", state.playLabel)
        assertFalse(state.loading)
    }
}
