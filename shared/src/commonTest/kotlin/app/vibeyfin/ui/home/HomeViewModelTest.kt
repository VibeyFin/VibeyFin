package app.vibeyfin.ui.home

import app.vibeyfin.ui.common.LoadMessages
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TICKS_PER_MINUTE
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import app.vibeyfin.ui.whoswatching.SignInMessages
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
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

    private fun viewModel() = viewModels.track(HomeViewModel(app.connection, app.library, app.watch, app.watchChanges, app.clock, app.profileSettings))

    private suspend fun signedIn(): FakeServer {
        val server = app.serverAt("http://nas:8096")
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex"}"""))
        server.on("POST", "/Sessions/Logout", Reply.noContent())
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.connection.activate("srv-1", "u1")
        return server
    }

    @Test
    fun nothingIsShownBeforeSignIn() = runTest {
        assertEquals(HomeUiState(), viewModel().uiState.value)
    }

    @Test
    fun showsWhoIsSignedInOnWhichServer() = runTest {
        signedIn()

        val state = viewModel().uiState.first { it.userName != null }

        assertEquals("Alex", state.userName)
        assertEquals("Home", state.serverName)
        assertEquals("srv-1", state.serverId)
        assertNull(state.exit)
    }

    @Test
    fun anEndedSessionSendsTheUserBackToWhoIsWatching() = runTest {
        app.serverAt("http://nas:8096").on("GET", "/Users/Me", Reply.status(HttpStatusCode.Unauthorized))
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.connection.activate("srv-1", "u1")

        val state = viewModel().uiState.first { it.exit != null }

        assertEquals(HomeExit.WhoIsWatching("srv-1"), state.exit)
    }

    @Test
    fun switchingProfileLeavesTheSessionSignedInAndGoesToWhoIsWatching() = runTest {
        signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }

        viewModel.onSwitchProfile()

        assertEquals(HomeExit.WhoIsWatching("srv-1"), viewModel.uiState.first { it.exit != null }.exit)
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
        assertEquals("token-1", app.accounts.sessionToken("srv-1", "u1"))
        assertEquals(0, app.jellyfin.requestsTo("POST", "/Sessions/Logout").size)
    }

    @Test
    fun switchingServerGoesToTheServersList() = runTest {
        signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }

        viewModel.onSwitchServer()

        assertEquals(HomeExit.Servers, viewModel.uiState.first { it.exit != null }.exit)
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
    }

    @Test
    fun signingOutRevokesTheTokenLocksTheProfileAndGoesToWhoIsWatching() = runTest {
        signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }

        viewModel.onSignOut()

        assertEquals(HomeExit.WhoIsWatching("srv-1"), viewModel.uiState.first { it.exit != null }.exit)
        assertEquals(1, app.jellyfin.requestsTo("POST", "/Sessions/Logout").size)
        assertNull(app.accounts.sessionToken("srv-1", "u1"))
        assertIs<ConnectionState.Disconnected>(app.connection.state.value)
    }

    @Test
    fun theMenuDoesNothingWhenNobodyIsSignedIn() = runTest {
        val viewModel = viewModel()

        viewModel.onSwitchProfile()
        viewModel.onSignOut()

        assertNull(viewModel.uiState.value.exit)
    }

    private fun FakeServer.libraries() = on(
        "GET", "/UserViews",
        Reply.json(
            itemsJson(
                itemJson("lib-1", "Movies", type = "CollectionFolder", isFolder = true),
                itemJson("lib-2", "Shows", type = "CollectionFolder", isFolder = true),
            ),
        ),
    )

    @Test
    fun listsTheLibrariesOnceSignedIn() = runTest {
        signedIn().libraries()

        val state = viewModel().uiState.first { it.libraries.isNotEmpty() }

        assertEquals(listOf("Movies", "Shows"), state.libraries.map { it.title })
        assertFalse(state.loadingLibraries)
        assertNull(state.librariesMessage)
        assertEquals("u1", app.jellyfin.requestsTo("GET", "/UserViews").single().query["userId"])
    }

    @Test
    fun asksForNothingBeforeSignIn() = runTest {
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.libraries.isEmpty())
        assertEquals(0, app.jellyfin.requestsTo("GET", "/UserViews").size)
    }

    @Test
    fun librariesThatCantBeLoadedSayWhyAndCanBeRetried() = runTest {
        val server = signedIn()
        server.libraries()
        server.refuseConnections()
        val viewModel = viewModel()

        val failed = viewModel.uiState.first { it.librariesMessage != null }
        assertEquals(SignInMessages.UNREACHABLE, failed.librariesMessage)
        assertTrue(failed.libraries.isEmpty())
        assertFalse(failed.loadingLibraries)
        // The rest of Home still works: the menu needs no server.
        assertEquals("Alex", failed.userName)

        server.refuseConnections(false)
        viewModel.onRetryLibraries()
        val loaded = viewModel.uiState.first { it.libraries.isNotEmpty() }

        assertNull(loaded.librariesMessage)
    }

    @Test
    fun aRejectedTokenWhileListingLibrariesSendsTheUserBack() = runTest {
        val server = signedIn()
        server.on("GET", "/UserViews", Reply.status(HttpStatusCode.Unauthorized))

        val state = viewModel().uiState.first { it.exit != null }

        assertEquals(HomeExit.WhoIsWatching("srv-1"), state.exit)
        assertTrue(state.libraries.isEmpty())
    }

    @Test
    fun neverShowsNoLibrariesWhileTheyAreOnTheirWay() = runTest {
        signedIn().libraries()
        val seen = mutableListOf<HomeUiState>()

        viewModel().uiState.takeWhile { it.libraries.isEmpty() }.toList(seen)

        // Before the list arrives, a signed-in Home is loading rather than telling the user it is empty.
        assertTrue(seen.filter { it.serverId != null }.all { it.loadingLibraries }, seen.toString())
    }

    @Test
    fun anAccountWithNoLibrariesEndsEmptyAndNotLoading() = runTest {
        signedIn().on("GET", "/UserViews", Reply.json(itemsJson()))

        val state = viewModel().uiState.first { it.serverId != null && !it.loadingLibraries }

        assertTrue(state.libraries.isEmpty())
        assertNull(state.librariesMessage)
    }

    private fun FakeServer.watching(resume: String = itemsJson(), nextUp: String = itemsJson()) {
        on("GET", "/UserItems/Resume", Reply.json(resume))
        on("GET", "/Shows/NextUp", Reply.json(nextUp))
    }

    private val halfWatched = itemJson(
        "e1", "Pilot", type = "Episode", seriesName = "Show", seriesId = "s1", seasonNumber = 2, episodeNumber = 5,
        runtimeTicks = 40 * TICKS_PER_MINUTE, positionTicks = 10 * TICKS_PER_MINUTE, thumbTag = "t", year = 2020,
        blurHashes = mapOf("Thumb" to mapOf("t" to "LEHV6nWB2yk8pyo0adR*.7kCMdnj")),
    )
    private val nextEpisode = itemJson(
        "e9", "Opener", type = "Episode", seriesName = "Other", seriesId = "s2", seasonNumber = 1, episodeNumber = 1,
        runtimeTicks = 40 * TICKS_PER_MINUTE,
    )

    private suspend fun HomeViewModel.loadedRow() = uiState.first { it.serverId != null && !it.continueRow.loading }.continueRow

    private suspend fun pause() = withContext(Dispatchers.Default) { delay(150) }

    @Test
    fun continueWatchingListsWhatIsInProgressThenTheNextEpisodes() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched), nextUp = itemsJson(nextEpisode))

        val row = viewModel().loadedRow()

        val (started, next) = row.cards
        assertEquals("Show", started.title)
        assertEquals("S2 E5 · 30m left", started.subtitle)
        assertEquals(0.25f, started.progress)
        assertEquals("Show, S2 E5, 25% watched", started.accessibilityLabel)
        assertEquals("http://nas:8096/Items/e1/Images/Thumb?tag=t&maxWidth=640", started.imageUrl)
        assertEquals("LEHV6nWB2yk8pyo0adR*.7kCMdnj", started.imageBlurHash)
        assertEquals("Other", next.title)
        assertEquals("S1 E1 · Opener", next.subtitle)
        // A next-up card has no bar: nothing has been watched of it.
        assertNull(next.progress)
        assertEquals("Other, S1 E1", next.accessibilityLabel)
        assertTrue(row.isVisible)
        assertNull(row.message)
    }

    @Test
    fun aMovieInProgressIsTitledByItselfWithItsYearInTheDescription() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(itemJson("m1", "Caminandes", year = 2013, runtimeTicks = 10 * TICKS_PER_MINUTE, positionTicks = 5 * TICKS_PER_MINUTE)))

        val card = viewModel().loadedRow().cards.single()

        assertEquals("Caminandes", card.title)
        assertEquals("5m left", card.subtitle)
        assertEquals("Caminandes, 2013, 50% watched", card.accessibilityLabel)
    }

    @Test
    fun aShowWithSomethingInProgressIsListedOnceInTheRow() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(
            resume = itemsJson(halfWatched),
            nextUp = itemsJson(itemJson("e2", type = "Episode", seriesName = "Show", seriesId = "s1"), nextEpisode),
        )

        val row = viewModel().loadedRow()

        assertEquals(listOf("e1", "e9"), row.cards.map { it.id })
    }

    @Test
    fun theRowShowsTheSkeletonUntilItsAnswerArrives() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched))
        val seen = mutableListOf<HomeUiState>()

        viewModel().uiState.takeWhile { it.continueRow.cards.isEmpty() }.toList(seen)

        assertTrue(seen.filter { it.serverId != null }.all { it.continueRow.loading && it.continueRow.isVisible }, seen.toString())
    }

    @Test
    fun aRowWithNothingToShowIsNotDrawn() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching()

        val row = viewModel().loadedRow()

        assertTrue(row.cards.isEmpty())
        assertFalse(row.isVisible)
    }

    @Test
    fun whenOneListFailsTheRowShowsTheOtherWithNoMessage() = runTest {
        val server = signedIn()
        server.libraries()
        server.on("GET", "/UserItems/Resume", Reply.status(HttpStatusCode.ServiceUnavailable))
        server.on("GET", "/Shows/NextUp", Reply.json(itemsJson(nextEpisode)))

        val row = viewModel().loadedRow()

        assertEquals(listOf("e9"), row.cards.map { it.id })
        assertNull(row.message)
    }

    @Test
    fun whenBothListsFailTheRowSaysWhyAndCanBeRetried() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched))
        server.refuseConnections()
        val viewModel = viewModel()

        val failed = viewModel.loadedRow()
        assertEquals(SignInMessages.UNREACHABLE, failed.message)
        assertTrue(failed.cards.isEmpty())
        assertTrue(failed.isVisible)

        server.refuseConnections(false)
        viewModel.onRetryContinue()
        val loaded = viewModel.uiState.first { it.continueRow.cards.isNotEmpty() }.continueRow

        assertNull(loaded.message)
        assertEquals(listOf("e1"), loaded.cards.map { it.id })
    }

    @Test
    fun aRejectedTokenWhileLoadingTheRowSendsTheUserBack() = runTest {
        val server = signedIn()
        server.libraries()
        server.on("GET", "/UserItems/Resume", Reply.status(HttpStatusCode.Unauthorized))
        server.on("GET", "/Shows/NextUp", Reply.status(HttpStatusCode.Unauthorized))

        val state = viewModel().uiState.first { it.exit != null }

        assertEquals(HomeExit.WhoIsWatching("srv-1"), state.exit)
    }

    @Test
    fun theRowReloadsWhenPlaybackChangesWhatWasWatched() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching()
        val viewModel = viewModel()
        viewModel.loadedRow()

        server.watching(resume = itemsJson(halfWatched))
        app.watchChanges.changed()
        val row = viewModel.uiState.first { it.continueRow.cards.isNotEmpty() }.continueRow

        assertEquals(listOf("e1"), row.cards.map { it.id })
    }

    @Test
    fun aReloadThatFailsLeavesTheCardsAsTheyWere() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched))
        val viewModel = viewModel()
        viewModel.loadedRow()

        server.refuseConnections()
        app.watchChanges.changed()
        pause()

        val row = viewModel.uiState.value.continueRow
        assertEquals(listOf("e1"), row.cards.map { it.id })
        assertNull(row.message)
    }

    @Test
    fun comingBackToTheForegroundReloadsOnlyAfterAMinute() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched))
        val viewModel = viewModel()
        viewModel.loadedRow()

        viewModel.onForeground()
        pause()
        assertEquals(1, app.jellyfin.requestsTo("GET", "/UserItems/Resume").size)

        app.clock.advance(61.seconds)
        viewModel.onForeground()

        app.jellyfin.awaitRequests("GET", "/UserItems/Resume", 2)
    }

    @Test
    fun pullToRefreshReloadsBothAndKeepsWhatIsOnScreenWhileItRuns() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched))
        val viewModel = viewModel()
        viewModel.loadedRow()

        server.watching(nextUp = itemsJson(nextEpisode))
        viewModel.onRefresh()
        val refreshed = viewModel.uiState.first { !it.refreshing && it.continueRow.cards.map { card -> card.id } == listOf("e9") }

        assertEquals(listOf("Movies", "Shows"), refreshed.libraries.map { it.title })
        assertEquals(2, app.jellyfin.requestsTo("GET", "/UserViews").size)
        assertNull(refreshed.continueRow.message)
    }

    @Test
    fun pullToRefreshDoesNothingWhenNobodyIsSignedIn() = runTest {
        val viewModel = viewModel()

        viewModel.onRefresh()
        viewModel.onForeground()
        pause()

        assertFalse(viewModel.uiState.value.refreshing)
        assertEquals(0, app.jellyfin.requests.size)
    }

    private fun CoroutineScope.collectStates(into: MutableList<HomeUiState>, viewModel: HomeViewModel) {
        launch(UnconfinedTestDispatcher(), start = CoroutineStart.UNDISPATCHED) { viewModel.uiState.collect { into += it } }
    }

    @Test
    fun markingACardWatchedRemovesItAtOnceAndSavesTheMark() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched), nextUp = itemsJson(nextEpisode))
        server.on("POST", "/UserPlayedItems/e1", Reply.noContent())
        val viewModel = viewModel()
        val states = mutableListOf<HomeUiState>()
        backgroundScope.collectStates(states, viewModel)
        viewModel.loadedRow()

        server.watching(nextUp = itemsJson(nextEpisode))
        viewModel.onMarkWatched("e1")
        val row = viewModel.uiState.first { it.continueRow.cards.map { card -> card.id } == listOf("e9") }.continueRow

        assertNull(row.message)
        app.jellyfin.awaitRequests("POST", "/UserPlayedItems/e1")
        // The change reloads the row from the server, which agrees.
        app.jellyfin.awaitRequests("GET", "/UserItems/Resume", 2)
    }

    @Test
    fun aMarkThatCantBeSavedBringsTheCardBackWhereItWasWithAMessage() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched), nextUp = itemsJson(nextEpisode))
        server.on("POST", "/UserPlayedItems/e1", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel()
        val states = mutableListOf<HomeUiState>()
        backgroundScope.collectStates(states, viewModel)
        viewModel.loadedRow()

        viewModel.onMarkWatched("e1")
        val failed = viewModel.uiState.first { it.actionMessage != null }

        assertTrue(states.any { state -> state.continueRow.cards.map { it.id } == listOf("e9") }, "the card should have gone at once")
        assertEquals(listOf("e1", "e9"), failed.continueRow.cards.map { it.id })
        assertEquals(LoadMessages.MARK_WATCHED_FAILED, failed.actionMessage)

        server.on("POST", "/UserPlayedItems/e1", Reply.noContent())
        server.watching(nextUp = itemsJson(nextEpisode))
        viewModel.onRetryAction()
        val saved = viewModel.uiState.first { it.actionMessage == null && it.continueRow.cards.map { card -> card.id } == listOf("e9") }

        assertNull(saved.actionMessage)
    }

    @Test
    fun aRejectedTokenWhileMarkingEndsTheSessionWithoutAMessage() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched))
        server.on("POST", "/UserPlayedItems/e1", Reply.status(HttpStatusCode.Unauthorized))
        val viewModel = viewModel()
        viewModel.loadedRow()

        viewModel.onMarkWatched("e1")
        val state = viewModel.uiState.first { it.exit != null }

        assertNull(state.actionMessage)
    }

    @Test
    fun markingACardThatIsNotThereDoesNothing() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched))
        val viewModel = viewModel()
        viewModel.loadedRow()

        viewModel.onMarkWatched("nope")
        pause()

        assertEquals(0, app.jellyfin.requestsTo("POST", "/UserPlayedItems/nope").size)
        assertEquals(listOf("e1"), viewModel.uiState.value.continueRow.cards.map { it.id })
    }
}
