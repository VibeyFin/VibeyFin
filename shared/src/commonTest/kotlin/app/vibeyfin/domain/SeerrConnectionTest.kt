package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.testing.FakeSeerr
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.seerrUserJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SeerrConnectionTest {
    private val app = TestApp()
    private val seerrAddress = "http://seerr:5055"

    @AfterTest
    fun tearDown() = runTest { app.close() }

    private suspend fun await(predicate: (SeerrState) -> Boolean): SeerrState = withContext(Dispatchers.Default) {
        withTimeout(5.seconds) { app.seerr.state.first(predicate) }
    }

    // "Alex" (u1) signed in on "Home" at http://nas:8096, with a working Seerr linked to it, and the connection settled.
    private suspend fun signedInWithSeerr(): Pair<FakeServer, FakeSeerr> {
        val jellyfin = app.signedIn()
        val seerr = FakeSeerr(app.jellyfin.server(seerrAddress), jellyfin)
        await { it == SeerrState.NotSet }
        return jellyfin to seerr
    }

    private fun calls(path: String) = app.jellyfin.requests.count { it.path == path }

    @Test
    fun withoutAnAddressSeerrIsNotSetAndNothingIsAsked() = runTest {
        app.signedIn()

        assertEquals(SeerrState.NotSet, await { it != SeerrState.Hidden })
        assertTrue(app.jellyfin.requests.none { it.address == seerrAddress })
    }

    @Test
    fun connectingSignsInWithoutAPasswordAndKeepsOnlyTheSessionCookie() = runTest {
        signedInWithSeerr()

        assertEquals(SeerrConnectResult.Connected, app.seerr.connect("http://seerr:5055/"))

        val ready = assertIs<SeerrState.Ready>(app.seerr.state.value)
        assertEquals(seerrAddress, ready.address)
        assertEquals("Alex", ready.user.name)
        // The profile approved Seerr's code on its own Jellyfin, with its own token, as itself.
        val approval = app.jellyfin.requestsTo("POST", "/QuickConnect/Authorize").single()
        assertEquals("123456", approval.query["code"])
        assertEquals("u1", approval.query["userId"])
        assertTrue(approval.authorization!!.contains("""Token="token-1""""))
        // No password went anywhere, and what is kept is the cookie, sealed, plus the address for the server.
        assertEquals(0, calls("/api/v1/auth/jellyfin"))
        assertEquals("sess-1", app.seerrStore.session("srv-1", "u1")?.session)
        assertEquals(7, app.seerrStore.session("srv-1", "u1")?.seerrUserId)
        assertEquals(seerrAddress, app.seerrStore.address("srv-1"))
    }

    @Test
    fun anAddressTypedWithoutASchemeIsTakenAsHttps() {
        assertEquals("https://requests.example.org", normalizeSeerrAddress(" requests.example.org/ "))
        assertEquals("http://10.0.2.2:5155", normalizeSeerrAddress("http://10.0.2.2:5155/api/v1"))
        assertEquals("https://example.org/seerr", normalizeSeerrAddress("https://example.org/seerr/"))
        assertEquals("http://10.0.2.2:5155", normalizeSeerrAddress("HTTP://10.0.2.2:5155"))
        assertNull(normalizeSeerrAddress("not an address"))
        assertNull(normalizeSeerrAddress("ftp://example.org"))
        assertNull(normalizeSeerrAddress("   "))
    }

    @Test
    fun anAddressTypedWithoutASchemeFallsBackToPlainHttpWhenHttpsDoesntAnswer() = runTest {
        signedInWithSeerr()

        assertEquals(SeerrConnectResult.Connected, app.seerr.connect("seerr:5055"))

        assertEquals(seerrAddress, assertIs<SeerrState.Ready>(app.seerr.state.value).address)
    }

    @Test
    fun withQuickConnectOffTheAddressIsKeptAndThePasswordIsAskedOnceButNeverKept() = runTest {
        val (_, seerr) = signedInWithSeerr()
        seerr.quickConnectOnJellyfin = false

        assertEquals(SeerrConnectResult.NeedsPassword, app.seerr.connect(seerrAddress))
        assertEquals(SeerrState.NeedsPassword(seerrAddress), app.seerr.state.value)
        assertEquals(seerrAddress, app.seerrStore.address("srv-1"))

        assertEquals(SeerrPasswordResult.WrongPassword, app.seerr.signInWithPassword("nope"))
        assertEquals(SeerrPasswordResult.SignedIn, app.seerr.signInWithPassword("secret"))

        assertIs<SeerrState.Ready>(app.seerr.state.value)
        assertTrue(app.jellyfin.requestsTo("POST", "/api/v1/auth/jellyfin").last().body.contains("\"username\":\"Alex\""))
        // Only the cookie is stored.
        assertEquals("sess-1", app.seerrStore.session("srv-1", "u1")?.session)
    }

    @Test
    fun aSeerrLinkedToAnotherJellyfinIsRefusedAndNotSaved() = runTest {
        val jellyfin = app.signedIn()
        jellyfin.on("GET", "/QuickConnect/Enabled", Reply.json("true"))
        // This Seerr's codes come from another server, which ours has never heard of.
        FakeSeerr(app.jellyfin.server(seerrAddress), app.jellyfin.server("http://other:8096"))
        await { it == SeerrState.NotSet }

        assertEquals(SeerrConnectResult.WrongServer, app.seerr.connect(seerrAddress))

        assertEquals(SeerrState.NotSet, app.seerr.state.value)
        assertNull(app.seerrStore.address("srv-1"))
    }

    @Test
    fun aSeerrUserOfAnotherJellyfinUserIsSignedOutAndRefused() = runTest {
        val (_, seerr) = signedInWithSeerr()
        seerr.user = seerrUserJson(jellyfinUserId = "someone-else")

        assertEquals(SeerrConnectResult.WrongServer, app.seerr.connect(seerrAddress))

        assertEquals(1, calls("/api/v1/auth/logout"))
        assertNull(app.seerrStore.session("srv-1", "u1"))
    }

    @Test
    fun anAddressNothingAnswersAtOrThatIsNotSeerrSaysSo() = runTest {
        signedInWithSeerr()
        app.jellyfin.server("http://plain:8080")

        assertEquals(SeerrConnectResult.Unreachable, app.seerr.connect("http://nothing-here:5055"))
        assertEquals(SeerrConnectResult.NotSeerr, app.seerr.connect("http://plain:8080"))
        assertEquals(SeerrState.NotSet, app.seerr.state.value)
    }

    @Test
    fun aSavedSessionIsUsedAgainWithoutSigningIn() = runTest {
        signedInWithSeerr()
        app.seerr.connect(seerrAddress)

        app.connection.switchProfile()
        await { it == SeerrState.Hidden }
        app.connection.activate("srv-1", "u1")

        assertIs<SeerrState.Ready>(await { it is SeerrState.Ready })
        assertEquals(1, calls("/api/v1/auth/jellyfin/quickconnect/initiate"))
    }

    @Test
    fun aSessionSeerrEndedIsRenewedSilentlyAndTheCallRunsAgain() = runTest {
        val (_, seerr) = signedInWithSeerr()
        seerr.onSignedIn("GET", "/api/v1/search") { Reply.json("""{"page":1,"totalPages":1,"totalResults":0,"results":[]}""") }
        app.seerr.connect(seerrAddress)
        seerr.expireSessions()

        val result = app.seerrRepository.search("harbour")

        assertIs<ApiResult.Success<*>>(result)
        assertEquals(2, calls("/api/v1/auth/jellyfin/quickconnect/initiate"))
        assertEquals("sess-2", app.seerrStore.session("srv-1", "u1")?.session)
    }

    @Test
    fun renewingASessionKeepsSeerrReadyThroughout() = runTest {
        val (_, seerr) = signedInWithSeerr()
        seerr.onSignedIn("GET", "/api/v1/search") { Reply.json("""{"page":1,"totalPages":1,"totalResults":0,"results":[]}""") }
        app.seerr.connect(seerrAddress)
        seerr.expireSessions()
        val seen = mutableListOf<SeerrState>()
        val watching = launch(Dispatchers.Unconfined) { app.seerr.state.collect { seen += it } }

        app.seerrRepository.search("harbour")
        watching.cancel()

        assertTrue(seen.all { it is SeerrState.Ready }, "states during renewal: $seen")
    }

    @Test
    fun aCallerCancelledWhileTheSessionRenewsLeavesSeerrSignedInNotStuckConnecting() = runTest {
        val (jellyfin, seerr) = signedInWithSeerr()
        seerr.onSignedIn("GET", "/api/v1/search") { Reply.json("""{"page":1,"totalPages":1,"totalResults":0,"results":[]}""") }
        app.seerr.connect(seerrAddress)
        seerr.expireSessions()
        // The screen that made the call closes while its renewal is approving the Quick Connect code.
        var caller: Job? = null
        val approve = jellyfin.handlerFor("POST", "/QuickConnect/Authorize")
        jellyfin.on("POST", "/QuickConnect/Authorize") { request ->
            caller?.cancel()
            approve(request)
        }
        caller = launch(Dispatchers.Default) { app.seerrRepository.search("harbour") }
        caller.join()

        assertIs<SeerrState.Ready>(await { it is SeerrState.Ready || it is SeerrState.Unavailable })
        assertEquals("sess-2", app.seerrStore.session("srv-1", "u1")?.session)
    }

    @Test
    fun aSeerrThatKeepsRefusingNeverLocksTheJellyfinProfile() = runTest {
        val (jellyfin, seerr) = signedInWithSeerr()
        seerr.onSignedIn("GET", "/api/v1/search") { Reply.status(HttpStatusCode.Unauthorized) }
        app.seerr.connect(seerrAddress)
        // From now on Seerr refuses every session, and its Quick Connect stops working too.
        seerr.expireSessions()
        jellyfin.on("POST", "/QuickConnect/Authorize", Reply.status(HttpStatusCode.Forbidden))

        val result = app.seerrRepository.search("harbour")

        assertEquals(ApiResult.Failure(ApiError.Unauthorized), result)
        assertIs<ConnectionState.Connected>(app.connection.state.value)
        assertEquals("token-1", app.accounts.sessionToken("srv-1", "u1"))
        assertIs<SeerrState.Unavailable>(app.seerr.state.value)
    }

    @Test
    fun disconnectingTurnsSeerrOffForThisProfileOnly() = runTest {
        signedInWithSeerr()
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        app.seerr.connect(seerrAddress)

        app.seerr.disconnect()

        assertEquals(SeerrState.Off(seerrAddress), app.seerr.state.value)
        assertNull(app.seerrStore.session("srv-1", "u1"))
        assertEquals(1, calls("/api/v1/auth/logout"))

        // Another profile on the server signs in to Seerr by itself, with no prompt.
        app.connection.switchProfile()
        await { it == SeerrState.Hidden }
        app.connection.activate("srv-1", "u2")
        assertIs<SeerrState.Ready>(await { it is SeerrState.Ready })
        assertEquals("u2", app.jellyfin.requestsTo("POST", "/QuickConnect/Authorize").last().query["userId"])

        // The first stays off, without signing in, until it connects again.
        app.connection.switchProfile()
        await { it == SeerrState.Hidden }
        app.connection.activate("srv-1", "u1")
        assertEquals(SeerrState.Off(seerrAddress), await { it is SeerrState.Off })
        assertEquals(2, calls("/api/v1/auth/jellyfin/quickconnect/initiate"))

        app.seerr.reconnect()
        assertIs<SeerrState.Ready>(app.seerr.state.value)
    }

    @Test
    fun aKidsProfileNeverSignsInAndTurningKidsOnSignsItOut() = runTest {
        signedInWithSeerr()
        app.seerr.connect(seerrAddress)

        app.profileSettings.setKidsProfile(true)

        assertEquals(SeerrState.Hidden, await { it == SeerrState.Hidden })
        assertNull(app.seerrStore.session("srv-1", "u1"))
        assertEquals(SeerrConnectResult.Failed, app.seerr.connect(seerrAddress))
        assertEquals(1, calls("/api/v1/auth/jellyfin/quickconnect/initiate"))
        // Its session is ended on Seerr too, not left valid there.
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { while (calls("/api/v1/auth/logout") == 0) delay(10) } }
    }

    @Test
    fun signingOutOfJellyfinDropsTheSeerrSession() = runTest {
        signedInWithSeerr()
        app.seerr.connect(seerrAddress)

        app.connection.signOut()

        assertEquals(SeerrState.Hidden, await { it == SeerrState.Hidden })
        assertNull(app.seerrStore.session("srv-1", "u1"))
        assertTrue("sealed:sess-1" in app.vault.discarded)
    }

    @Test
    fun aNewAddressSignsEveryProfileOutOfTheOldSeerr() = runTest {
        val (jellyfin, _) = signedInWithSeerr()
        app.seerr.connect(seerrAddress)
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        app.seerrStore.saveSession("srv-1", "u2", "sam-session", 8)
        FakeSeerr(app.jellyfin.server("http://seerr2:5055"), jellyfin)

        assertEquals(SeerrConnectResult.Connected, app.seerr.connect("http://seerr2:5055"))

        assertNull(app.seerrStore.session("srv-1", "u2"))
        assertEquals("http://seerr2:5055", app.seerrStore.address("srv-1"))
        assertEquals("http://seerr2:5055", assertIs<SeerrState.Ready>(app.seerr.state.value).address)
    }
}
