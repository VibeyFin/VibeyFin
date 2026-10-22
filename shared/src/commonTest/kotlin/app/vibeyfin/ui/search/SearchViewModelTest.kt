package app.vibeyfin.ui.search

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.domain.model.SearchKind
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.home.HomeExit
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
class SearchViewModelTest {
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

    private fun viewModel() = viewModels.track(SearchViewModel(app.library, app.connection))

    private fun hints(vararg items: String) = """{"SearchHints":[${items.joinToString(",")}],"TotalRecordCount":${items.size}}"""

    private fun hint(id: String, name: String, type: String, year: Int? = null, series: String? = null, season: Int? = null, episode: Int? = null, tag: String? = null) =
        """{"ItemId":"$id","Id":"$id","Name":"$name","Type":"$type","ProductionYear":${year ?: "null"},"Series":${series?.let { "\"$it\"" } ?: "null"},"ParentIndexNumber":${season ?: "null"},"IndexNumber":${episode ?: "null"},"PrimaryImageTag":${tag?.let { "\"$it\"" } ?: "null"}}"""

    private suspend fun server(body: String = hints()) = app.signedIn().also { it.on("GET", "/Search/Hints", Reply.json(body)) }

    @Test
    fun anEmptyFieldInvitesASearchAndAsksNothing() = runTest {
        server()
        val viewModel = viewModel()

        val state = viewModel.uiState.value

        assertTrue(state.showsPrompt)
        assertEquals(0, app.jellyfin.requestsTo("GET", "/Search/Hints").size)
    }

    @Test
    fun resultsAreGroupedMoviesShowsEpisodesPeopleCollectionsAndOtherTypesAreLeftOut() = runTest {
        server(
            hints(
                hint("p1", "Neon Person", "Person"),
                hint("c1", "Neon Collection", "BoxSet", year = 2026),
                hint("m1", "Neon Night", "Movie", year = 2026, tag = "t"),
                hint("e1", "Pilot", "Episode", series = "Neon Show", season = 1, episode = 4),
                hint("s1", "Neon Show", "Series", year = 2020),
                hint("x1", "Neon", "Studio"),
            ),
        )
        val viewModel = viewModel()

        viewModel.onQueryChange("neon")
        val state = viewModel.uiState.first { it.searched == "neon" && !it.loading }

        assertEquals(listOf(SearchKind.Movie, SearchKind.Show, SearchKind.Episode, SearchKind.Person, SearchKind.Collection), state.groups.map { it.kind })
        assertEquals(listOf("Movies", "Shows", "Episodes", "People", "Collections"), state.groups.map { it.title })
        assertEquals("2026", state.groups[0].results.single().subtitle)
        assertEquals("Neon Show · S1 E4", state.groups[2].results.single().subtitle)
        assertNull(state.groups[3].results.single().subtitle)
        assertTrue(state.groups[0].results.single().imageUrl!!.contains("/Items/m1/Images/Primary?tag=t"))
    }

    @Test
    fun theSearchAsksForTheTermAndTheFiveKinds() = runTest {
        server()
        val viewModel = viewModel()

        viewModel.onQueryChange("  neon ")
        viewModel.uiState.first { it.searched == "neon" }

        val request = app.jellyfin.requestsTo("GET", "/Search/Hints").single().query
        assertEquals("neon", request["searchTerm"])
        assertEquals("Movie,Series,Episode,Person,BoxSet", request["includeItemTypes"])
        assertEquals("40", request["limit"])
    }

    @Test
    fun typingQuicklyAsksOnceForTheLastText() = runTest {
        server()
        val viewModel = viewModel()

        viewModel.onQueryChange("n")
        viewModel.onQueryChange("ne")
        viewModel.onQueryChange("neo")
        viewModel.uiState.first { it.searched == "neo" }

        assertEquals(listOf("neo"), app.jellyfin.requestsTo("GET", "/Search/Hints").map { it.query["searchTerm"] })
    }

    @Test
    fun noMatchesSaysSoAndClearingTheFieldGoesBackToThePrompt() = runTest {
        server(hints())
        val viewModel = viewModel()

        viewModel.onQueryChange("zzz")
        val none = viewModel.uiState.first { it.searched == "zzz" && !it.loading }
        assertTrue(none.noResults)

        viewModel.onClear()
        val cleared = viewModel.uiState.value
        assertTrue(cleared.showsPrompt)
        assertFalse(cleared.noResults)
        assertTrue(cleared.groups.isEmpty())
    }

    @Test
    fun aFailedSearchSaysWhyAndTryAgainAsksAgain() = runTest {
        val server = server()
        server.on("GET", "/Search/Hints", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel()

        viewModel.onQueryChange("neon")
        val failed = viewModel.uiState.first { it.message != null }
        assertEquals(LoadMessages.forError(ApiError.Server(503)), failed.message)

        server.on("GET", "/Search/Hints", Reply.json(hints(hint("m1", "Neon Night", "Movie"))))
        viewModel.onRetry()
        val ok = viewModel.uiState.first { it.searched == "neon" && it.message == null && !it.loading }

        assertEquals(1, ok.groups.single().results.size)
    }

    @Test
    fun anExpiredSessionLeavesTheScreen() = runTest {
        app.signedIn().on("GET", "/Search/Hints", Reply.status(HttpStatusCode.Unauthorized))
        val viewModel = viewModel()

        viewModel.onQueryChange("neon")
        val state = viewModel.uiState.first { it.exit != null }

        assertEquals(HomeExit.WhoIsWatching("srv-1"), state.exit)
    }

    @Test
    fun aFilterNarrowsTheAnswerToOneKindWithoutAskingAgain() = runTest {
        server(hints(hint("m1", "Neon Night", "Movie"), hint("s1", "Neon Show", "Series"), hint("p1", "Neon Person", "Person")))
        val viewModel = viewModel()
        viewModel.onQueryChange("neon")
        val all = viewModel.uiState.first { it.searched == "neon" && !it.loading }
        assertEquals(listOf("All", "Movies", "Shows", "Episodes", "People", "Collections"), all.chips.map { it.label })
        assertEquals(3, all.visibleGroups.size)

        viewModel.onFilter(SearchFilter.People)

        val people = viewModel.uiState.value
        assertEquals(listOf(SearchKind.Person), people.visibleGroups.map { it.kind })
        assertTrue(people.chips.single { it.selected }.filter == SearchFilter.People)
        assertEquals(1, app.jellyfin.requestsTo("GET", "/Search/Hints").size)
    }

    @Test
    fun aFilterWithNoMatchesSaysSoWhileAllStillHasThem() = runTest {
        server(hints(hint("m1", "Neon Night", "Movie")))
        val viewModel = viewModel()
        viewModel.onQueryChange("neon")
        viewModel.uiState.first { it.searched == "neon" && !it.loading }

        viewModel.onFilter(SearchFilter.Collections)
        assertTrue(viewModel.uiState.value.noResults)

        viewModel.onFilter(SearchFilter.All)
        assertFalse(viewModel.uiState.value.noResults)
    }

    @Test
    fun theChosenFilterSurvivesANewSearchAndAClearedField() = runTest {
        server(hints(hint("m1", "Neon Night", "Movie")))
        val viewModel = viewModel()
        viewModel.onFilter(SearchFilter.Movies)

        viewModel.onQueryChange("neon")
        viewModel.uiState.first { it.searched == "neon" && !it.loading }
        viewModel.onClear()

        assertEquals(SearchFilter.Movies, viewModel.uiState.value.filter)
    }
}
