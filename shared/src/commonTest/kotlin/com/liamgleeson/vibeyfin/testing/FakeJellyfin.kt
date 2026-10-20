package com.liamgleeson.vibeyfin.testing

import com.liamgleeson.vibeyfin.data.api.ClientInfo
import com.liamgleeson.vibeyfin.data.api.JellyfinApi
import com.liamgleeson.vibeyfin.data.api.JellyfinApiFactory
import com.liamgleeson.vibeyfin.data.api.JellyfinHttpClient
import com.liamgleeson.vibeyfin.data.api.RetryPolicy
import com.liamgleeson.vibeyfin.data.api.UnauthorizedEvents
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/** A request as the fake server saw it. [path] is relative to the server's address, without the query. */
class RecordedRequest(
    val method: String,
    val address: String,
    val path: String,
    val query: Parameters,
    val authorization: String?,
    val body: String,
)

/** How a fake server answers one call. */
class Reply(val status: HttpStatusCode, val body: String, val contentType: String) {
    companion object {
        fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK) = Reply(status, body, "application/json")
        fun status(status: HttpStatusCode, body: String = "") = Reply(status, body, "text/plain")
        fun noContent() = Reply(HttpStatusCode.NoContent, "", "text/plain")
    }
}

/** Thrown from a handler to look like a connection that never answers. */
class ConnectionRefused : Exception("connection refused")

/** One server behind one address, e.g. `http://nas:8096`. Unknown paths answer 404. */
class FakeServer(val address: String) {
    private val handlers = mutableMapOf<String, (RecordedRequest) -> Reply>()

    /** Answers `method path`. The handler may throw [ConnectionRefused]. */
    fun on(method: String, path: String, handler: (RecordedRequest) -> Reply) {
        handlers["$method $path"] = handler
    }

    fun on(method: String, path: String, reply: Reply) = on(method, path) { reply }

    /** Makes every call to this address fail as if nothing were listening; pass false to bring it back. */
    fun refuseConnections(refuse: Boolean = true) {
        refusing = refuse
    }

    internal var refusing = false

    internal fun answer(request: RecordedRequest): Reply =
        handlers["${request.method} ${request.path}"]?.invoke(request) ?: Reply.status(HttpStatusCode.NotFound)
}

/**
 * A pretend network of Jellyfin servers. Addresses nobody registered refuse connections. Every
 * request is recorded in [requests]. Retried reads try [retries] times without waiting by default.
 */
class FakeJellyfin(retries: RetryPolicy = RetryPolicy(attempts = 3, backoff = Duration.ZERO)) {
    val requests = mutableListOf<RecordedRequest>()
    val unauthorized = UnauthorizedEvents()

    private val servers = mutableMapOf<String, FakeServer>()

    /** Calls can overlap on the engine's threads; a plain list would lose an append now and then. */
    private val recording = Mutex()

    fun server(address: String): FakeServer = servers.getOrPut(address) { FakeServer(address) }

    private val engine = MockEngine { data ->
        val url = data.url.toString()
        val server = servers.values.firstOrNull { url == it.address || url.startsWith(it.address + "/") || url.startsWith(it.address + "?") }
            ?: throw ConnectionRefused()
        if (server.refusing) throw ConnectionRefused()
        val recorded = RecordedRequest(
            method = data.method.value,
            address = server.address,
            path = url.removePrefix(server.address).substringBefore('?'),
            query = data.url.parameters,
            authorization = data.headers[HttpHeaders.Authorization],
            body = data.bodyText(),
        )
        recording.withLock { requests += recorded }
        val reply = server.answer(recorded)
        respond(
            content = reply.body,
            status = reply.status,
            headers = headersOf(HttpHeaders.ContentType, reply.contentType),
        )
    }

    private val http = JellyfinHttpClient.create(
        clientInfo = { ClientInfo(deviceName = "Test phone", deviceId = "device-1", appVersion = "1.0.0") },
        engine = engine,
    )

    val apiFactory = JellyfinApiFactory(http, unauthorized, retries)

    fun api(address: String, token: String? = null): JellyfinApi = apiFactory.create(address, token)

    /** The recorded requests for `method path`, in order. */
    fun requestsTo(method: String, path: String) = requests.filter { it.method == method && it.path == path }

    /**
     * Waits, in real time, until at least [count] requests have come in for `method path`, and
     * returns them. For calls the code under test makes in the background: they end up on the
     * engine's threads, so the test can't know when they are done any other way.
     */
    suspend fun awaitRequests(method: String, path: String, count: Int = 1): List<RecordedRequest> {
        repeat(AWAIT_ATTEMPTS) {
            val found = recording.withLock { requests.filter { it.method == method && it.path == path } }
            if (found.size >= count) return found
            withContext(Dispatchers.Default) { delay(AWAIT_STEP_MS) }
        }
        throw AssertionError("Expected $count request(s) to $method $path, saw ${requestsTo(method, path).size}")
    }

    /** What the server saw, in order, as `METHOD path`. */
    suspend fun callLog(): List<String> = recording.withLock { requests.map { "${it.method} ${it.path}" } }

    private companion object {
        const val AWAIT_ATTEMPTS = 500
        const val AWAIT_STEP_MS = 10L
    }

    private fun HttpRequestData.bodyText(): String = when (val content = body) {
        is TextContent -> content.text
        is ByteArrayContent -> content.bytes().decodeToString()
        else -> ""
    }
}
