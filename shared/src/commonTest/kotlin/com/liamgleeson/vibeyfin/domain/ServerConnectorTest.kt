package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.api.ApiError
import com.liamgleeson.vibeyfin.data.api.ApiResult
import com.liamgleeson.vibeyfin.testing.Reply
import com.liamgleeson.vibeyfin.testing.TestApp
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class ServerConnectorTest {
    private val app = TestApp()

    @AfterTest
    fun tearDown() = runTest { app.close() }

    private suspend fun saveWithTwoAddresses() {
        // "http://lan" worked first; the domain was added later, so it leads.
        app.servers.save("srv-1", "Home", "10.10.7", "http://lan:8096")
        app.clock.advance(1.minutes)
        app.servers.save("srv-1", "Home", "10.10.7", "https://jelly.example.com")
    }

    @Test
    fun theFirstAddressThatAnswersIsUsedAndRemembered() = runTest {
        saveWithTwoAddresses()
        app.jellyfin.server("https://jelly.example.com").refuseConnections()
        app.jellyfin.server("http://lan:8096").on("GET", "/Users/Public", Reply.json("[]"))

        val reached = app.connector.firstAnswer("srv-1") { it.publicUsers() }

        assertEquals("http://lan:8096", reached!!.api.baseUrl)
        assertIs<ApiResult.Success<*>>(reached.result)
        app.clock.advance(1.minutes)
        assertEquals("http://lan:8096", app.servers.get("srv-1")!!.addresses.first())
    }

    @Test
    fun theBestAddressIsTriedFirst() = runTest {
        saveWithTwoAddresses()
        app.jellyfin.server("https://jelly.example.com").on("GET", "/Users/Public", Reply.json("[]"))
        app.jellyfin.server("http://lan:8096").on("GET", "/Users/Public", Reply.json("[]"))

        app.connector.firstAnswer("srv-1") { it.publicUsers() }

        assertEquals(listOf("https://jelly.example.com"), app.jellyfin.requests.map { it.address })
    }

    @Test
    fun anErrorFromAServerThatAnsweredEndsTheSearch() = runTest {
        saveWithTwoAddresses()
        app.jellyfin.server("https://jelly.example.com").on("GET", "/Users/Me", Reply.status(HttpStatusCode.Unauthorized))
        app.jellyfin.server("http://lan:8096").on("GET", "/Users/Me", Reply.json("""{"Id":"u1"}"""))

        val reached = app.connector.firstAnswer("srv-1", token = "t") { it.me() }

        assertEquals(ApiError.Unauthorized, (reached!!.result as ApiResult.Failure).error)
        assertEquals(1, app.jellyfin.requests.size)
    }

    @Test
    fun nothingAnsweringGivesNull() = runTest {
        saveWithTwoAddresses()

        assertNull(app.connector.firstAnswer("srv-1") { it.publicUsers() })
    }

    @Test
    fun anUnknownServerGivesNull() = runTest {
        assertNull(app.connector.firstAnswer("missing") { it.publicUsers() })
        assertNull(app.connector.apiFor("missing", null))
    }

    @Test
    fun anApiOnTheBestAddressCanBeHadWithoutAskingTheServer() = runTest {
        saveWithTwoAddresses()

        val api = app.connector.apiFor("srv-1", "tok")

        assertEquals("https://jelly.example.com", api!!.baseUrl)
        assertEquals(emptyList(), app.jellyfin.requests)
    }
}
