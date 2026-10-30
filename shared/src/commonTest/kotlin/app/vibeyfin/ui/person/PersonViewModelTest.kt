package app.vibeyfin.ui.person

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PersonViewModelTest {
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

    private fun viewModel(id: String = "p1") = viewModels.track(PersonViewModel(id, app.library, app.connection))

    private fun person(overview: String? = "Born in Rotterdam.") = itemJson(
        "p1",
        "Alexander Burger",
        type = "Person",
        overview = overview,
        primaryTag = "tag",
        premiereDate = "1999-10-17T00:00:00.0000000Z",
        productionLocations = listOf("Rotterdam, Netherlands"),
    )

    // Answers the person by id and what they appear in by `personIds`, as the server does for both on `/Items`.
    private suspend fun server(appearances: String = itemsJson(itemJson("m1", year = 2026), itemJson("s1", type = "Series", isFolder = true), itemJson("m2", year = 2020)), person: String = person()) = app.signedIn().also {
        it.on("GET", "/Items") { request ->
            if (request.query["personIds"] != null) Reply.json(appearances) else Reply.json(itemsJson(person))
        }
    }

    @Test
    fun showsThePersonAndWhatTheyAppearIn() = runTest {
        server()

        val state = viewModel().uiState.first { !it.loading && !it.appearancesLoading }

        assertEquals("Alexander Burger", state.person?.name)
        assertEquals("Born in Rotterdam.", state.person?.overview)
        assertEquals(listOf("Born 1999", "Rotterdam, Netherlands"), state.facts)
        assertEquals(listOf("m1", "m2"), state.movies.items.map { it.id })
        assertEquals(listOf("s1"), state.shows.items.map { it.id })
    }

    @Test
    fun appearancesAreAskedForByPersonNewestFirst() = runTest {
        server()

        viewModel().uiState.first { !it.appearancesLoading }

        val request = app.jellyfin.requestsTo("GET", "/Items").single { it.query["personIds"] != null }
        assertEquals("p1", request.query["personIds"])
        assertEquals("Movie,Series", request.query["includeItemTypes"])
        assertEquals("PremiereDate,SortName", request.query["sortBy"])
        assertEquals("Descending", request.query["sortOrder"])
    }

    @Test
    fun aPersonWithoutABiographyOrBirthDetailsStillLoads() = runTest {
        server(person = itemJson("p1", "Sabrina", type = "Person"))

        val state = viewModel().uiState.first { !it.loading && !it.appearancesLoading }

        assertNull(state.person?.overview)
        assertTrue(state.facts.isEmpty())
    }

    @Test
    fun aListOfAppearancesThatFailsLeavesThePersonAndRetries() = runTest {
        val server = server()
        server.on("GET", "/Items") { request ->
            if (request.query["personIds"] != null) Reply.status(HttpStatusCode.ServiceUnavailable) else Reply.json(itemsJson(person()))
        }
        val viewModel = viewModel()

        val failed = viewModel.uiState.first { !it.loading && !it.appearancesLoading }
        assertEquals("Alexander Burger", failed.person?.name)
        assertEquals(LoadMessages.forError(ApiError.Server(503)), failed.appearancesMessage)

        server.on("GET", "/Items") { request ->
            if (request.query["personIds"] != null) Reply.json(itemsJson(itemJson("m1"))) else Reply.json(itemsJson(person()))
        }
        viewModel.onRetry()
        val retried = viewModel.uiState.first { it.movies.items.isNotEmpty() }

        assertNull(retried.appearancesMessage)
    }

    @Test
    fun aPersonThatCantBeLoadedSaysSoAndRetries() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModel()

        val failed = viewModel.uiState.first { !it.loading }
        assertTrue(failed.message != null)
        assertNull(failed.person)

        server.on("GET", "/Items") { request ->
            if (request.query["personIds"] != null) Reply.json(itemsJson()) else Reply.json(itemsJson(person()))
        }
        viewModel.onRetry()

        assertEquals("Alexander Burger", viewModel.uiState.first { it.person != null }.person?.name)
    }

    @Test
    fun aPersonThePageHasNothingForIsSaid() = runTest {
        server(appearances = itemsJson())

        val state = viewModel().uiState.first { !it.loading && !it.appearancesLoading }

        assertTrue(state.noAppearances)
    }

    @Test
    fun anExpiredSessionLeavesThePage() = runTest {
        app.signedIn().on("GET", "/Items", Reply.status(HttpStatusCode.Unauthorized))

        val state = viewModel().uiState.first { it.exit != null }

        assertEquals(HomeExit.WhoIsWatching("srv-1"), state.exit)
    }
}
