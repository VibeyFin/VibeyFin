package app.vibeyfin.ui.seerr

import app.vibeyfin.domain.SeerrConnectResult
import app.vibeyfin.domain.SeerrState
import app.vibeyfin.domain.model.RequestState
import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.testing.FakeSeerr
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.ui.common.Strings
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SeerrListsTest {
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

    private fun page(vararg results: String, page: Int = 1, pages: Int = 1) = """{"page":$page,"totalPages":$pages,"totalResults":${results.size},"results":[${results.joinToString(",")}]}"""

    private fun movie(id: Int, title: String, date: String = "2025-01-01", extra: String = "") = """{"id":$id,"mediaType":"movie","title":"$title","releaseDate":"$date"$extra}"""

    private fun show(id: Int, name: String, date: String = "2025-01-01") = """{"id":$id,"mediaType":"tv","name":"$name","firstAirDate":"$date"}"""

    // FakeClock's "now" is 2023-11-14T22:13:20Z.
    private fun request(id: Int, status: Int, tmdbId: Int, type: String = "movie", mediaStatus: Int = 1, updatedAt: String = "2023-11-13T22:13:20Z", seasons: String = "") = """{"id":$id,"status":$status,"type":"$type","media":{"tmdbId":$tmdbId,"mediaType":"$type","status":$mediaStatus},"seasons":[$seasons],"requestedBy":{"id":7},"updatedAt":"$updatedAt"}"""

    private suspend fun connected(configure: FakeSeerr.() -> Unit = {}): FakeSeerr {
        val jellyfin = app.signedIn()
        val seerr = FakeSeerr(app.jellyfin.server("http://seerr:5055"), jellyfin)
        seerr.onSignedIn("GET", "/api/v1/discover/trending") { Reply.json(page(movie(1, "Low Tide"), show(2, "Tidewater"), """{"id":3,"mediaType":"person","name":"Someone"}""")) }
        seerr.onSignedIn("GET", "/api/v1/discover/movies") { Reply.json(page(movie(4, "Harbour Bridge"))) }
        seerr.onSignedIn("GET", "/api/v1/discover/tv") { Reply.json(page()) }
        seerr.onSignedIn("GET", "/api/v1/discover/movies/upcoming") { Reply.json(page(movie(5, "Ferry Lights", "2026-12-12"), movie(6, "Second Tide", "2027-02-02"))) }
        seerr.onSignedIn("GET", "/api/v1/discover/tv/upcoming") { Reply.json(page(show(7, "Kiln Season 3", "2027-01-08"))) }
        seerr.onSignedIn("GET", "/api/v1/request") { Reply.json("""{"pageInfo":{"pages":1,"page":1,"results":0},"results":[]}""") }
        for ((id, title) in listOf(10 to "Waiting One", 11 to "Old Decline", 12 to "New Arrival", 13 to "Approved One")) {
            seerr.onSignedIn("GET", "/api/v1/movie/$id") { Reply.json("""{"id":$id,"title":"$title","releaseDate":"2022-01-01"}""") }
        }
        seerr.configure()
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { app.seerr.state.first { it == SeerrState.NotSet } } }
        assertEquals(SeerrConnectResult.Connected, app.seerr.connect("http://seerr:5055"))
        return seerr
    }

    private suspend fun <T> Flow<T>.await(predicate: (T) -> Boolean): T = withContext(Dispatchers.Default) { withTimeout(5.seconds) { first(predicate) } }

    @Test
    fun homeShowsTheDiscoverRowsThatHaveTitlesWithTheNoteOnTheFirst() = runTest {
        connected()
        val home = viewModels.track(SeerrHomeViewModel(app.seerr, app.seerrRepository, app.clock))

        val state = home.uiState.await { it.discover.isNotEmpty() && it.discover.none { row -> row.loading } }

        // Popular shows came back empty, so it isn't there.
        assertEquals(listOf(SeerrListKind.Trending, SeerrListKind.PopularMovies, SeerrListKind.ComingSoon), state.discover.map { it.kind })
        assertEquals(Strings.seerrDiscoverNote, state.discover.first().note)
        assertNull(state.discover[1].note)
        assertEquals(listOf("Low Tide", "Tidewater"), state.discover.first().titles.map { it.title })
        // Coming soon puts movies and shows together by date, and shows the dates.
        val soon = state.discover.last()
        assertEquals(listOf("Ferry Lights", "Kiln Season 3", "Second Tide"), soon.titles.map { it.title })
        assertTrue(soon.showsDates)
        assertEquals("Dec 12", Strings.seerrReleaseDay(soon.titles.first().releaseDate))
        // No requests: no My requests row.
        assertNull(state.requests)
    }

    @Test
    fun myRequestsOnHomeAreTheOnesStillOnTheirWayAndThisWeeksNews() = runTest {
        connected {
            onSignedIn("GET", "/api/v1/request") {
                Reply.json(
                    """{"pageInfo":{"pages":1,"page":1,"results":4},"results":[
                    ${request(1, 1, 10)},
                    ${request(2, 3, 11, updatedAt = "2023-10-01T00:00:00Z")},
                    ${request(3, 2, 12, mediaStatus = 5)},
                    ${request(4, 2, 13, mediaStatus = 3)}]}""",
                )
            }
        }
        val home = viewModels.track(SeerrHomeViewModel(app.seerr, app.seerrRepository, app.clock))

        val requests = home.uiState.await { it.requests?.loading == false }.requests!!

        // The decline from six weeks ago is old news.
        assertEquals(listOf("Waiting One", "New Arrival", "Approved One"), requests.titles.map { it.title })
        assertEquals(listOf(RequestState.Requested, RequestState.Available, RequestState.Approved), requests.titles.map { it.state })
    }

    @Test
    fun seerrSigningOutEmptiesHomesSeerrRows() = runTest {
        connected()
        val home = viewModels.track(SeerrHomeViewModel(app.seerr, app.seerrRepository, app.clock))
        home.uiState.await { it.discover.isNotEmpty() }

        app.seerr.disconnect()

        assertEquals(SeerrHomeUiState(), home.uiState.await { it.discover.isEmpty() })
    }

    @Test
    fun aDiscoverRowThatFailsIsLeftOutQuietly() = runTest {
        connected { onSignedIn("GET", "/api/v1/discover/movies") { Reply.status(HttpStatusCode.InternalServerError) } }
        val home = viewModels.track(SeerrHomeViewModel(app.seerr, app.seerrRepository, app.clock))

        val state = home.uiState.await { it.discover.isNotEmpty() && it.discover.none { row -> row.loading } }

        assertEquals(listOf(SeerrListKind.Trending, SeerrListKind.ComingSoon), state.discover.map { it.kind })
    }

    @Test
    fun seeAllPagesThroughTheListAndNarrowsItToOneKind() = runTest {
        connected {
            onSignedIn("GET", "/api/v1/discover/trending") { request ->
                if (request.query["page"] == "2") Reply.json(page(movie(8, "Northbound"), page = 2, pages = 2)) else Reply.json(page(movie(1, "Low Tide"), show(2, "Tidewater"), pages = 2))
            }
        }
        val list = viewModels.track(SeerrListViewModel(SeerrListKind.Trending, app.seerrRepository))
        assertEquals(2, list.uiState.await { !it.loading }.titles.size)
        assertTrue(list.uiState.value.offersTypeFilter)

        list.onLoadMore()

        assertEquals(listOf("Low Tide", "Tidewater", "Northbound"), list.uiState.await { it.titles.size == 3 }.titles.map { it.title })
        list.onTypeFilter(SeerrMediaType.Show)
        assertEquals(listOf("Tidewater"), list.uiState.value.titles.map { it.title })
        // Tapping the chosen chip again shows both.
        list.onTypeFilter(SeerrMediaType.Show)
        assertEquals(3, list.uiState.value.titles.size)
        // The last page was read: no more is asked for.
        list.onLoadMore()
        assertEquals(2, app.jellyfin.requestsTo("GET", "/api/v1/discover/trending").size)
    }

    @Test
    fun myRequestsPageNamesEachRequestAndFiltersByState() = runTest {
        connected {
            onSignedIn("GET", "/api/v1/request") {
                Reply.json(
                    """{"pageInfo":{"pages":1,"page":1,"results":3},"results":[
                    ${request(1, 1, 10, updatedAt = "2023-11-14T20:00:00Z")},
                    ${request(2, 3, 11, updatedAt = "2023-11-04T22:13:20Z")},
                    ${request(3, 2, 12, mediaStatus = 5, updatedAt = "2023-11-13T22:13:20Z")}]}""",
                )
            }
        }
        val page = viewModels.track(MyRequestsViewModel(app.seerrRepository, app.clock))

        val all = page.uiState.await { !it.loading }

        assertEquals(listOf("Waiting One", "Old Decline", "New Arrival"), all.items.map { it.title.title })
        assertEquals("Movie · 2022", all.items[0].kind)
        assertEquals("Requested", all.items[0].stateLabel)
        assertEquals("Requested today", all.items[0].changed)
        assertEquals("Declined last week", all.items[1].changed)
        assertEquals("Added to your library yesterday", all.items[2].changed)

        page.onFilter(RequestFilter.Declined)
        assertEquals(listOf("Old Decline"), page.uiState.value.items.map { it.title.title })
    }

    @Test
    fun myRequestsIsNeverSeenLoadedAndEmptyOnTheWayToItsList() = runTest {
        connected {
            onSignedIn("GET", "/api/v1/request") { Reply.json("""{"pageInfo":{"pages":1,"page":1,"results":1},"results":[${request(1, 1, 10)}]}""") }
        }
        val seen = mutableListOf<MyRequestsUiState>()
        val page = viewModels.track(MyRequestsViewModel(app.seerrRepository, app.clock))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { page.uiState.collect { seen += it } }

        page.uiState.await { it.items.isNotEmpty() }

        assertTrue(seen.none { !it.loading && it.items.isEmpty() }, "a state said loaded with nothing in it: $seen")
    }

    @Test
    fun noRequestsAtAllSaysHowToStart() = runTest {
        connected()

        assertTrue(viewModels.track(MyRequestsViewModel(app.seerrRepository, app.clock)).uiState.await { !it.loading }.empty)
    }

    @Test
    fun aRequestsSeasonsAreWorded() {
        assertEquals("Show · Seasons 1–2", Strings.seerrRequestKind(SeerrMediaType.Show, 2010, listOf(2, 1)))
        assertEquals("Show · Season 3", Strings.seerrRequestKind(SeerrMediaType.Show, 2010, listOf(3)))
        assertEquals("Show · Seasons 1, 3", Strings.seerrRequestKind(SeerrMediaType.Show, null, listOf(1, 3)))
    }
}
