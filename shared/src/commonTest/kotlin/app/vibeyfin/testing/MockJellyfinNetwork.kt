package app.vibeyfin.testing

import app.vibeyfin.data.api.ClientInfo
import app.vibeyfin.data.api.JellyfinHttpClient
import app.vibeyfin.data.api.Timeouts
import app.vibeyfin.domain.ServerResolver
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

typealias Route = MockRequestHandleScope.() -> HttpResponseData

class FakeTlsException : Exception("TLS handshake failed")

/**
 * A pretend network. Each route answers for one server base URL, e.g. `"http://nas:8096"`; any
 * other address refuses the connection. Requests and their `Authorization` headers are recorded.
 */
class MockJellyfinNetwork(vararg routes: Pair<String, Route>) {
    val requested = mutableListOf<String>()
    val authorization = mutableListOf<String?>()

    // Probes run side by side on the engine's threads; a plain list would lose an append now and then.
    private val recording = Mutex()
    private val byAddress = routes.toMap()

    fun resolver(timeSource: TimeSource = ScriptedTimeSource()): ServerResolver {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            recording.withLock {
                requested += url
                authorization += request.headers[HttpHeaders.Authorization]
            }
            val route = byAddress[url.removeSuffix("/System/Info/Public")]
                ?: throw Exception("connection refused")
            route()
        }
        val client = JellyfinHttpClient.create(
            clientInfo = { ClientInfo(deviceName = "Test phone", deviceId = "device-1", appVersion = "1.0.0") },
            timeouts = Timeouts.Probe,
            engine = engine,
        )
        return ServerResolver(client, timeSource, isTlsFailure = { it is FakeTlsException })
    }
}

/** A healthy Jellyfin answering `/System/Info/Public`. */
fun jellyfin(
    id: String = "srv-1",
    name: String = "Home",
    version: String = "10.10.7",
    product: String = "Jellyfin Server",
): Route = {
    respond(
        content = """{"LocalAddress":"http://10.0.0.2:8096","ServerName":"$name","Version":"$version",""" +
            """"ProductName":"$product","OperatingSystem":"","Id":"$id","StartupWizardCompleted":true}""",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )
}

/** Something that answers with a plain HTTP status, like a web server or a proxy. */
fun httpStatus(code: HttpStatusCode, body: String = ""): Route = {
    respond(content = body, status = code, headers = headersOf(HttpHeaders.ContentType, "text/html"))
}

/** A connection whose TLS handshake fails. */
fun tlsFailure(): Route = { throw FakeTlsException() }
