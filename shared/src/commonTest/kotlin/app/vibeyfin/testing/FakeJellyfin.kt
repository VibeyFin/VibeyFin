package app.vibeyfin.testing

import app.vibeyfin.data.api.ClientInfo
import app.vibeyfin.data.api.JellyfinApi
import app.vibeyfin.data.api.JellyfinApiFactory
import app.vibeyfin.data.api.JellyfinHttpClient
import app.vibeyfin.data.api.RetryPolicy
import app.vibeyfin.data.api.ServerReachability
import app.vibeyfin.data.api.UnauthorizedEvents
import app.vibeyfin.data.seerr.SeerrApiFactory
import app.vibeyfin.data.seerr.SeerrHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

/** A request as the fake server saw it. [path] is relative to the server's address, without the query. */
class RecordedRequest(
    val method: String,
    val address: String,
    val path: String,
    val query: Parameters,
    val authorization: String?,
    val body: String,
    /** The `Range` header, when the request had one. */
    val range: String? = null,
    /** The `Cookie` header: Seerr's session travels in it. */
    val cookie: String? = null,
    /** The query exactly as sent, still encoded. */
    val encodedQuery: String = "",
)

/** How a fake server answers one call. */
class Reply(
    val status: HttpStatusCode,
    val body: String,
    val contentType: String,
    /** Headers beyond `Content-Type`, such as `Content-Range` and `ETag`. */
    val headers: Map<String, String> = emptyMap(),
    /** The connection drops after this many bytes of [body]: the rest never arrives, as when the network goes mid-download. */
    val dropAfter: Int? = null,
) {
    companion object {
        /** A file's bytes (text here, for easy reading), as a download answers it. */
        fun file(body: String, etag: String? = null) = Reply(
            HttpStatusCode.OK,
            body,
            "application/octet-stream",
            listOfNotNull(HttpHeaders.ContentLength to body.length.toString(), etag?.let { HttpHeaders.ETag to it }).toMap(),
        )

        /**
         * The file as a server that supports ranges serves it: all of [body] for a plain request, the rest from the offset of a
         * `Range: bytes=N-` request as `206`. [lastModified] stands for the file's version, which Jellyfin sends instead of an ETag.
         */
        fun ranged(body: String, request: RecordedRequest, lastModified: String = "v1"): Reply {
            val from = request.range?.removePrefix("bytes=")?.removeSuffix("-")?.toIntOrNull()
            val headers = mapOf(HttpHeaders.LastModified to lastModified)
            return if (from == null) {
                Reply(HttpStatusCode.OK, body, "application/octet-stream", headers + (HttpHeaders.ContentLength to body.length.toString()))
            } else {
                Reply(
                    HttpStatusCode.PartialContent,
                    body.substring(from),
                    "application/octet-stream",
                    headers + (HttpHeaders.ContentRange to "bytes $from-${body.length - 1}/${body.length}"),
                )
            }
        }

        /** [ranged], but the connection drops after [dropAfter] bytes of what it sends. */
        fun rangedThenDrop(body: String, request: RecordedRequest, dropAfter: Int, lastModified: String = "v1"): Reply {
            val whole = ranged(body, request, lastModified)
            return Reply(whole.status, whole.body, whole.contentType, whole.headers, dropAfter)
        }

        /** The rest of a file from a `Range` request: [body] begins at [from] and the whole file is [total] bytes. */
        fun partial(body: String, from: Long, total: Long) = Reply(
            HttpStatusCode.PartialContent,
            body,
            "application/octet-stream",
            mapOf(HttpHeaders.ContentRange to "bytes $from-${total - 1}/$total"),
        )

        fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK) = Reply(status, body, "application/json")

        fun status(status: HttpStatusCode, body: String = "") = Reply(status, body, "text/plain")

        fun noContent() = Reply(HttpStatusCode.NoContent, "", "text/plain")
    }
}

/**
 * Holds a call at the fake server until the test lets it through, so a test can look at what the app shows while a write is still
 * in flight (an optimistic change) and then decide how it ends. Without one a call is answered as soon as it is made.
 */
class Gate {
    private val opened = CompletableDeferred<Unit>()

    /** Lets the held call (and any later one) through. The call finishes before this returns. */
    fun open() {
        opened.complete(Unit)
    }

    internal suspend fun await() = opened.await()
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

