package app.vibeyfin.ui.seerr

import app.vibeyfin.domain.SeerrConnectResult
import app.vibeyfin.domain.SeerrState
import app.vibeyfin.domain.model.RequestState
import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.testing.FakeSeerr
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.seerrUserJson
import app.vibeyfin.ui.common.Strings
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SeerrTitleViewModelTest {
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

    private val movieJson = """{"id":603,"title":"Harbour Bridge","releaseDate":"2025-03-01","runtime":96,"overview":"A bridge.",
        "genres":[{"id":1,"name":"Drama"}],"releases":{"results":[{"iso_3166_1":"US","release_dates":[{"certification":"PG-13"}]}]},
        "relatedVideos":[{"site":"YouTube","type":"Trailer","key":"abc"}],"credits":{"cast":[{"id":1,"name":"Ada Vance","character":"Queen"}]}}"""

    private fun showJson(mediaInfo: String = """{"status":4,"seasons":[{"seasonNumber":1,"status":5}],"requests":[]}""") = """{"id":19885,"name":"Storm Harbour","firstAirDate":"2010-07-25","seasons":[
        {"seasonNumber":0,"name":"Specials","episodeCount":3},{"seasonNumber":1,"name":"Series 1","episodeCount":8,"airDate":"2010-07-25"},
        {"seasonNumber":2,"name":"Series 2","episodeCount":8},{"seasonNumber":3,"episodeCount":10},{"seasonNumber":4,"episodeCount":10}],
        "mediaInfo":$mediaInfo}"""

    private val noRecommendations = """{"page":1,"totalPages":1,"totalResults":0,"results":[]}"""

    // Alex signed in to Jellyfin and, silently, to a Seerr that knows [movieJson] (603) and a show (19885).
    private suspend fun connected(configure: FakeSeerr.() -> Unit = {}): FakeSeerr {
        val jellyfin = app.signedIn()
        val seerr = FakeSeerr(app.jellyfin.server("http://seerr:5055"), jellyfin)
        seerr.onSignedIn("GET", "/api/v1/movie/603") { Reply.json(movieJson) }
        seerr.onSignedIn("GET", "/api/v1/movie/603/recommendations") { Reply.json(noRecommendations) }
        seerr.onSignedIn("GET", "/api/v1/tv/19885") { Reply.json(showJson()) }
        seerr.onSignedIn("GET", "/api/v1/tv/19885/recommendations") { Reply.json(noRecommendations) }
        seerr.configure()
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { app.seerr.state.first { it == SeerrState.NotSet } } }
        assertEquals(SeerrConnectResult.Connected, app.seerr.connect("http://seerr:5055"))
        return seerr
    }

    private fun viewModel(type: SeerrMediaType, id: Int) = viewModels.track(SeerrTitleViewModel(type, id, app.seerrRepository, app.seerr, app.clock))

    private suspend fun SeerrTitleViewModel.await(predicate: (SeerrTitleUiState) -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5.seconds) { uiState.first(predicate) }
    }

    @Test
    fun aMovieNobodyAskedForOffersRequestWithItsFactsAndStatus() = runTest {
        connected()

        val state = viewModel(SeerrMediaType.Movie, 603).await { !it.loading }

        assertEquals("Harbour Bridge", state.title?.title)
        assertEquals(listOf("2025", "PG-13", "1 h 36 min"), state.facts)
        assertEquals("Not in your library", state.status)
        assertEquals(SeerrPrimary(Strings.seerrRequest, enabled = true), state.primary)
        assertEquals("https://www.youtube.com/watch?v=abc", state.trailerUrl)
        assertFalse(state.offer4k)
        assertNull(state.cancelRequestId)
        assertEquals("2025 · Movie · Not in your library", state.menuSubtitle)
        assertTrue(state.canRequest)
    }

    @Test
    fun aMoviesImdbRatingJoinsItsFactsWhenSeerrHasOne() = runTest {
        connected().onSignedIn("GET", "/api/v1/movie/603/ratingscombined") {
            Reply.json("""{"rt":{"criticsScore":83},"imdb":{"title":"Harbour Bridge","criticsScore":8.66,"criticsScoreCount":2289808}}""")
        }

        val state = viewModel(SeerrMediaType.Movie, 603).await { it.facts.size == 4 }

        assertEquals(listOf("2025", "PG-13", "1 h 36 min", "\u2605 8.7"), state.facts)
    }

    @Test
    fun requestingAMovieSendsItOnceAndThePageThenShowsItWaiting() = runTest {
        val seerr = connected()
        seerr.onSignedIn("POST", "/api/v1/request") {
            // From now on Seerr knows it as requested by Alex.
            seerr.onSignedIn("GET", "/api/v1/movie/603") {
                Reply.json(movieJson.replace("\"credits\"", "\"mediaInfo\":{\"status\":2,\"requests\":[{\"id\":9,\"status\":1,\"type\":\"movie\",\"requestedBy\":{\"id\":7},\"createdAt\":\"2023-11-14T22:13:20Z\"}]},\"credits\""))
            }
            Reply.json("""{"id":9,"status":1,"type":"movie","requestedBy":{"id":7}}""", HttpStatusCode.Created)
        }
        val viewModel = viewModel(SeerrMediaType.Movie, 603)
        viewModel.await { !it.loading }
        app.clock.advance(2.days)

        viewModel.onRequest()

        val sent = viewModel.await { it.state == RequestState.Requested }
        assertEquals(SeerrNotice(Strings.seerrRequested("Harbour Bridge"), SeerrNoticeAction.View, Strings.seerrView), sent.notice)
        assertEquals("Requested by you · Waiting for approval", sent.status)
        assertEquals(SeerrPrimary("Requested", enabled = false), sent.primary)
        assertEquals(9, sent.cancelRequestId)
        assertEquals("Requested", sent.menuStatus)
        assertEquals("By you, 2 days ago · Waiting for approval", sent.menuStatusDetail)
        assertFalse(sent.canRequest)
        assertEquals(1, app.jellyfin.requestsTo("POST", "/api/v1/request").size)

        viewModel.onNoticeShown()
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun aShowsRequestOpensThePickerWithEveryMissingSeasonButSpecials() = runTest {
        connected().onSignedIn("POST", "/api/v1/request") { Reply.json("""{"id":3,"status":1,"type":"tv"}""", HttpStatusCode.Created) }
        val viewModel = viewModel(SeerrMediaType.Show, 19885)
        val page = viewModel.await { !it.loading }
        assertEquals(SeerrPrimary(Strings.seerrRequestSeasons, enabled = true), page.primary)
        assertEquals(listOf("Specials", "Series 1", "Series 2", "Season 3", "Season 4"), page.seasons.map { it.name })
        assertEquals("Available", page.seasons[1].stateLabel)

        viewModel.onRequest()

        val picker = viewModel.uiState.value.picker!!
        assertTrue(picker.allMissing)
        assertEquals("Storm Harbour · 4 seasons", picker.subtitle)
        assertEquals(listOf(2, 3, 4), picker.seasons.filter { it.selected }.map { it.number })
        assertTrue(picker.seasons.first { it.number == 1 }.locked)
        assertFalse(picker.seasons.first { it.number == 0 }.locked)
        assertEquals("Request 3 seasons", picker.confirmLabel)

        // Picking one by hand turns "All missing seasons" off.
        viewModel.onPickerSeason(4)
        assertFalse(viewModel.uiState.value.picker!!.allMissing)
        assertEquals("Request 2 seasons", viewModel.uiState.value.picker!!.confirmLabel)
        // A locked season can't be picked.
        viewModel.onPickerSeason(1)
        assertEquals(listOf(2, 3), viewModel.uiState.value.picker!!.seasons.filter { it.selected }.map { it.number })

        viewModel.onPickerConfirm()

        viewModel.await { it.notice != null }
        assertNull(viewModel.uiState.value.picker)
        assertEquals("""{"mediaType":"tv","mediaId":19885,"seasons":[2,3]}""", app.jellyfin.requestsTo("POST", "/api/v1/request").single().body)
    }

    @Test
    fun aShowsPageOpensOnItsFirstSeasonsEpisodesAndSwitchesSeason() = runTest {
        connected {
            onSignedIn("GET", "/api/v1/tv/19885/season/1") {
                Reply.json(
                    """{"seasonNumber":1,"episodes":[{"episodeNumber":2,"name":"Two","airDate":"2010-08-01"},
                {"episodeNumber":1,"name":"Pilot","overview":"It starts.","stillPath":"https://image.tmdb.org/t/p/original//a.jpg","voteAverage":8.2}]}""",
                )
            }
            onSignedIn("GET", "/api/v1/tv/19885/season/2") { Reply.json("""{"seasonNumber":2,"episodes":[{"episodeNumber":1,"name":""}]}""") }
        }
        val viewModel = viewModel(SeerrMediaType.Show, 19885)

        val first = viewModel.await { it.episodes?.loading == false }.episodes!!

        assertEquals(1, first.season)
        assertEquals(listOf("Pilot", "Two"), first.episodes.map { it.title })
        assertEquals("https://image.tmdb.org/t/p/w500//a.jpg", first.episodes[0].stillUrl)
        assertEquals(8.2, first.episodes[0].rating)

        viewModel.onSelectSeason(2)

        val second = viewModel.await { it.episodes?.let { shown -> shown.season == 2 && !shown.loading } == true }.episodes!!
        assertEquals(listOf("Episode 1"), second.episodes.map { it.title })
    }

    @Test
    fun theTagSaysWhereTheTitleStandsAndTheDetailWhoAsked() = runTest {
        connected()
        val fresh = viewModel(SeerrMediaType.Movie, 603).await { !it.loading }
        assertEquals("Not in your library", fresh.tag)
        assertNull(fresh.statusDetail)
    }

    @Test
    fun whoMayChooseQualityGetsTheRequestSheetWithTheServersProfiles() = runTest {
        connected {
            user = seerrUserJson(permissions = 32 or 8192)
            onSignedIn("GET", "/api/v1/service/radarr") { Reply.json("""[{"id":1,"name":"Radarr 4K","is4k":true},{"id":0,"name":"Radarr","is4k":false,"isDefault":true,"activeProfileId":4}]""") }
            onSignedIn("GET", "/api/v1/service/radarr/0") { Reply.json("""{"server":{"id":0,"activeProfileId":4},"profiles":[{"id":4,"name":"HD-1080p"},{"id":5,"name":"Ultra-HD"}]}""") }
        }.onSignedIn("POST", "/api/v1/request") { Reply.json("""{"id":9,"status":1,"type":"movie"}""", HttpStatusCode.Created) }
        val viewModel = viewModel(SeerrMediaType.Movie, 603)
        viewModel.await { !it.loading }

        viewModel.onRequest()

        val sheet = viewModel.await { it.picker?.qualities?.isNotEmpty() == true }.picker!!
        assertFalse(sheet.showsSeasons)
        assertEquals(listOf("HD-1080p" to true, "Ultra-HD" to false), sheet.qualities.map { it.name to it.selected })
        assertTrue(app.jellyfin.requestsTo("POST", "/api/v1/request").isEmpty())

        viewModel.onPickerProfile(5)
        viewModel.onPickerConfirm()

        viewModel.await { it.notice != null }
        assertEquals("""{"mediaType":"movie","mediaId":603,"serverId":0,"profileId":5}""", app.jellyfin.requestsTo("POST", "/api/v1/request").single().body)
    }

    @Test
    fun everyoneElsesMovieRequestGoesAtOnceWithTheDefaultQuality() = runTest {
        connected().onSignedIn("POST", "/api/v1/request") { Reply.json("""{"id":9,"status":1,"type":"movie"}""", HttpStatusCode.Created) }
        val viewModel = viewModel(SeerrMediaType.Movie, 603)
        viewModel.await { !it.loading }

        viewModel.onRequest()

        viewModel.await { it.notice != null }
        assertNull(viewModel.uiState.value.picker)
        assertTrue(app.jellyfin.requestsTo("GET", "/api/v1/service/radarr").isEmpty())
    }

    @Test
    fun allMissingSeasonsOffLeavesNothingToRequest() = runTest {
        connected()
        val viewModel = viewModel(SeerrMediaType.Show, 19885)
        viewModel.await { !it.loading }
        viewModel.onRequest()

        viewModel.onPickerAllMissing(false)

        assertFalse(viewModel.uiState.value.picker!!.canConfirm)
        viewModel.onPickerConfirm()
        assertTrue(app.jellyfin.requestsTo("POST", "/api/v1/request").isEmpty())
    }

    @Test
    fun aRefusedRequestSaysWhyAndAnUnreachableOneCanBeSentAgain() = runTest {
        val seerr = connected()
        seerr.onSignedIn("POST", "/api/v1/request") { Reply.status(HttpStatusCode.Forbidden) }
        seerr.onSignedIn("GET", "/api/v1/user/7/quota") { Reply.json("""{"movie":{"days":7,"limit":5,"used":5,"remaining":0,"restricted":true}}""") }
        val viewModel = viewModel(SeerrMediaType.Movie, 603)
        viewModel.await { !it.loading }

        viewModel.onRequest()
        assertEquals("Your request limit is reached. You can request 5 movies a week.", viewModel.await { it.notice != null }.notice?.text)
        viewModel.onNoticeShown()

        seerr.server.refuseConnections()
        viewModel.onRequest()
        val unreachable = viewModel.await { it.notice != null }.notice!!
        assertEquals(SeerrNoticeAction.TryAgain, unreachable.action)
        assertTrue(unreachable.failure)
        viewModel.onNoticeShown()

        seerr.server.refuseConnections(false)
        seerr.onSignedIn("POST", "/api/v1/request") { Reply.json("""{"id":9,"status":1,"type":"movie"}""", HttpStatusCode.Created) }
        viewModel.onRetryRequest()
        assertEquals(Strings.seerrRequested("Harbour Bridge"), viewModel.await { it.notice != null }.notice?.text)
    }

    @Test
    fun theProfilesOwnPendingRequestCanBeCancelled() = runTest {
        val seerr = connected {
            onSignedIn("GET", "/api/v1/movie/603") {
                Reply.json(movieJson.replace("\"credits\"", "\"mediaInfo\":{\"status\":2,\"requests\":[{\"id\":9,\"status\":1,\"type\":\"movie\",\"requestedBy\":{\"id\":7}}]},\"credits\""))
            }
        }
        seerr.onSignedIn("DELETE", "/api/v1/request/9") {
            seerr.onSignedIn("GET", "/api/v1/movie/603") { Reply.json(movieJson) }
            Reply.noContent()
        }
        val viewModel = viewModel(SeerrMediaType.Movie, 603)
        assertEquals(9, viewModel.await { !it.loading }.cancelRequestId)

        viewModel.onCancelRequest()

        val cancelled = viewModel.await { it.state == RequestState.NotRequested && it.notice != null }
        assertEquals(Strings.seerrRequestCancelled, cancelled.notice?.text)
        assertNull(cancelled.cancelRequestId)
    }

    @Test
    fun someoneElsesRequestSaysWhoAndLeavesNothingToDo() = runTest {
        connected {
            onSignedIn("GET", "/api/v1/movie/603") {
                Reply.json(movieJson.replace("\"credits\"", "\"mediaInfo\":{\"status\":3,\"requests\":[{\"id\":4,\"status\":2,\"type\":\"movie\",\"requestedBy\":{\"id\":8,\"displayName\":\"Sam\"}}]},\"credits\""))
            }
        }

        val state = viewModel(SeerrMediaType.Movie, 603).await { !it.loading }

        assertEquals("Approved · Added when the download finishes", state.status)
        assertEquals(SeerrPrimary("Approved", enabled = false), state.primary)
        assertNull(state.cancelRequestId)
        assertEquals("By Sam · Added when the download finishes", state.menuStatusDetail)
    }

    @Test
    fun aShowWithEverySeasonButSpecialsRequestedOffersNoMoreRequests() = runTest {
        // A limited series: Specials and one season, which was asked for (approved at once, before Seerr marked the season).
        val limited = """{"id":19885,"name":"Low Tide","seasons":[{"seasonNumber":0,"name":"Specials","episodeCount":2},
            {"seasonNumber":1,"name":"Limited Series","episodeCount":6}],"mediaInfo":{"status":3,"seasons":[],
            "requests":[{"id":5,"status":2,"type":"tv","requestedBy":{"id":7},"seasons":[{"seasonNumber":1,"status":2}]}]}}"""
        connected { onSignedIn("GET", "/api/v1/tv/19885") { Reply.json(limited) } }
        val page = viewModel(SeerrMediaType.Show, 19885).await { !it.loading }

        assertFalse(page.primary!!.enabled)
        assertFalse(page.canRequest)
        assertEquals(RequestState.Approved, page.seasons.first { it.number == 1 }.state)
    }

    @Test
    fun aSeasonWithAnApprovedRequestSeerrHasNotMarkedYetIsNotRequestableAgain() = runTest {
        connected {
            onSignedIn("GET", "/api/v1/tv/19885") {
                Reply.json(showJson("""{"status":3,"seasons":[],"requests":[{"id":5,"status":2,"type":"tv","requestedBy":{"id":7},"seasons":[{"seasonNumber":2,"status":2}]}]}"""))
            }
        }
        val viewModel = viewModel(SeerrMediaType.Show, 19885)
        val page = viewModel.await { !it.loading }

        assertEquals(RequestState.Approved, page.seasons.first { it.number == 2 }.state)
        assertTrue(page.primary!!.enabled)
        viewModel.onRequest()
        assertTrue(viewModel.uiState.value.picker!!.seasons.first { it.number == 2 }.locked)
    }

    @Test
    fun aTitleInTheLibraryOpensTheLibrarysPageInstead() = runTest {
        connected {
            onSignedIn("GET", "/api/v1/tv/19885") { Reply.json(showJson("""{"status":5,"jellyfinMediaId":"jf-1"}""")) }
        }
        val viewModel = viewModel(SeerrMediaType.Show, 19885)

        assertEquals("jf-1", viewModel.await { !it.loading }.openItem)
        viewModel.onItemOpened()
        assertNull(viewModel.uiState.value.openItem)
    }

    @Test
    fun fourKIsOfferedOnlyToWhoMayAskForIt() = runTest {
        connected {
            user = seerrUserJson(permissions = 32 or 2048)
            server.on("GET", "/api/v1/settings/public", Reply.json("""{"initialized":true,"mediaServerType":2,"movie4kEnabled":true,"series4kEnabled":false}"""))
        }.onSignedIn("POST", "/api/v1/request") { Reply.json("""{"id":9,"status":1,"type":"movie","is4k":true}""", HttpStatusCode.Created) }
        val movie = viewModel(SeerrMediaType.Movie, 603)
        assertTrue(movie.await { !it.loading }.offer4k)

        movie.onRequest4k()

        movie.await { it.notice != null }
        assertEquals("""{"mediaType":"movie","mediaId":603,"is4k":true}""", app.jellyfin.requestsTo("POST", "/api/v1/request").single().body)
        // This Seerr has no 4K server for shows.
        val show = viewModel(SeerrMediaType.Show, 19885)
        show.await { !it.loading }
        show.onRequest()
        assertFalse(show.uiState.value.picker!!.offer4k)
    }

    @Test
    fun aTitleSeerrCannotReadOffersTryAgain() = runTest {
        connected().server.refuseConnections()
        val viewModel = viewModel(SeerrMediaType.Movie, 603)

        assertEquals(SeerrMessages.UNREACHABLE_NOW, viewModel.await { !it.loading }.message)
    }
}
