package app.vibeyfin.dev

import app.vibeyfin.data.api.ClientInfo
import app.vibeyfin.data.api.JellyfinHttpClient
import app.vibeyfin.data.api.Timeouts
import app.vibeyfin.data.seerr.SeerrHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class SkeletonHoldTest {
    private val shortTimeouts: Timeouts = Timeouts(connect = 100.milliseconds, request = 100.milliseconds, socket = 100.milliseconds)

    @Test
    fun jellyfinDataIsHeld() {
        assertTrue(HeldRequests.jellyfin(HttpMethod.Get, "/UserViews"))
        assertTrue(HeldRequests.jellyfin(HttpMethod.Get, "/Items/abc/Similar"))
        assertTrue(HeldRequests.jellyfin(HttpMethod.Get, "/Shows/NextUp"))
    }

    @Test
    fun jellyfinAuthDiscoverySessionAndWritesAreNotHeld() {
        assertFalse(HeldRequests.jellyfin(HttpMethod.Get, "/System/Info/Public"))
        assertFalse(HeldRequests.jellyfin(HttpMethod.Get, "/Users/Public"))
        assertFalse(HeldRequests.jellyfin(HttpMethod.Get, "/Users/Me"))
        assertFalse(HeldRequests.jellyfin(HttpMethod.Post, "/Users/AuthenticateByName"))
        assertFalse(HeldRequests.jellyfin(HttpMethod.Get, "/QuickConnect/Enabled"))
        assertFalse(HeldRequests.jellyfin(HttpMethod.Post, "/Sessions/Playing/Progress"))
        assertFalse(HeldRequests.jellyfin(HttpMethod.Get, "/Items/abc/Download"))
        assertFalse(HeldRequests.jellyfin(HttpMethod.Post, "/Items"))
    }

    @Test
    fun seerrDataIsHeldAndItsAuthAndStatusAreNot() {
        assertTrue(HeldRequests.seerr(HttpMethod.Get, "/api/v1/discover/movies"))
        assertTrue(HeldRequests.seerr(HttpMethod.Get, "/api/v1/movie/603"))
        assertFalse(HeldRequests.seerr(HttpMethod.Get, "/api/v1/status"))
        assertFalse(HeldRequests.seerr(HttpMethod.Get, "/api/v1/settings/public"))
        assertFalse(HeldRequests.seerr(HttpMethod.Get, "/api/v1/auth/me"))
        assertFalse(HeldRequests.seerr(HttpMethod.Post, "/api/v1/request"))
    }

    @Test
    fun aServerBehindASubPathKeepsItsExemptionsAndHoldsData() {
        assertTrue(HeldRequests.jellyfin(HttpMethod.Get, "/jellyfin/UserViews"))
        assertFalse(HeldRequests.jellyfin(HttpMethod.Get, "/jellyfin/System/Info/Public"))
        assertFalse(HeldRequests.jellyfin(HttpMethod.Get, "/jellyfin/Users/Me"))
        assertTrue(HeldRequests.seerr(HttpMethod.Get, "/seerr/api/v1/discover/movies"))
        assertFalse(HeldRequests.seerr(HttpMethod.Get, "/seerr/api/v1/auth/me"))
        assertFalse(HeldRequests.seerr(HttpMethod.Get, "/seerr/api/v1/status"))
    }

    @Test
    fun aHeldRequestWaitsForTheGateAndOutlivesTheRequestTimeout() = runBlocking {
        val flags = DevFlags(enabled = true)
        val gate = SkeletonGate(flags)
        val client = JellyfinHttpClient.create(
            clientInfo = { ClientInfo(deviceName = "Test", deviceId = "id", appVersion = "1.0") },
            timeouts = shortTimeouts,
            engine = MockEngine { respondOk("ok") },
            gate = gate,
        )
        flags.set(DevFlagRegistry.showSkeletons, true)

        val held: Deferred<HttpResponse> = CoroutineScope(Dispatchers.Default).async { client.get("http://server/UserViews") }
        delay(300.milliseconds)
        assertFalse(held.isCompleted)
        flags.set(DevFlagRegistry.showSkeletons, false)

        assertEquals(200, held.await().status.value)
    }

    @Test
    fun anExemptRequestPassesWhileTheGateIsClosed() = runBlocking {
        val flags = DevFlags(enabled = true)
        val client = JellyfinHttpClient.create(
            clientInfo = { ClientInfo(deviceName = "Test", deviceId = "id", appVersion = "1.0") },
            engine = MockEngine { respondOk("ok") },
            gate = SkeletonGate(flags),
        )
        flags.set(DevFlagRegistry.showSkeletons, true)

        assertEquals(200, client.get("http://server/Users/Me").status.value)
        assertEquals(200, client.post("http://server/Items").status.value)
    }

    @Test
    fun seerrHoldsDataToo() = runBlocking {
        val flags = DevFlags(enabled = true)
        val client = SeerrHttpClient.create(
            timeouts = shortTimeouts,
            engine = MockEngine { respondOk("ok") },
            gate = SkeletonGate(flags),
        )
        flags.set(DevFlagRegistry.showSkeletons, true)

        val held: Deferred<HttpResponse> = CoroutineScope(Dispatchers.Default).async { client.get("http://seerr/api/v1/discover/movies") }
        delay(300.milliseconds)
        assertFalse(held.isCompleted)
        assertEquals(200, client.get("http://seerr/api/v1/status").status.value)
        flags.set(DevFlagRegistry.showSkeletons, false)

        assertEquals(200, held.await().status.value)
    }
}
