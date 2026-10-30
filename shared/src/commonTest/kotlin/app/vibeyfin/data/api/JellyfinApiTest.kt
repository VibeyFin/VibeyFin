package app.vibeyfin.data.api

import app.vibeyfin.testing.ConnectionRefused
import app.vibeyfin.testing.FakeJellyfin
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.authJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JellyfinApiTest {
    private val address = "http://nas:8096"
    private val jellyfin = FakeJellyfin()
    private val server = jellyfin.server(address)

    private fun <T> ApiResult<T>.success(): T = assertIs<ApiResult.Success<T>>(this).value

    private fun ApiResult<*>.error(): ApiError = assertIs<ApiResult.Failure>(this).error

    @Test
    fun publicUsersAreReadWithoutSigningIn() = runTest {
        server.on(
            "GET",
            "/Users/Public",
            Reply.json("""[{"Id":"u1","Name":"Alex","HasPassword":true,"PrimaryImageTag":"abc","Extra":1},{"Id":"u2","Name":"Sam"}]"""),
        )

        val users = jellyfin.api(address).publicUsers().success()

        assertEquals(listOf("u1", "u2"), users.map { it.id })
        assertEquals("abc", users[0].primaryImageTag)
        assertNull(users[1].hasPassword)
        assertFalse(jellyfin.requests.single().authorization!!.contains("Token="))
    }

    @Test
    fun signingInSendsTheUsernameAndPasswordAsJson() = runTest {
        server.on("POST", "/Users/AuthenticateByName", Reply.json(authJson(userId = "u1", name = "Alex", token = "t-1")))

        val result = jellyfin.api(address).authenticateByName("alex", "pa\"ss").success()

        assertEquals("t-1", result.accessToken)
        assertEquals("Alex", result.user.name)
        val sent = jellyfin.requests.single().body
        assertTrue(sent.contains(""""Username":"alex""""), sent)
        assertTrue(sent.contains(""""Pw":"pa\"ss""""), sent)
    }

    @Test
    fun aWrongPasswordIsUnauthorizedAndIsNotReportedAsARejectedToken() = runTest(UnconfinedTestDispatcher()) {
        server.on("POST", "/Users/AuthenticateByName", Reply.status(HttpStatusCode.Unauthorized))
        val heard = async { jellyfin.unauthorized.tokens.first() }

        val result = jellyfin.api(address).authenticateByName("alex", "wrong")
        yield()

        assertEquals(ApiError.Unauthorized, result.error())
        assertFalse(heard.isCompleted, "a 401 without a token must not be reported")
        heard.cancel()
    }

    @Test
    fun theTokenGoesInTheAuthorizationHeader() = runTest {
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex"}"""))

        jellyfin.api(address, token = "secret-token").me().success()

        assertTrue(jellyfin.requests.single().authorization!!.contains("""Token="secret-token""""))
    }

    @Test
    fun aRejectedTokenIsReported() = runTest(UnconfinedTestDispatcher()) {
        server.on("GET", "/Users/Me", Reply.status(HttpStatusCode.Unauthorized))
        val heard = async { jellyfin.unauthorized.tokens.first() }

        val result = jellyfin.api(address, token = "old-token").me()

        assertEquals(ApiError.Unauthorized, result.error())
        assertEquals("old-token", heard.await())
    }

    @Test
    fun theSameApiCanBeSignedInWithAnotherToken() = runTest {
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1"}"""))

        jellyfin.api(address).withToken("later").me().success()

        assertTrue(jellyfin.requests.single().authorization!!.contains("""Token="later""""))
    }

    @Test
    fun statusesBecomeTypedErrors() = runTest {
        server.on("GET", "/Users/Public", Reply.status(HttpStatusCode.BadGateway))
        server.on("GET", "/Users/Me", Reply.status(HttpStatusCode.Forbidden))
        server.on("GET", "/QuickConnect/Enabled", Reply.status(HttpStatusCode.NotFound))
        server.on("POST", "/QuickConnect/Initiate", Reply.status(HttpStatusCode.TooManyRequests))
        val api = jellyfin.api(address)

        assertEquals(ApiError.Server(502), api.publicUsers().error())
        assertEquals(ApiError.Forbidden, api.me().error())
        assertEquals(ApiError.NotFound, api.quickConnectEnabled().error())
        assertEquals(ApiError.Unexpected(429), api.quickConnectInitiate().error())
    }

    @Test
    fun noAnswerIsANetworkError() = runTest {
        server.on("GET", "/Users/Public") { throw ConnectionRefused() }

        assertEquals(ApiError.Network, jellyfin.api(address).publicUsers().error())
        assertEquals(ApiError.Network, jellyfin.api("http://elsewhere:8096").publicUsers().error())
    }

    @Test
    fun aSuccessThatIsNotWhatWasAskedForIsABadResponse() = runTest {
        server.on("GET", "/Users/Public", Reply.json("""{"not":"a list"}"""))
        server.on("GET", "/Users/Me", Reply.json("<html>proxy page</html>"))

        assertEquals(ApiError.BadResponse, jellyfin.api(address).publicUsers().error())
        assertEquals(ApiError.BadResponse, jellyfin.api(address).me().error())
    }

    @Test
    fun loggingOutNeedsNoBody() = runTest {
        server.on("POST", "/Sessions/Logout", Reply.noContent())

        assertEquals(Unit, jellyfin.api(address, token = "t").logout().success())
        assertEquals(1, jellyfin.requestsTo("POST", "/Sessions/Logout").size)
    }

    @Test
    fun quickConnectRunsInThreeSteps() = runTest {
        server.on("GET", "/QuickConnect/Enabled", Reply.json("true"))
        server.on("POST", "/QuickConnect/Initiate", Reply.json("""{"Authenticated":false,"Secret":"sec","Code":"123456"}"""))
        server.on("GET", "/QuickConnect/Connect", Reply.json("""{"Authenticated":true,"Secret":"sec","Code":"123456"}"""))
        server.on("POST", "/Users/AuthenticateWithQuickConnect", Reply.json(authJson(token = "qc-token")))
        val api = jellyfin.api(address)

        assertTrue(api.quickConnectEnabled().success())
        val started = api.quickConnectInitiate().success()
        assertEquals("123456", started.code)
        assertFalse(started.authenticated)
        assertTrue(api.quickConnectStatus("sec").success().authenticated)
        assertEquals("qc-token", api.authenticateWithQuickConnect("sec").success().accessToken)

        assertEquals("sec", jellyfin.requestsTo("GET", "/QuickConnect/Connect").single().query["secret"])
        assertTrue(jellyfin.requestsTo("POST", "/Users/AuthenticateWithQuickConnect").single().body.contains(""""Secret":"sec""""))
    }

    @Test
    fun avatarUrlsPointAtTheUsersPrimaryImage() {
        assertEquals(
            "http://nas:8096/Users/u1/Images/Primary?tag=abc&maxWidth=256",
            jellyfin.api(address).userImageUrl("u1", "abc"),
        )
        assertEquals(
            "https://x.example/jf/Users/u2/Images/Primary?tag=t&maxWidth=96",
            userImageUrl("https://x.example/jf", "u2", "t", 96),
        )
    }
}