    /** The handler set for [method] and [path], to wrap it; a 404 when there is none. */
    fun handlerFor(method: String, path: String): (RecordedRequest) -> Reply = handlers["$method $path"] ?: { Reply.status(HttpStatusCode.NotFound) }

    /** Makes every call to this address fail as if nothing were listening; pass false to bring it back. */
    fun refuseConnections(refuse: Boolean = true) {
        refusing = refuse
    }

    internal var refusing = false

    private val gates = mutableMapOf<String, Gate>()

    /** Holds every call to `method path` until the returned [Gate] is opened. Set it up before the call is made. */
    fun hold(method: String, path: String): Gate = Gate().also { gates["$method $path"] = it }

    internal suspend fun answer(request: RecordedRequest): Reply {
        gates["${request.method} ${request.path}"]?.await()
        return handlers["${request.method} ${request.path}"]?.invoke(request) ?: Reply.status(HttpStatusCode.NotFound)
    }
}

/**
 * A pretend network of Jellyfin servers. Addresses nobody registered refuse connections. Every
 * request is recorded in [requests]. Retried reads try [retries] times without waiting by default.
 */
class FakeJellyfin(
    retries: RetryPolicy = RetryPolicy(attempts = 3, backoff = Duration.ZERO),
    reachability: ServerReachability? = null,
) {
    val requests = mutableListOf<RecordedRequest>()
    val unauthorized = UnauthorizedEvents()

    private val servers = mutableMapOf<String, FakeServer>()

    // Calls can overlap on the engine's threads; a plain list would lose an append now and then.
    private val recording = Mutex()

    fun server(address: String): FakeServer = servers.getOrPut(address) { FakeServer(address) }

    private val handler: MockRequestHandler = { data ->
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
            range = data.headers[HttpHeaders.Range],
            cookie = data.headers[HttpHeaders.Cookie],
            encodedQuery = data.url.encodedQuery,
        )
        recording.withLock { requests += recorded }
        val reply = server.answer(recorded)
        val headers = headersOf(
            HttpHeaders.ContentType to listOf(reply.contentType),
            *reply.headers.map { (name, value) -> name to listOf(value) }.toTypedArray(),
        )
        if (reply.dropAfter == null) {
            respond(content = reply.body, status = reply.status, headers = headers)
        } else {
            // Sends the first bytes and then breaks the connection.
            val bytes = reply.body.encodeToByteArray()
            val channel = CoroutineScope(Dispatchers.Unconfined).writer(autoFlush = true) {
                channel.writeFully(bytes, 0, reply.dropAfter)
                channel.flush()
                // Lets the reader take what was sent before the connection breaks, as a socket does.
                delay(DROP_DELAY_MS)
                throw ConnectionRefused()
            }.channel
            respond(content = channel, status = reply.status, headers = headers)
        }
    }

    // Answers on the caller's thread, not the engine's own pool: a call is done by the time the code that made it resumes, so a
    // test that sends an event and then reads state is not racing a thread.
    private val engine = MockEngine(
        MockEngineConfig().apply {
            dispatcher = Dispatchers.Unconfined
            addHandler(handler)
        },
    )

    private val http = JellyfinHttpClient.create(
        clientInfo = { ClientInfo(deviceName = "Test phone", deviceId = "device-1", appVersion = "1.0.0") },
        engine = engine,
    )

    // Bodies decode in the test's own context, so virtual time covers them
    val apiFactory = JellyfinApiFactory(http, unauthorized, retries, reachability, decodeOn = EmptyCoroutineContext)

    /** Seerr's client on the same pretend network: a Seerr is just another [server] address. */
    val seerrFactory = SeerrApiFactory(SeerrHttpClient.create(engine = engine), retries, decodeOn = EmptyCoroutineContext)

    fun api(address: String, token: String? = null): JellyfinApi = apiFactory.create(address, token)

    /** The recorded requests for `method path`, in order. */
    fun requestsTo(method: String, path: String): List<RecordedRequest> {
        // The engine records on its own thread while a test reads: read again if a request was added in the middle of it.
        repeat(20) {
            try {
                return requests.filter { it.method == method && it.path == path }
            } catch (_: ConcurrentModificationException) {
                // Try again.
            }
        }
        return requests.toList().filter { it.method == method && it.path == path }
    }

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
        const val DROP_DELAY_MS = 20L
    }

    private fun HttpRequestData.bodyText(): String = when (val content = body) {
        is TextContent -> content.text
        is ByteArrayContent -> content.bytes().decodeToString()
        else -> ""
    }
}
