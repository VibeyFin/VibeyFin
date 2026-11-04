package app.vibeyfin.ui.home

import app.vibeyfin.data.db.DOWNLOAD_PAUSED
import app.vibeyfin.data.db.DOWNLOAD_QUEUED
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TICKS_PER_MINUTE
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.downloadItemJson
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import app.vibeyfin.testing.itemsPage
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.PinReason
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.downloads.DownloadAction
import app.vibeyfin.ui.downloads.actions
import app.vibeyfin.ui.whoswatching.SignInMessages
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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

    private fun viewModel() = viewModels.track(HomeViewModel(app.connection, app.library, app.watch, app.watchChanges, app.clock, app.profileSettings, app.searchHistory, app.pins, app.auth, app.downloadRepository))

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
    fun `switching profile asks for the PIN when the switch is on`() = runTest {
        signedIn()
        app.setPin("u1", "1234")
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }

        viewModel.onSwitchProfile()
        assertEquals(PinReason.SwitchProfile, viewModel.uiState.first { it.pinPrompt != null }.pinPrompt!!.reason)
        assertIs<ConnectionState.Connected>(app.connection.state.value)

        viewModel.onSubmitPin("1234")
        assertEquals(HomeExit.WhoIsWatching("srv-1"), viewModel.uiState.first { it.exit != null }.exit)
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
    }

    @Test
    fun `switching server asks for the current profile's PIN`() = runTest {
        signedIn()
        app.setPin("u1", "1234")
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }

        viewModel.onSwitchServer()
        assertEquals(PinReason.SwitchProfile, viewModel.uiState.first { it.pinPrompt != null }.pinPrompt!!.reason)
        assertIs<ConnectionState.Connected>(app.connection.state.value)

        viewModel.onSubmitPin("1234")
        assertEquals(HomeExit.Servers, viewModel.uiState.first { it.exit != null }.exit)
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
    }

    @Test
    fun `a kids profile cannot sign out from Home`() = runTest {
        signedIn()
        app.profileSettings.setKidsProfile(true)
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.first { it.userName != null && it.kidsProfile }.kidsProfile)

        viewModel.onSignOut()

        assertEquals("token-1", app.accounts.sessionToken("srv-1", "u1"))
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
    fun signingOutForgetsWhatTheProfileSearchedForButSwitchingProfileKeepsIt() = runTest {
        signedIn()
        val viewModel = viewModel()
        viewModel.uiState.first { it.userName != null }
        app.searchHistory.record("heat")

        viewModel.onSwitchProfile()
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.connection.activate("srv-1", "u1")
        assertEquals(listOf("heat"), app.searchHistory.entries.first())

        val again = viewModel()
        again.uiState.first { it.userName != null }
        again.onSignOut()
        again.uiState.first { it.exit != null }
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-2")
        app.connection.activate("srv-1", "u1")

        assertEquals(emptyList(), app.searchHistory.entries.first())
    }

    @Test
    fun theMenuDoesNothingWhenNobodyIsSignedIn() = runTest {
        val viewModel = viewModel()

        viewModel.onSwitchProfile()
        viewModel.onSignOut()

        assertNull(viewModel.uiState.value.exit)
    }

    private fun FakeServer.libraries() = on(
        "GET",
        "/UserViews",
        Reply.json(
            itemsJson(
                itemJson("lib-1", "Movies", type = "CollectionFolder", isFolder = true),
                itemJson("lib-2", "Shows", type = "CollectionFolder", isFolder = true),
            ),
        ),
    )

    private fun HomeUiState.latestRows() = rows.filter { it.id.startsWith("latest:") }

    @Test
    fun eachLibraryGetsARecentlyAddedRowOnceSignedIn() = runTest {
        signedIn().libraries()

        val state = viewModel().uiState.first { it.latestRows().isNotEmpty() }

        assertEquals(listOf("Recently added in Movies", "Recently added in Shows"), state.latestRows().map { it.title })
        assertFalse(state.loadingLibraries)
        assertEquals("u1", app.jellyfin.requestsTo("GET", "/UserViews").single().query["userId"])
    }

    @Test
    fun asksForNothingBeforeSignIn() = runTest {
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.rows.isEmpty())
        assertEquals(0, app.jellyfin.requestsTo("GET", "/UserViews").size)
    }

    @Test
    fun librariesThatCantBeLoadedShowOneRecentlyAddedRowThatSaysWhyAndCanBeRetried() = runTest {
        val server = signedIn()
        server.libraries()
        server.refuseConnections()
        val viewModel = viewModel()

        val failed = viewModel.uiState.first { it.latestRows().any { row -> row.message != null } }
        val row = failed.latestRows().single()
        assertEquals("Recently added", row.title)
        assertEquals(SignInMessages.UNREACHABLE, row.message)
        assertFalse(row.loading)
        assertNull(row.seeAll)
        assertFalse(failed.loadingLibraries)
        // The rest of Home still works: the menu needs no server.
        assertEquals("Alex", failed.userName)

        server.refuseConnections(false)
        viewModel.onRetryRow(row.id)
        val loaded = viewModel.uiState.first { it.latestRows().any { r -> r.seeAll != null } }

        assertEquals(listOf("Recently added in Movies", "Recently added in Shows"), loaded.latestRows().map { it.title })
    }

    @Test
    fun aRejectedTokenWhileListingLibrariesSendsTheUserBack() = runTest {
        val server = signedIn()
        server.on("GET", "/UserViews", Reply.status(HttpStatusCode.Unauthorized))

        val state = viewModel().uiState.first { it.exit != null }

        assertEquals(HomeExit.WhoIsWatching("srv-1"), state.exit)
    }

    @Test
    fun aSignedInHomeIsLoadingUntilTheLibrariesArrive() = runTest {
        signedIn().libraries()
        val seen = mutableListOf<HomeUiState>()

        viewModel().uiState.takeWhile { it.latestRows().isEmpty() }.toList(seen)

        // Before the list arrives, a signed-in Home holds grey rows rather than showing nothing where its rows will be.
        assertTrue(seen.filter { it.serverId != null }.all { it.loadingLibraries }, seen.toString())
    }

    @Test
    fun anAccountWithNoLibrariesEndsEmptyAndNotLoading() = runTest {
        signedIn().on("GET", "/UserViews", Reply.json(itemsJson()))

        val state = viewModel().uiState.first { it.serverId != null && !it.loadingLibraries }

        // No libraries: no Recently added rows, and no failed one either.
        assertTrue(state.latestRows().isEmpty())
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
        "e9",
        "Opener",
        type = "Episode",
        seriesName = "Other",
        seriesId = "s2",
        seasonNumber = 1,
        episodeNumber = 1,
        runtimeTicks = 40 * TICKS_PER_MINUTE,
    )

    // The same episode as [halfWatched] with its position cleared, as Next Up names it.
    private val notStartedEpisode = itemJson("e1", "Pilot", type = "Episode", seriesName = "Show", seriesId = "s1", seasonNumber = 2, episodeNumber = 5, runtimeTicks = 40 * TICKS_PER_MINUTE)

    private suspend fun HomeViewModel.loadedRow() = uiState.first { it.serverId != null && !it.continueRow.loading }.continueRow

    // Lets anything the ViewModel started run to its end. The fake server answers inline, so there is nothing to wait for in real time.
    private suspend fun settle() = repeat(5) { yield() }

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
        settle()

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
        settle()
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

        assertEquals(listOf("Recently added in Movies", "Recently added in Shows"), refreshed.latestRows().map { it.title })
        assertEquals(2, app.jellyfin.requestsTo("GET", "/UserViews").size)
        assertNull(refreshed.continueRow.message)
    }

    @Test
    fun pullToRefreshDoesNothingWhenNobodyIsSignedIn() = runTest {
        val viewModel = viewModel()

        viewModel.onRefresh()
        viewModel.onForeground()
        settle()

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
    fun removingACardInProgressClearsItsPositionAndStaysGoneWhenTheEpisodeIsNextUp() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched), nextUp = itemsJson(nextEpisode))
        server.on("POST", "/UserItems/e1/UserData", Reply.noContent())
        val viewModel = viewModel()
        viewModel.loadedRow()
        // With its position at 0 the server names the same episode as next up.
        server.watching(nextUp = itemsJson(notStartedEpisode, nextEpisode))

        viewModel.onRemoveFromContinueWatching("e1")
        val removed = viewModel.uiState.first { it.canUndo }

        assertEquals(Strings.removedFromContinueWatching, removed.actionMessage)
        assertEquals(Strings.undo, removed.actionLabel)
        val body = app.jellyfin.awaitRequests("POST", "/UserItems/e1/UserData").single().body
        assertEquals(0L, Json.parseToJsonElement(body).jsonObject.getValue("PlaybackPositionTicks").jsonPrimitive.long)
        assertFalse("Played" in Json.parseToJsonElement(body).jsonObject, "the played state should be left alone")
        assertTrue(app.jellyfin.requestsTo("DELETE", "/UserPlayedItems/e1").isEmpty())

        viewModel.onRefresh()
        settle()
        assertEquals(listOf("e9"), viewModel.uiState.value.continueRow.cards.map { it.id })
    }

    @Test
    fun undoPutsTheCardAndItsPositionBack() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched), nextUp = itemsJson(nextEpisode))
        server.on("POST", "/UserItems/e1/UserData", Reply.noContent())
        val viewModel = viewModel()
        viewModel.loadedRow()

        viewModel.onRemoveFromContinueWatching("e1")
        viewModel.uiState.first { it.canUndo }
        viewModel.onRetryAction()
        val back = viewModel.uiState.first { !it.canUndo && it.continueRow.cards.any { card -> card.id == "e1" } }

        assertNull(back.actionMessage)
        val bodies = app.jellyfin.awaitRequests("POST", "/UserItems/e1/UserData", 2).map { Json.parseToJsonElement(it.body).jsonObject.getValue("PlaybackPositionTicks").jsonPrimitive.long }
        assertEquals(listOf(0L, 10 * TICKS_PER_MINUTE), bodies)
    }

    @Test
    fun aRemovalThatCantBeSavedBringsTheCardBackAndCanBeTriedAgain() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched), nextUp = itemsJson(nextEpisode))
        server.on("POST", "/UserItems/e1/UserData", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel()
        val states = mutableListOf<HomeUiState>()
        backgroundScope.collectStates(states, viewModel)
        viewModel.loadedRow()

        viewModel.onRemoveFromContinueWatching("e1")
        val failed = viewModel.uiState.first { it.actionMessage != null }

        assertTrue(states.any { state -> state.continueRow.cards.map { it.id } == listOf("e9") }, "the card should have gone at once")
        assertEquals(listOf("e1", "e9"), failed.continueRow.cards.map { it.id })
        assertEquals(LoadMessages.REMOVE_FROM_CONTINUE_FAILED, failed.actionMessage)
        assertEquals(Strings.tryAgain, failed.actionLabel)

        server.on("POST", "/UserItems/e1/UserData", Reply.noContent())
        server.watching(nextUp = itemsJson(nextEpisode))
        viewModel.onRetryAction()

        assertEquals(listOf("e9"), viewModel.uiState.first { it.canUndo }.continueRow.cards.map { it.id })
    }

    @Test
    fun anUndoThatCantBeSavedTakesTheCardOutAgainWithAMessage() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched))
        server.on("POST", "/UserItems/e1/UserData", Reply.noContent())
        val viewModel = viewModel()
        viewModel.loadedRow()
        viewModel.onRemoveFromContinueWatching("e1")
        viewModel.uiState.first { it.canUndo }

        server.on("POST", "/UserItems/e1/UserData", Reply.status(HttpStatusCode.ServiceUnavailable))
        viewModel.onRetryAction()
        val failed = viewModel.uiState.first { it.actionMessage == LoadMessages.RESTORE_TO_CONTINUE_FAILED }

        assertFalse(failed.canUndo)
        assertTrue(failed.continueRow.cards.none { it.id == "e1" })
    }

    @Test
    fun aRejectedTokenWhileRemovingEndsTheSessionWithoutAMessage() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched))
        server.on("POST", "/UserItems/e1/UserData", Reply.status(HttpStatusCode.Unauthorized))
        val viewModel = viewModel()
        viewModel.loadedRow()

        viewModel.onRemoveFromContinueWatching("e1")
        val state = viewModel.uiState.first { it.exit != null }

        assertNull(state.actionMessage)
        assertFalse(state.canUndo)
    }

    @Test
    fun aNextUpCardIsHiddenOnTheDeviceAndComesBackWhenNextUpMovesOn() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(nextUp = itemsJson(nextEpisode))
        val viewModel = viewModel()
        viewModel.loadedRow()

        viewModel.onRemoveFromContinueWatching("e9")
        viewModel.uiState.first { it.canUndo }
        settle()

        assertTrue(app.jellyfin.requestsTo("POST", "/UserItems/e9/UserData").isEmpty(), "the server should not be changed")
        viewModel.onRefresh()
        settle()
        assertTrue(viewModel.uiState.value.continueRow.cards.isEmpty())

        val later = itemJson("e10", "Second", type = "Episode", seriesName = "Other", seriesId = "s2", seasonNumber = 1, episodeNumber = 2, runtimeTicks = 40 * TICKS_PER_MINUTE)
        server.watching(nextUp = itemsJson(later))
        viewModel.onRefresh()
        settle()
        assertEquals(listOf("e10"), viewModel.uiState.value.continueRow.cards.map { it.id })

        // Next Up having moved on, the old episode is no longer hidden if it is ever named again.
        server.watching(nextUp = itemsJson(nextEpisode))
        viewModel.onRefresh()
        settle()
        assertEquals(listOf("e9"), viewModel.uiState.value.continueRow.cards.map { it.id })
    }

    @Test
    fun undoOnANextUpCardShowsItAgain() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(nextUp = itemsJson(nextEpisode))
        val viewModel = viewModel()
        viewModel.loadedRow()

        viewModel.onRemoveFromContinueWatching("e9")
        viewModel.uiState.first { it.canUndo }
        viewModel.onRetryAction()
        settle()
        viewModel.onRefresh()
        settle()

        assertEquals(listOf("e9"), viewModel.uiState.value.continueRow.cards.map { it.id })
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
        settle()

        assertEquals(0, app.jellyfin.requestsTo("POST", "/UserPlayedItems/nope").size)
        assertEquals(listOf("e1"), viewModel.uiState.value.continueRow.cards.map { it.id })
    }

    // What the download plan reads for the card: the same episode, with its file.
    private fun FakeServer.episodeFile() = on(
        "GET",
        "/Items",
        Reply.json(itemsPage(downloadItemJson("e1", "Pilot", "Episode", seriesId = "s1", seriesName = "Show", seasonNumber = 2, episodeNumber = 5))),
    )

    @Test
    fun aContinueCardCanBeDownloadedFromItsMenu() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched), nextUp = itemsJson(nextEpisode))
        server.episodeFile()
        val viewModel = viewModel()
        viewModel.loadedRow()

        viewModel.onDownload("e1")

        val state = viewModel.uiState.first { it.downloads.containsKey("e1") }
        assertEquals("Pilot", state.downloads.getValue("e1").item.title)
        assertEquals(DOWNLOAD_QUEUED, app.downloads.find("srv-1", "u1", "e1")!!.state)
    }

    @Test
    fun aCardsDownloadCanBePausedFromItsMenuAndRemovingAsksFirst() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched), nextUp = itemsJson(nextEpisode))
        server.episodeFile()
        val viewModel = viewModel()
        viewModel.loadedRow()
        viewModel.onDownload("e1")
        assertEquals(listOf(DownloadAction.Pause, DownloadAction.Remove), viewModel.uiState.first { it.downloads.containsKey("e1") }.downloads.getValue("e1").actions)

        viewModel.onDownloadAction("e1", DownloadAction.Pause)
        viewModel.uiState.first { it.downloads["e1"]?.actions?.firstOrNull() == DownloadAction.Resume }
        assertEquals(DOWNLOAD_PAUSED, app.downloads.find("srv-1", "u1", "e1")!!.state)

        viewModel.onDownloadAction("e1", DownloadAction.Remove)
        assertEquals(listOf("e1"), viewModel.uiState.first { it.removePrompt != null }.removePrompt!!.itemIds)
        assertTrue(app.downloads.find("srv-1", "u1", "e1") != null, "nothing is deleted before the user says yes")
        viewModel.onDismissRemoval()
        assertNull(viewModel.uiState.first { it.removePrompt == null }.removePrompt)
    }

    @Test
    fun aUserWhoMayNotDownloadIsToldSoForTheMenu() = runTest {
        val server = signedIn()
        server.libraries()
        server.watching(resume = itemsJson(halfWatched))
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex","Policy":{"EnableContentDownloading":false}}"""))
        val viewModel = viewModel()
        viewModel.loadedRow()

        assertFalse(viewModel.uiState.first { !it.canDownload }.canDownload)
    }
}
