package app.vibeyfin.ui.search

import app.vibeyfin.domain.SeerrConnectResult
import app.vibeyfin.domain.SeerrState
import app.vibeyfin.domain.model.RequestState
import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.testing.FakeSeerr
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.seerr.SeerrMessages
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The "Not in your library" group: Seerr's answer next to the library's (issue #14). */
@OptIn(ExperimentalCoroutinesApi::class)
class SeerrSearchTest {
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

    private fun viewModel(withSeerr: Boolean = true) = viewModels.track(
        if (withSeerr) {
            SearchViewModel(app.library, app.searchHistory, app.connection, app.offline, app.offlineLibrary, app.seerr, app.seerrRepository)
        } else {
            SearchViewModel(app.library, app.searchHistory, app.connection, app.offline, app.offlineLibrary)
        },
    )

    private fun hints(vararg items: String) = """{"SearchHints":[${items.joinToString(",")}],"TotalRecordCount":${items.size}}"""

    private fun hint(id: String, name: String, type: String) = """{"ItemId":"$id","Id":"$id","Name":"$name","Type":"$type"}"""

    private val seerrResults = """{"page":1,"totalPages":1,"totalResults":4,"results":[
        {"id":1,"mediaType":"movie","title":"Harbour Bridge","releaseDate":"2025-03-01"},
        {"id":2,"mediaType":"tv","name":"Harbour Nights","firstAirDate":"2022-01-01","mediaInfo":{"status":2}},
        {"id":3,"mediaType":"movie","title":"Harbour Lights","releaseDate":"2019-05-05","mediaInfo":{"status":5,"jellyfinMediaId":"jf-3"}},
        {"id":4,"mediaType":"person","name":"Harbour Person"}]}"""

    // Alex signed in to Jellyfin (answering [libraryHints]) and, silently, to a Seerr answering searches with [seerrResults].
    private suspend fun signedInWithSeerr(libraryHints: String = hints()): Pair<FakeServer, FakeSeerr> {
        val jellyfin = app.signedIn()
        jellyfin.on("GET", "/Search/Hints", Reply.json(libraryHints))
        val seerr = FakeSeerr(app.jellyfin.server("http://seerr:5055"), jellyfin)
        seerr.onSignedIn("GET", "/api/v1/search") { Reply.json(seerrResults) }
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { app.seerr.state.first { it == SeerrState.NotSet } } }
        assertEquals(SeerrConnectResult.Connected, app.seerr.connect("http://seerr:5055"))
        return jellyfin to seerr
    }

    private suspend fun SearchViewModel.filtered(filter: SearchFilter): SearchUiState {
        onFilter(filter)
        return withContext(Dispatchers.Default) { withTimeout(5.seconds) { uiState.first { it.filter == filter } } }
    }

    private suspend fun SearchViewModel.typed(text: String): SearchUiState {
        onQueryChange(text)
        return withContext(Dispatchers.Default) { withTimeout(5.seconds) { uiState.first { !it.loading && it.searched == text && it.seerr !is SeerrSearch.Searching } } }
    }

    @Test
    fun seerrTitlesNotInTheLibraryComeAfterTheLibrarysGroups() = runTest {
        signedInWithSeerr(hints(hint("m1", "Harbour", "Movie")))
        val viewModel = viewModel()

        val state = viewModel.typed("harbour")

        assertEquals(listOf(Strings.searchMovies), state.visibleGroups.map { it.title })
        val found = assertIs<SeerrSearch.Found>(state.seerrGroup)
        // Seerr's people and the titles it knows are in the library are left out.
        assertEquals(listOf("Harbour Bridge", "Harbour Nights"), found.titles.map { it.title })
        assertEquals(RequestState.Requested, found.titles[1].state)
        assertFalse(state.libraryNoMatch)
        assertFalse(state.noResults)
        assertEquals("query=harbour&page=1", app.jellyfin.requestsTo("GET", "/api/v1/search").single().encodedQuery)
    }

    @Test
    fun whenTheLibraryHasNothingSeerrsTitlesShowUnderALineThatSaysSo() = runTest {
        signedInWithSeerr()
        val viewModel = viewModel()

        val state = viewModel.typed("harbour")

        assertTrue(state.visibleGroups.isEmpty())
        assertTrue(state.libraryNoMatch)
        assertFalse(state.noResults)
        assertEquals(2, assertIs<SeerrSearch.Found>(state.seerrGroup).titles.size)
    }

    @Test
    fun whenNeitherHasAnythingThePageSaysNothingMatches() = runTest {
        val (_, seerr) = signedInWithSeerr()
        seerr.onSignedIn("GET", "/api/v1/search") { Reply.json("""{"page":1,"totalPages":1,"totalResults":0,"results":[]}""") }
        val viewModel = viewModel()

        val state = viewModel.typed("nothing")

        assertTrue(state.noResults)
        assertNull(state.seerrGroup)
        assertFalse(state.libraryNoMatch)
    }

    @Test
    fun theLibraryNeverWaitsForSeerr() = runTest {
        val (_, seerr) = signedInWithSeerr(hints(hint("m1", "Harbour", "Movie")))
        val gate = seerr.server.hold("GET", "/api/v1/search")
        val viewModel = viewModel()

        viewModel.onQueryChange("harbour")
        val waiting = withContext(Dispatchers.Default) { withTimeout(5.seconds) { viewModel.uiState.first { it.searched == "harbour" && !it.loading } } }

        assertEquals(1, waiting.visibleGroups.size)
        assertEquals(SeerrSearch.Searching, waiting.seerrGroup)
        gate.open()
        assertIs<SeerrSearch.Found>(withContext(Dispatchers.Default) { withTimeout(5.seconds) { viewModel.uiState.first { it.seerrGroup is SeerrSearch.Found } } }.seerrGroup)
    }

    @Test
    fun aSeerrThatFailsSaysSoInItsGroupAndTryAgainAsksItAlone() = runTest {
        val (_, seerr) = signedInWithSeerr(hints(hint("m1", "Harbour", "Movie")))
        seerr.onSignedIn("GET", "/api/v1/search") { Reply.status(HttpStatusCode.InternalServerError) }
        val viewModel = viewModel()

        val failed = viewModel.typed("harbour")

        assertEquals(SeerrSearch.Failed(SeerrMessages.UNREACHABLE_NOW), failed.seerrGroup)
        assertEquals(1, failed.visibleGroups.size)
        assertNull(failed.message)

        seerr.onSignedIn("GET", "/api/v1/search") { Reply.json(seerrResults) }
        val libraryCalls = app.jellyfin.requestsTo("GET", "/Search/Hints").size
        viewModel.onSeerrRetry()

        val found = withContext(Dispatchers.Default) { withTimeout(5.seconds) { viewModel.uiState.first { it.seerrGroup is SeerrSearch.Found } } }
        assertEquals(2, assertIs<SeerrSearch.Found>(found.seerrGroup).titles.size)
        assertEquals(libraryCalls, app.jellyfin.requestsTo("GET", "/Search/Hints").size)
    }

    @Test
    fun theFiltersNarrowSeerrsGroupToo() = runTest {
        signedInWithSeerr(hints(hint("m1", "Harbour", "Movie")))
        val viewModel = viewModel()
        assertTrue(viewModel.typed("harbour").chips.any { it.filter == SearchFilter.NotInLibrary })

        assertEquals(listOf(SeerrMediaType.Movie), (viewModel.filtered(SearchFilter.Movies).seerrGroup as SeerrSearch.Found).titles.map { it.type })

        val shows = viewModel.filtered(SearchFilter.Shows)
        assertEquals(listOf("Harbour Nights"), (shows.seerrGroup as SeerrSearch.Found).titles.map { it.title })
        // The library had no show: Seerr's group stands alone, under the line.
        assertTrue(shows.libraryNoMatch)

        assertNull(viewModel.filtered(SearchFilter.People).seerrGroup)

        val onlySeerr = viewModel.filtered(SearchFilter.NotInLibrary)
        assertTrue(onlySeerr.visibleGroups.isEmpty())
        assertEquals(2, (onlySeerr.seerrGroup as SeerrSearch.Found).titles.size)
        assertFalse(onlySeerr.libraryNoMatch)
    }

    @Test
    fun withoutSeerrSearchIsAsBeforeWithNoGroupAndNoFilter() = runTest {
        app.signedIn().on("GET", "/Search/Hints", Reply.json(hints()))
        val viewModel = viewModel(withSeerr = false)

        val state = viewModel.typed("harbour")

        assertNull(state.seerrGroup)
        assertTrue(state.noResults)
        assertFalse(state.chips.any { it.filter == SearchFilter.NotInLibrary })
        assertTrue(app.jellyfin.requests.none { it.path.startsWith("/api/v1/search") })
    }

    @Test
    fun disconnectingSeerrTakesItsGroupAndFilterAway() = runTest {
        signedInWithSeerr(hints(hint("m1", "Harbour", "Movie")))
        val viewModel = viewModel()
        viewModel.typed("harbour")
        viewModel.filtered(SearchFilter.NotInLibrary)

        app.seerr.disconnect()

        val state = withContext(Dispatchers.Default) { withTimeout(5.seconds) { viewModel.uiState.first { !it.seerrReady } } }
        assertNull(state.seerrGroup)
        assertEquals(SearchFilter.All, state.filter)
        assertFalse(state.chips.any { it.filter == SearchFilter.NotInLibrary })
        assertEquals(1, state.visibleGroups.size)
    }

    @Test
    fun aSeerrPostersLinesAreWorded() {
        assertEquals("2025 · Movie", Strings.seerrTitleMeta(2025, SeerrMediaType.Movie))
        assertEquals("Show", Strings.seerrTitleMeta(null, SeerrMediaType.Show))
        assertEquals("Harbour Bridge, 2025 · Movie, not in your library, can be requested", Strings.seerrTitleDescription("Harbour Bridge", "2025 · Movie", RequestState.NotRequested))
        assertEquals("Harbour Nights, Show, partly available", Strings.seerrTitleDescription("Harbour Nights", "Show", RequestState.PartlyAvailable))
    }
}
