package app.vibeyfin.domain

import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ConnectionManagerTest {
    private val app = TestApp()
    private val address = "http://nas:8096"

    @AfterTest
    fun tearDown() = runTest { app.close() }

    /** A saved server with a signed-in profile "u1" holding "token-1". */
    private suspend fun signedInProfile(): FakeServer {
        val server = app.serverAt(address)
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        return server
    }

    @Test
    fun aSavedSessionIsCheckedWithTheServerAndBecomesTheCurrentConnection() = runTest {
        signedInProfile().on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex"}"""))

        val result = app.connection.activate("srv-1", "u1")

        assertEquals(ActivateResult.Activated, result)
        val state = assertIs<ConnectionState.Connected>(app.connection.state.value)
        assertEquals("Alex", state.account.name)
        assertEquals("Home", state.server.name)
        assertTrue(app.jellyfin.requests.single().authorization!!.contains("""Token="token-1""""))
        assertEquals(ProfileRef("srv-1", "u1"), app.preferences.lastProfile.first())
    }

    @Test
    fun aRejectedTokenLocksTheProfileButKeepsItsTile() = runTest {
        signedInProfile().on("GET", "/Users/Me", Reply.status(HttpStatusCode.Unauthorized))

        val result = app.connection.activate("srv-1", "u1")

        assertEquals(ActivateResult.Expired, result)
        assertEquals(ConnectionState.Expired("srv-1", "u1"), app.connection.state.value)
        assertNull(app.accounts.sessionToken("srv-1", "u1"))
        assertFalse(app.accounts.observeAccounts("srv-1").first().single().hasSession)
    }

    @Test
    fun aProfileWithoutATokenIsExpired() = runTest {
        signedInProfile()
        app.accounts.expire("srv-1", "u1")

        assertEquals(ActivateResult.Expired, app.connection.activate("srv-1", "u1"))
        assertEquals(0, app.jellyfin.requests.size)
    }

    @Test
    fun anUnreachableServerKeepsTheTokenAndChangesNothing() = runTest {
        signedInProfile().refuseConnections()

        val result = app.connection.activate("srv-1", "u1")

        assertEquals(ActivateResult.Unreachable, result)
        assertEquals("token-1", app.accounts.sessionToken("srv-1", "u1"))
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
    }

    @Test
    fun aServerErrorIsNotTreatedAsAnExpiredSession() = runTest {
        signedInProfile().on("GET", "/Users/Me", Reply.status(HttpStatusCode.BadGateway))

        assertEquals(ActivateResult.Unreachable, app.connection.activate("srv-1", "u1"))
        assertEquals("token-1", app.accounts.sessionToken("srv-1", "u1"))
    }

    @Test
    fun anUnknownServerOrProfileIsUnknown() = runTest {
        signedInProfile()

        assertEquals(ActivateResult.Unknown, app.connection.activate("missing", "u1"))
        assertEquals(ActivateResult.Unknown, app.connection.activate("srv-1", "nobody"))
    }

    @Test
    fun aTokenRejectedOnAnyLaterCallLocksTheProfile() = runTest {
        val server = signedInProfile()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1"}"""))
        app.connection.activate("srv-1", "u1")
        val api = assertIs<ConnectionState.Connected>(app.connection.state.value).api
        server.on("GET", "/Users/Me", Reply.status(HttpStatusCode.Unauthorized))

        api.me()

        assertEquals(ConnectionState.Expired("srv-1", "u1"), app.connection.state.first { it is ConnectionState.Expired })
        assertNull(app.accounts.sessionToken("srv-1", "u1"))
    }

    @Test
    fun aRejectionOfSomeOtherTokenIsIgnored() = runTest {
        val server = signedInProfile()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1"}"""))
        app.connection.activate("srv-1", "u1")
        server.on("GET", "/Users/Me", Reply.status(HttpStatusCode.Unauthorized))

        app.jellyfin.api(address, token = "an-old-token").me()

        assertIs<ConnectionState.Connected>(app.connection.state.value)
        assertEquals("token-1", app.accounts.sessionToken("srv-1", "u1"))
    }

    @Test
    fun signingOutRevokesTheTokenAndLocksTheProfile() = runTest {
        val server = signedInProfile()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1"}"""))
        server.on("POST", "/Sessions/Logout", Reply.noContent())
        app.connection.activate("srv-1", "u1")

        app.connection.signOut()

        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
        assertTrue(app.jellyfin.requestsTo("POST", "/Sessions/Logout").single().authorization!!.contains("""Token="token-1""""))
        assertNull(app.accounts.sessionToken("srv-1", "u1"))
        assertEquals("Alex", app.accounts.account("srv-1", "u1")!!.name)
    }

    @Test
    fun signingOutLocksTheProfileEvenWhenTheServerCannotBeReached() = runTest {
        val server = signedInProfile()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1"}"""))
        app.connection.activate("srv-1", "u1")
        server.refuseConnections()

        app.connection.signOut()

        assertNull(app.accounts.sessionToken("srv-1", "u1"))
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
    }

    @Test
    fun switchingProfileKeepsTheTokenForNextTime() = runTest {
        signedInProfile().on("GET", "/Users/Me", Reply.json("""{"Id":"u1"}"""))
        app.connection.activate("srv-1", "u1")

        app.connection.switchProfile()

        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
        assertEquals("token-1", app.accounts.sessionToken("srv-1", "u1"))
    }

    @Test
    fun signingOutWhenNobodyIsSignedInDoesNothing() = runTest {
        app.connection.signOut()

        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
    }

    // Resuming on a cold start

    @Test
    fun resumingConnectsAtOnceWithoutWaitingForTheNetwork() = runTest {
        signedInProfile().refuseConnections()

        val result = app.connection.resume("srv-1", "u1")

        assertEquals(ActivateResult.Activated, result)
        assertEquals("Alex", assertIs<ConnectionState.Connected>(app.connection.state.value).account.name)
        assertEquals("token-1", app.accounts.sessionToken("srv-1", "u1"))
        assertEquals(ProfileRef("srv-1", "u1"), app.preferences.lastProfile.first())
    }

    @Test
    fun resumingThenFindingTheTokenRevokedLocksTheProfile() = runTest {
        signedInProfile().on("GET", "/Users/Me", Reply.status(HttpStatusCode.Unauthorized))

        app.connection.resume("srv-1", "u1")

        assertEquals(ConnectionState.Expired("srv-1", "u1"), app.connection.state.first { it is ConnectionState.Expired })
        assertNull(app.accounts.sessionToken("srv-1", "u1"))
    }

    @Test
    fun resumingAndFindingTheServerFineKeepsTheSession() = runTest {
        signedInProfile().on("GET", "/Users/Me", Reply.json("""{"Id":"u1"}"""))

        app.connection.resume("srv-1", "u1")

        // The check runs in the background, so wait for it to reach the server.
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) { while (app.jellyfin.requestsTo("GET", "/Users/Me").isEmpty()) delay(10) }
        }
        assertEquals(1, app.jellyfin.requestsTo("GET", "/Users/Me").size)
        assertIs<ConnectionState.Connected>(app.connection.state.value)
        assertEquals("token-1", app.accounts.sessionToken("srv-1", "u1"))
    }

    @Test
    fun resumingMovesCallsToTheAddressThatActuallyAnswers() = runTest {
        app.servers.save("srv-1", "Home", "10.10.7", "http://lan:8096")
        app.clock.advance(kotlin.time.Duration.parse("1m"))
        app.servers.save("srv-1", "Home", "10.10.7", "https://jelly.example.com")
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.jellyfin.server("https://jelly.example.com").refuseConnections()
        app.jellyfin.server("http://lan:8096").on("GET", "/Users/Me", Reply.json("""{"Id":"u1"}"""))

        app.connection.resume("srv-1", "u1")

        val moved = app.connection.state.first { (it as? ConnectionState.Connected)?.api?.baseUrl == "http://lan:8096" }
        assertIs<ConnectionState.Connected>(moved)
    }

    @Test
    fun resumingAProfileWithoutATokenIsExpired() = runTest {
        signedInProfile()
        app.accounts.expire("srv-1", "u1")

        assertEquals(ActivateResult.Expired, app.connection.resume("srv-1", "u1"))
        assertEquals(ConnectionState.Expired("srv-1", "u1"), app.connection.state.value)
    }

    @Test
    fun resumingAnUnknownProfileIsUnknown() = runTest {
        signedInProfile()

        assertEquals(ActivateResult.Unknown, app.connection.resume("srv-1", "nobody"))
        assertEquals(ActivateResult.Unknown, app.connection.resume("missing", "u1"))
    }
}
