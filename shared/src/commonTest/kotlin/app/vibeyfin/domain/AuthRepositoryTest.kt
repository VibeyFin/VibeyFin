package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.ConnectionRefused
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.authJson
import app.vibeyfin.testing.publicUsersJson
import app.vibeyfin.testing.quickConnectJson
import app.vibeyfin.testing.virtualClock
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {
    private val app = TestApp()
    private val address = "http://nas:8096"

    @AfterTest
    fun tearDown() = runTest { app.close() }

    private suspend fun server(): FakeServer = app.serverAt(address)

    @Test
    fun readsTheServersPublicUsersAndWhetherQuickConnectIsOn() = runTest {
        server().apply {
            on("GET", "/Users/Public", Reply.json(publicUsersJson(Triple("u1", "Alex", true), Triple("u2", "Kim", false))))
            on("GET", "/QuickConnect/Enabled", Reply.json("true"))
        }

        val profiles = app.auth.loadPublicProfiles("srv-1")!!

        assertEquals(listOf("Alex", "Kim"), profiles.users.map { it.name })
        assertEquals(listOf(true, false), profiles.users.map { it.hasPassword })
        assertEquals(address, profiles.baseUrl)
        assertTrue(profiles.quickConnectEnabled)
    }

    @Test
    fun aUserWhoseHasPasswordIsMissingCountsAsHavingOne() = runTest {
        server().on("GET", "/Users/Public", Reply.json("""[{"Id":"u1","Name":"Alex"}]"""))

        assertTrue(app.auth.loadPublicProfiles("srv-1")!!.users.single().hasPassword)
    }

    @Test
    fun quickConnectCountsAsOffWhenTheServerDoesNotSay() = runTest {
        server().on("GET", "/Users/Public", Reply.json("[]"))

        assertFalse(app.auth.loadPublicProfiles("srv-1")!!.quickConnectEnabled)
    }

    @Test
    fun anUnreachableServerHasNoPublicProfiles() = runTest {
        server().refuseConnections()

        assertNull(app.auth.loadPublicProfiles("srv-1"))
    }

    @Test
    fun signingInSavesTheSessionAndMakesItTheCurrentConnection() = runTest {
        server().on("POST", "/Users/AuthenticateByName", Reply.json(authJson(userId = "u1", name = "Alex", token = "t-1", imageTag = "img")))

        val result = app.auth.signIn("srv-1", "alex", "secret")

        assertEquals(SignInResult.SignedIn, result)
        assertEquals("t-1", app.accounts.sessionToken("srv-1", "u1"))
        val account = app.accounts.account("srv-1", "u1")!!
        assertEquals("Alex", account.name)
        assertEquals("img", account.imageTag)
        val state = assertIs<ConnectionState.Connected>(app.connection.state.value)
        assertEquals("u1", state.account.userId)
        assertEquals(ProfileRef("srv-1", "u1"), app.preferences.lastProfile.first())
    }

    @Test
    fun anEmptyPasswordIsSentAsIs() = runTest {
        server().on("POST", "/Users/AuthenticateByName", Reply.json(authJson()))

        app.auth.signIn("srv-1", "kim", "")

        assertTrue(app.jellyfin.requests.single().body.contains(""""Pw":"""""))
    }

    @Test
    fun aWrongPasswordSavesNothingAndConnectsNothing() = runTest {
        server().on("POST", "/Users/AuthenticateByName", Reply.status(HttpStatusCode.Unauthorized))

        val result = app.auth.signIn("srv-1", "alex", "wrong")

        assertEquals(SignInResult.InvalidCredentials, result)
        assertNull(app.accounts.account("srv-1", "u1"))
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
        assertNull(app.preferences.lastProfile.first())
    }

    @Test
    fun anUnreachableServerIsReportedAsSuch() = runTest {
        server().refuseConnections()

        assertEquals(SignInResult.ServerUnreachable, app.auth.signIn("srv-1", "alex", "pw"))
    }

    @Test
    fun aServerErrorIsReportedWithItsStatus() = runTest {
        server().on("POST", "/Users/AuthenticateByName", Reply.status(HttpStatusCode.InternalServerError))

        assertEquals(SignInResult.Failed(ApiError.Server(500)), app.auth.signIn("srv-1", "alex", "pw"))
    }

    @Test
    fun anAnswerWithoutATokenIsABadResponse() = runTest {
        server().on("POST", "/Users/AuthenticateByName", Reply.json("""{"User":{"Id":"u1","Name":"Alex"}}"""))

        assertEquals(SignInResult.Failed(ApiError.BadResponse), app.auth.signIn("srv-1", "alex", "pw"))
        assertNull(app.accounts.account("srv-1", "u1"))
    }

    @Test
    fun signingInAgainReplacesTheTokenAndReleasesTheOldOne() = runTest {
        var count = 0
        server().on("POST", "/Users/AuthenticateByName") { Reply.json(authJson(token = "t-${++count}")) }

        app.auth.signIn("srv-1", "alex", "pw")
        app.auth.signIn("srv-1", "alex", "pw")

        assertEquals("t-2", app.accounts.sessionToken("srv-1", "u1"))
        assertEquals(listOf("sealed:t-1"), app.vault.discarded)
    }

    @Test
    fun signingInRemembersWhichAddressAnswered() = runTest {
        app.servers.save("srv-1", "Home", "10.10.7", "http://lan:8096")
        app.clock.advance(kotlin.time.Duration.parse("1m"))
        app.servers.save("srv-1", "Home", "10.10.7", "https://jelly.example.com")
        app.jellyfin.server("https://jelly.example.com").refuseConnections()
        app.jellyfin.server("http://lan:8096").on("POST", "/Users/AuthenticateByName", Reply.json(authJson()))

        assertEquals(SignInResult.SignedIn, app.auth.signIn("srv-1", "alex", "pw"))

        assertEquals("http://lan:8096", assertIs<ConnectionState.Connected>(app.connection.state.value).api.baseUrl)
    }

    // Quick Connect

    private fun TestScope.qcAuth(clock: Clock = virtualClock()) = AuthRepository(app.connector, app.accounts, app.connection, clock)

    private suspend fun quickConnectServer(approveOnPoll: Int = 2): FakeServer {
        val server = server()
        server.on("POST", "/QuickConnect/Initiate", Reply.json(quickConnectJson(authenticated = false)))
        var polls = 0
        server.on("GET", "/QuickConnect/Connect") { Reply.json(quickConnectJson(authenticated = ++polls >= approveOnPoll)) }
        server.on("POST", "/Users/AuthenticateWithQuickConnect", Reply.json(authJson(userId = "u9", name = "Quinn", token = "qc-token")))
        return server
    }

    @Test
    fun quickConnectShowsACodeThenSignsInOnceAnotherDeviceApproves() = runTest {
        quickConnectServer(approveOnPoll = 3)

        val states = qcAuth().quickConnect("srv-1").toList()

        assertEquals(
            listOf(
                QuickConnectState.Starting,
                QuickConnectState.Waiting("123456", Instant.fromEpochMilliseconds(1_700_000_000_000) + 5.minutes),
                QuickConnectState.Approved,
                QuickConnectState.SignedIn,
            ),
            states,
        )
        assertEquals(3, app.jellyfin.requestsTo("GET", "/QuickConnect/Connect").size)
        assertEquals("qc-token", app.accounts.sessionToken("srv-1", "u9"))
        assertEquals("Quinn", assertIs<ConnectionState.Connected>(app.connection.state.value).account.name)
        assertTrue(app.jellyfin.requestsTo("POST", "/Users/AuthenticateWithQuickConnect").single().body.contains(""""Secret":"sec""""))
    }

    @Test
    fun quickConnectPollsEveryThreeSeconds() = runTest {
        quickConnectServer(approveOnPoll = 2)

        qcAuth().quickConnect("srv-1").toList()

        // Initiate at 0 s, two polls at 3 s and 6 s of virtual time.
        assertEquals(6_000L, testScheduler.currentTime)
    }

    @Test
    fun quickConnectGivesUpAfterFiveMinutes() = runTest {
        quickConnectServer(approveOnPoll = Int.MAX_VALUE)

        val states = qcAuth().quickConnect("srv-1").toList()

        assertEquals(QuickConnectState.TimedOut, states.last())
        assertEquals(300_000L, testScheduler.currentTime)
        assertNull(app.accounts.account("srv-1", "u9"))
    }

    @Test
    fun theTimeoutCanBeShorter() = runTest {
        quickConnectServer(approveOnPoll = Int.MAX_VALUE)
        val auth = AuthRepository(app.connector, app.accounts, app.connection, virtualClock(), quickConnectTimeout = 30.seconds)

        val states = auth.quickConnect("srv-1").toList()

        assertEquals(QuickConnectState.TimedOut, states.last())
        assertEquals(30_000L, testScheduler.currentTime)
    }

    @Test
    fun aServerWithQuickConnectOffIsUnavailable() = runTest {
        server().on("POST", "/QuickConnect/Initiate", Reply.status(HttpStatusCode.Unauthorized))

        assertEquals(listOf(QuickConnectState.Starting, QuickConnectState.Unavailable), qcAuth().quickConnect("srv-1").toList())
    }

    @Test
    fun quickConnectOnAnUnreachableServer() = runTest {
        server().refuseConnections()

        assertEquals(listOf(QuickConnectState.Starting, QuickConnectState.Unreachable), qcAuth().quickConnect("srv-1").toList())
    }

    @Test
    fun aServerErrorWhenAskingForACodeIsAFailure() = runTest {
        server().on("POST", "/QuickConnect/Initiate", Reply.status(HttpStatusCode.InternalServerError))

        assertEquals(
            QuickConnectState.Failed(ApiError.Server(500)),
            qcAuth().quickConnect("srv-1").toList().last(),
        )
    }

    @Test
    fun aDroppedConnectionWhilePollingIsRetried() = runTest {
        val server = server()
        server.on("POST", "/QuickConnect/Initiate", Reply.json(quickConnectJson(authenticated = false)))
        var polls = 0
        server.on("GET", "/QuickConnect/Connect") {
            if (++polls <= 2) throw ConnectionRefused() else Reply.json(quickConnectJson(authenticated = true))
        }
        server.on("POST", "/Users/AuthenticateWithQuickConnect", Reply.json(authJson()))

        assertEquals(QuickConnectState.SignedIn, qcAuth().quickConnect("srv-1").toList().last())
        assertEquals(3, polls)
    }

    @Test
    fun aCodeTheServerForgotMeansItExpired() = runTest {
        val server = server()
        server.on("POST", "/QuickConnect/Initiate", Reply.json(quickConnectJson(authenticated = false)))
        server.on("GET", "/QuickConnect/Connect", Reply.status(HttpStatusCode.NotFound))

        assertEquals(QuickConnectState.TimedOut, qcAuth().quickConnect("srv-1").toList().last())
    }

    @Test
    fun anErrorWhilePollingStopsTheAttempt() = runTest {
        val server = server()
        server.on("POST", "/QuickConnect/Initiate", Reply.json(quickConnectJson(authenticated = false)))
        server.on("GET", "/QuickConnect/Connect", Reply.status(HttpStatusCode.BadGateway))

        assertEquals(QuickConnectState.Failed(ApiError.Server(502)), qcAuth().quickConnect("srv-1").toList().last())
        assertEquals(1, app.jellyfin.requestsTo("GET", "/QuickConnect/Connect").size)
    }

    @Test
    fun stoppingTheCollectorStopsPolling() = runTest {
        quickConnectServer(approveOnPoll = Int.MAX_VALUE)

        qcAuth().quickConnect("srv-1").first { it is QuickConnectState.Waiting }

        assertEquals(0, app.jellyfin.requestsTo("GET", "/QuickConnect/Connect").size)
    }

    @Test
    fun askingToPollNowPollsAtOnce() = runTest {
        quickConnectServer(approveOnPoll = 1)
        val poke = MutableSharedFlow<Unit>()
        launch {
            delay(1.seconds)
            poke.emit(Unit)
        }

        val states = qcAuth().quickConnect("srv-1", poke).toList()

        assertEquals(QuickConnectState.SignedIn, states.last())
        assertEquals(1_000L, testScheduler.currentTime)
    }

    @Test
    fun theClockEndsTheWaitEvenWhenFewPollsRan() = runTest {
        quickConnectServer(approveOnPoll = Int.MAX_VALUE)

        val states = qcAuth(app.clock).quickConnect("srv-1")
            .onEach { if (it is QuickConnectState.Waiting) app.clock.advance(6.minutes) }
            .toList()

        assertEquals(QuickConnectState.TimedOut, states.last())
        assertEquals(3_000L, testScheduler.currentTime)
        assertTrue(app.jellyfin.requestsTo("GET", "/QuickConnect/Connect").isEmpty())
    }
}
