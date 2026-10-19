package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.api.ApiError
import com.liamgleeson.vibeyfin.domain.model.ProfileRef
import com.liamgleeson.vibeyfin.testing.FakeServer
import com.liamgleeson.vibeyfin.testing.Reply
import com.liamgleeson.vibeyfin.testing.TestApp
import com.liamgleeson.vibeyfin.testing.authJson
import com.liamgleeson.vibeyfin.testing.publicUsersJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryTest {
    private val app = TestApp()
    private val address = "http://nas:8096"

    @AfterTest
    fun tearDown() = app.close()

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
}
