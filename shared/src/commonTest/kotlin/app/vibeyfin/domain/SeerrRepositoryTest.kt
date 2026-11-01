package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.seerr.SeasonChoice
import app.vibeyfin.domain.model.QuotaLeft
import app.vibeyfin.domain.model.RequestQuota
import app.vibeyfin.domain.model.RequestState
import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.testing.FakeSeerr
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SeerrRepositoryTest {
    private val app = TestApp()

    @AfterTest
    fun tearDown() = runTest { app.close() }

    // Alex signed in to Jellyfin and, silently, to a Seerr at http://seerr:5055.
    private suspend fun connected(): FakeSeerr {
        val jellyfin = app.signedIn()
        val seerr = FakeSeerr(app.jellyfin.server("http://seerr:5055"), jellyfin)
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { app.seerr.state.first { it == SeerrState.NotSet } } }
        assertEquals(SeerrConnectResult.Connected, app.seerr.connect("http://seerr:5055"))
        return seerr
    }

    private fun <T> ApiResult<T>.value(): T = assertIs<ApiResult.Success<T>>(this).value

    @Test
    fun searchListsMoviesAndShowsWithWhatSeerrKnowsAndLeavesPeopleOut() = runTest {
        connected().onSignedIn("GET", "/api/v1/search") {
            Reply.json(
                """{"page":1,"totalPages":3,"totalResults":41,"results":[
                {"id":10331,"mediaType":"movie","title":"Night of the Living Dead","releaseDate":"1968-10-04","posterPath":"/p.jpg",
                 "mediaInfo":{"tmdbId":10331,"status":5,"status4k":1,"jellyfinMediaId":"jf-1"}},
                {"id":19885,"mediaType":"tv","name":"Sherlock","firstAirDate":"2010-07-25","mediaInfo":{"status":4}},
                {"id":3,"mediaType":"person","name":"Someone","profilePath":"/x.jpg"},
                {"id":65522,"mediaType":"tv","name":"The Living and the Dead","firstAirDate":"2016-06-28","posterPath":null},
                {"id":70000,"mediaType":"movie","title":"Living Gone","mediaInfo":{"status":7,"jellyfinMediaId":"jf-gone"}}]}""",
            )
        }

        val page = app.seerrRepository.search("living dead").value()

        assertEquals(listOf(10331, 19885, 65522, 70000), page.titles.map { it.tmdbId })
        val movie = page.titles[0]
        assertEquals(SeerrMediaType.Movie, movie.type)
        assertEquals(1968, movie.year)
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg", movie.posterUrl)
        assertEquals(RequestState.Available, movie.state)
        assertEquals("jf-1", movie.jellyfinItemId)
        assertEquals(RequestState.PartlyAvailable, page.titles[1].state)
        assertEquals(RequestState.NotRequested, page.titles[2].state)
        assertNull(page.titles[2].posterUrl)
        // Deleted from the library: Seerr still names the old item, which no longer opens.
        assertNull(page.titles[3].jellyfinItemId)
        assertTrue(page.hasMore)
        // The term went out percent-encoded, as Seerr insists.
        assertEquals("query=living%20dead&page=1", app.jellyfin.requestsTo("GET", "/api/v1/search").single().encodedQuery)
    }

    @Test
    fun aShowsPageCarriesEachSeasonsStateIncludingOnesStillWaitingForApproval() = runTest {
        connected().onSignedIn("GET", "/api/v1/tv/19885") {
            Reply.json(
                """{"id":19885,"name":"Sherlock","firstAirDate":"2010-07-25","episodeRunTime":[88],"genres":[{"id":80,"name":"Crime"}],
                "contentRatings":{"results":[{"iso_3166_1":"DE","rating":"12"},{"iso_3166_1":"US","rating":"TV-14"}]},
                "relatedVideos":[{"site":"YouTube","type":"Featurette","key":"f"},{"site":"YouTube","type":"Trailer","key":"abc","url":"https://www.youtube.com/watch?v=abc"}],
                "credits":{"cast":[{"id":1,"name":"Ben","character":"Sherlock","profilePath":"/b.jpg"},{"id":2}]},
                "seasons":[{"seasonNumber":2,"episodeCount":3,"airDate":"2012-01-01"},{"seasonNumber":0,"name":"Specials","episodeCount":9},{"seasonNumber":1,"episodeCount":3,"airDate":"2010-07-25"}],
                "mediaInfo":{"status":4,"jellyfinMediaId":"jf-sherlock","seasons":[{"seasonNumber":1,"status":5},{"seasonNumber":2,"status":1}],
                  "requests":[{"id":1,"status":1,"type":"tv","seasons":[{"seasonNumber":2,"status":1}],"requestedBy":{"id":7,"displayName":"Alex"},"createdAt":"2026-10-31T15:47:29.000Z"}]}}""",
            )
        }

        val details = app.seerrRepository.title(SeerrMediaType.Show, 19885).value()

        assertEquals(RequestState.PartlyAvailable, details.title.state)
        assertEquals("jf-sherlock", details.title.jellyfinItemId)
        assertEquals("TV-14", details.certification)
        assertEquals(88, details.runtimeMinutes)
        assertEquals(listOf("Crime"), details.genres)
        assertEquals("https://www.youtube.com/watch?v=abc", details.trailerUrl)
        assertEquals(listOf("Ben"), details.cast.map { it.name })
        assertEquals(listOf(0, 1, 2), details.seasons.map { it.number })
        assertEquals(listOf(RequestState.NotRequested, RequestState.Available, RequestState.Requested), details.seasons.map { it.state })
        val request = details.requests.single()
        assertTrue(request.byMe)
        assertTrue(request.pendingApproval)
        assertEquals(listOf(2), request.seasons)
    }

    @Test
    fun aMovieRequestIsSentOnceAndComesBackAsRequested() = runTest {
        connected().onSignedIn("POST", "/api/v1/request") {
            Reply.json("""{"id":2,"status":1,"type":"movie","is4k":false,"media":{"tmdbId":603,"mediaType":"movie","status":2},"requestedBy":{"id":7}}""", HttpStatusCode.Created)
        }

        val outcome = assertIs<RequestOutcome.Sent>(app.seerrRepository.request(SeerrMediaType.Movie, 603))

        assertEquals(RequestState.Requested, outcome.request?.state)
        assertTrue(outcome.request!!.byMe)
        val sent = app.jellyfin.requestsTo("POST", "/api/v1/request").single()
        assertEquals("""{"mediaType":"movie","mediaId":603}""", sent.body)
    }

    @Test
    fun aShowRequestNamesItsSeasonsOrAllAndCanAskFor4k() = runTest {
        connected().onSignedIn("POST", "/api/v1/request") { Reply.json("""{"id":3,"status":1,"type":"tv","seasons":[{"seasonNumber":2},{"seasonNumber":3}]}""", HttpStatusCode.Created) }

        app.seerrRepository.request(SeerrMediaType.Show, 19885, SeasonChoice.Only(listOf(2, 3)))
        app.seerrRepository.request(SeerrMediaType.Show, 19885)
        app.seerrRepository.request(SeerrMediaType.Show, 19885, SeasonChoice.All, is4k = true)

        assertEquals(
            listOf(
                """{"mediaType":"tv","mediaId":19885,"seasons":[2,3]}""",
                """{"mediaType":"tv","mediaId":19885,"seasons":"all"}""",
                """{"mediaType":"tv","mediaId":19885,"seasons":"all","is4k":true}""",
            ),
            app.jellyfin.requestsTo("POST", "/api/v1/request").map { it.body },
        )
    }

    @Test
    fun nothingLeftToAskForIsSentWithoutARequest() = runTest {
        connected().onSignedIn("POST", "/api/v1/request") { Reply.json("""{"message":"No seasons available to request"}""", HttpStatusCode.Accepted) }

        assertEquals(RequestOutcome.Sent(null), app.seerrRepository.request(SeerrMediaType.Show, 19885))
    }

    @Test
    fun whyARequestFailedIsToldApart() = runTest {
        val seerr = connected()
        seerr.onSignedIn("POST", "/api/v1/request") { Reply.json("""{"message":"Request for this media already exists."}""", HttpStatusCode.Conflict) }
        assertEquals(RequestOutcome.AlreadyRequested, app.seerrRepository.request(SeerrMediaType.Movie, 603))

        // Forbidden with requests left: no permission (such as for 4K).
        seerr.onSignedIn("POST", "/api/v1/request") { Reply.json("""{"message":"You do not have permission to make 4K movie requests."}""", HttpStatusCode.Forbidden) }
        seerr.onSignedIn("GET", "/api/v1/user/7/quota") { Reply.json("""{"movie":{"used":0,"restricted":false},"tv":{"used":0,"restricted":false}}""") }
        assertEquals(RequestOutcome.NotAllowed, app.seerrRepository.request(SeerrMediaType.Movie, 604, is4k = true))

        // Forbidden with none left: the quota.
        seerr.onSignedIn("GET", "/api/v1/user/7/quota") { Reply.json("""{"movie":{"days":7,"limit":5,"used":5,"remaining":0,"restricted":true},"tv":{"used":0}}""") }
        assertEquals(
            RequestOutcome.QuotaReached(RequestQuota(movies = QuotaLeft(limit = 5, remaining = 0, days = 7), shows = null)),
            app.seerrRepository.request(SeerrMediaType.Movie, 605),
        )

        // Forbidden for three seasons with one left: the quota too, not a missing permission.
        seerr.onSignedIn("GET", "/api/v1/user/7/quota") { Reply.json("""{"movie":{"used":0},"tv":{"days":7,"limit":5,"used":4,"remaining":1,"restricted":false}}""") }
        assertEquals(
            RequestOutcome.QuotaReached(RequestQuota(movies = null, shows = QuotaLeft(limit = 5, remaining = 1, days = 7))),
            app.seerrRepository.request(SeerrMediaType.Show, 1399, SeasonChoice.Only(listOf(1, 2, 3))),
        )

        seerr.server.refuseConnections()
        assertEquals(RequestOutcome.Failed(ApiError.Network), app.seerrRepository.request(SeerrMediaType.Movie, 606))
        // A write is never repeated.
        assertEquals(4, app.jellyfin.requestsTo("POST", "/api/v1/request").size)
    }

    @Test
    fun myRequestsAreTheProfilesOwnNewestChangeFirstWithTheirState() = runTest {
        connected().onSignedIn("GET", "/api/v1/request") {
            Reply.json(
                """{"pageInfo":{"pages":2,"pageSize":20,"results":23,"page":1},"results":[
                {"id":5,"status":2,"type":"movie","media":{"tmdbId":10331,"mediaType":"movie","status":5},"requestedBy":{"id":7},"updatedAt":"2026-10-31T15:47:44.000Z"},
                {"id":4,"status":2,"type":"tv","media":{"tmdbId":19885,"mediaType":"tv","status":3},"seasons":[{"seasonNumber":3},{"seasonNumber":2}],"requestedBy":{"id":7}},
                {"id":3,"status":3,"type":"movie","media":{"tmdbId":603,"status":1},"requestedBy":{"id":7}},
                {"id":2,"status":4,"type":"movie","media":{"tmdbId":604,"status":3},"requestedBy":{"id":7}}]}""",
            )
        }

        val page = app.seerrRepository.myRequests().value()

        assertEquals(
            listOf(RequestState.Available, RequestState.Approved, RequestState.Declined, RequestState.Failed),
            page.requests.map { it.state },
        )
        assertEquals(listOf(2, 3), page.requests[1].seasons)
        assertFalse(page.requests[0].pendingApproval)
        assertTrue(page.hasMore)
        val query = app.jellyfin.requestsTo("GET", "/api/v1/request").single().query
        assertEquals("7", query["requestedBy"])
        assertEquals("modified", query["sort"])
        assertEquals("0", query["skip"])
    }

    @Test
    fun notSignedInToSeerrIsUnauthorizedWithoutACall() = runTest {
        app.signedIn()

        assertEquals(ApiResult.Failure(ApiError.Unauthorized), app.seerrRepository.search("anything"))
        assertTrue(app.jellyfin.requests.none { it.path.startsWith("/api/v1") })
    }
}
