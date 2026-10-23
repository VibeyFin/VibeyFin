package app.vibeyfin.ui.item

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TICKS_PER_MINUTE
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

    private fun viewModel(id: String = "m1") = viewModels.track(ItemViewModel(id, app.library, app.connection, app.watch, app.watchChanges))

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

        assertEquals("Resume · 49m left", state.playLabel)
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

        assertEquals("Resume · 1h 28m left", state.playLabel)
        assertFalse(state.loading)
    }

    private fun played(played: Boolean? = null, position: Long? = null) =
        itemJson("m1", runtimeTicks = 90 * TICKS_PER_MINUTE, positionTicks = position, played = played)

    private fun CoroutineScope.collectStates(into: MutableList<ItemUiState>, viewModel: ItemViewModel) {
        launch(UnconfinedTestDispatcher(), start = CoroutineStart.UNDISPATCHED) { viewModel.uiState.collect { into += it } }
    }

    @Test
    fun markingWatchedShowsItAtOnceDropsTheResumeAndSavesIt() = runTest {
        val server = itemServer(played(position = 600_000_000))
        // Like the real server: once marked, the item reads as watched with nothing to resume.
        server.on("POST", "/UserPlayedItems/m1") {
            server.on("GET", "/Items", Reply.json(itemsJson(played(played = true))))
            Reply.json("""{"Played":true}""")
        }
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }

        viewModel.onTogglePlayed()
        val shown = viewModel.uiState.first { it.item?.played == true }

        assertTrue(shown.watched)
        assertEquals("Mark as unwatched", shown.markLabel)
        assertEquals("Play", shown.playLabel)
        assertFalse(shown.canStartOver)
        app.jellyfin.awaitRequests("POST", "/UserPlayedItems/m1")
    }

    @Test
    fun markingUnwatchedDeletesTheMark() = runTest {
        val server = itemServer(played(played = true))
        // Like the real server: once the mark is gone, the item reads as unwatched.
        server.on("DELETE", "/UserPlayedItems/m1") {
            server.on("GET", "/Items", Reply.json(itemsJson(played(played = false))))
            Reply.json("""{"Played":false}""")
        }
        val viewModel = viewModel()
        assertEquals("Mark as unwatched", viewModel.uiState.first { !it.loading }.markLabel)

        viewModel.onTogglePlayed()

        app.jellyfin.awaitRequests("DELETE", "/UserPlayedItems/m1")
        assertEquals("Mark as watched", viewModel.uiState.first { it.item?.played == false }.markLabel)
    }

    @Test
    fun aMarkThatCantBeSavedIsTakenBackWithAMessageAndCanBeRetried() = runTest {
        val server = itemServer(played())
        server.on("POST", "/UserPlayedItems/m1", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel()
        val states = mutableListOf<ItemUiState>()
        backgroundScope.collectStates(states, viewModel)
        viewModel.uiState.first { !it.loading }

        viewModel.onTogglePlayed()
        val failed = viewModel.uiState.first { it.actionMessage != null }

        // It was shown first, then taken back.
        assertTrue(states.any { it.item?.played == true })
        assertEquals(LoadMessages.MARK_WATCHED_FAILED, failed.actionMessage)
        assertEquals(false, failed.item?.played)
        assertFalse(failed.watched)

        server.on("POST", "/UserPlayedItems/m1", Reply.json("""{"Played":true}"""))
        // A save that goes through reloads the page from the server, which now agrees; without this the reload would
        // read the old mark back and race the assertion below.
        server.on("GET", "/Items", Reply.json(itemsJson(played(played = true))))
        viewModel.onRetryAction()
        val saved = viewModel.uiState.first { it.item?.played == true }

        assertNull(saved.actionMessage)
    }

    @Test
    fun aRejectedTokenEndsTheSessionWithoutAMessage() = runTest {
        val server = itemServer(played())
        server.on("POST", "/UserPlayedItems/m1", Reply.status(HttpStatusCode.Unauthorized))
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }

        viewModel.onTogglePlayed()
        val state = viewModel.uiState.first { it.exit != null }

        assertNull(state.actionMessage)
    }

    @Test
    fun aSecondTapWhileTheFirstIsBeingSavedIsIgnored() = runTest {
        val server = itemServer(played())
        server.on("POST", "/UserPlayedItems/m1", Reply.json("""{"Played":true}"""))
        server.on("DELETE", "/UserPlayedItems/m1", Reply.json("""{"Played":false}"""))
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }

        viewModel.onTogglePlayed()
        viewModel.onTogglePlayed()
        app.jellyfin.awaitRequests("POST", "/UserPlayedItems/m1")
        delay(100)

        assertEquals(0, app.jellyfin.requestsTo("DELETE", "/UserPlayedItems/m1").size)
    }

    @Test
    fun theMessageCanBeDismissed() = runTest {
        val server = itemServer(played())
        server.on("POST", "/UserPlayedItems/m1", Reply.status(HttpStatusCode.Forbidden))
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }
        viewModel.onTogglePlayed()
        viewModel.uiState.first { it.actionMessage != null }

        viewModel.onDismissMessage()

        assertNull(viewModel.uiState.first { it.actionMessage == null }.actionMessage)
    }
}
