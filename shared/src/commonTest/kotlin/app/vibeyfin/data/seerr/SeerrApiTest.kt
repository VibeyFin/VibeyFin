package app.vibeyfin.data.seerr

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.testing.FakeJellyfin
import app.vibeyfin.testing.Reply
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SeerrApiTest {
    private val network = FakeJellyfin()
    private val seerr = network.server("http://seerr:5055")

    @Test
    fun searchTermsArePercentEncodedStrictlyAsSeerrInsists() {
        assertEquals("living%20dead", seerrQueryEncode("living dead"))
        assertEquals("ocean%27s%2011", seerrQueryEncode("ocean's 11"))
        assertEquals("am%C3%A9lie", seerrQueryEncode("amélie"))
        assertEquals("a-b_c.d~e", seerrQueryEncode("a-b_c.d~e"))
        assertEquals("%2B%26%3D%3F%2F", seerrQueryEncode("+&=?/"))
    }

    @Test
    fun theSessionIsReadFromItsCookieOnly() {
        assertEquals("s%3Aabc.def", SeerrApi.sessionCookie("connect.sid=s%3Aabc.def; Path=/; HttpOnly; SameSite=Lax"))
        assertNull(SeerrApi.sessionCookie("other=1; Path=/"))
        assertNull(SeerrApi.sessionCookie("connect.sid=; Path=/"))
    }

    @Test
    fun aSignInHandsBackTheSessionWhichLaterCallsSendAsACookie() = runTest {
        seerr.on("POST", "/api/v1/auth/jellyfin/quickconnect/authenticate") {
            Reply(HttpStatusCode.OK, """{"id":7,"displayName":"Alex","jellyfinUserId":"u1","permissions":32}""", "application/json", mapOf(HttpHeaders.SetCookie to "connect.sid=s%3Axyz; Path=/; HttpOnly"))
        }
        seerr.on("GET", "/api/v1/auth/me", Reply.json("""{"id":7}"""))
        val api = network.seerrFactory.create("http://seerr:5055")

        val signIn = assertIs<ApiResult.Success<SeerrSignIn>>(api.quickConnectAuthenticate("secret-1")).value
        api.withSession(signIn.session).me()

        assertEquals("s%3Axyz", signIn.session)
        assertEquals("u1", signIn.user.jellyfinUserId)
        assertEquals("""{"secret":"secret-1"}""", network.requestsTo("POST", "/api/v1/auth/jellyfin/quickconnect/authenticate").single().body)
        assertEquals("connect.sid=s%3Axyz", network.requestsTo("GET", "/api/v1/auth/me").single().cookie)
        // Seerr is never sent the Jellyfin header.
        assertNull(network.requestsTo("GET", "/api/v1/auth/me").single().authorization)
    }

    @Test
    fun aSignInWithoutASessionCookieIsABadResponse() = runTest {
        seerr.on("POST", "/api/v1/auth/jellyfin", Reply.json("""{"id":7}"""))

        val result = network.seerrFactory.create("http://seerr:5055").signInWithPassword("Alex", "pw")

        assertEquals(ApiResult.Failure(ApiError.BadResponse), result)
    }

    @Test
    fun aSeerr401IsNeverReportedAsARejectedJellyfinToken() = runTest {
        val reported = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { network.unauthorized.tokens.collect { reported += it } }
        seerr.on("GET", "/api/v1/auth/me", Reply.status(HttpStatusCode.Unauthorized))

        val result = network.seerrFactory.create("http://seerr:5055", session = "old").me()

        assertEquals(ApiResult.Failure(ApiError.Unauthorized), result)
        assertEquals(emptyList(), reported)
    }
}
