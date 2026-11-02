package app.vibeyfin.data.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Jellyfin sends PascalCase JSON with many fields we don't read. */
val JellyfinJson = Json {
    ignoreUnknownKeys = true
}

data class Timeouts(
    val connect: Duration,
    val request: Duration,
    val socket: Duration,
) {
    companion object {
        /** Probing a guessed address must fail fast: dead ports are common among the candidates. */
        val Probe = Timeouts(connect = 5.seconds, request = 8.seconds, socket = 8.seconds)

        /** The connect timeout matches [Probe]: saved addresses are tried in turn, so a dead one must not stall the rest. */
        val Api = Timeouts(connect = 5.seconds, request = 30.seconds, socket = 30.seconds)
    }
}

/** Set on a request to send that session's token; one client can then serve every session. */
internal val TokenAttribute = AttributeKey<String>("VibeyFinToken")

/**
 * Tells whoever listens that a server rejected a token (HTTP 401) on a call that carried one. A
 * 401 from a call without a token, such as a wrong password at sign-in, is not reported. Calls
 * report it in [JellyfinApi], which every authenticated request goes through.
 */
class UnauthorizedEvents {
    data class Rejection(val token: String, val baseUrl: String)

    private val events = MutableSharedFlow<Rejection>(extraBufferCapacity = 16)

    val rejections: SharedFlow<Rejection> get() = events

    val tokens: Flow<String> get() = events.map { it.token }

    internal fun report(token: String, baseUrl: String) {
        events.tryEmit(Rejection(token, baseUrl))
    }
}

object JellyfinHttpClient {
    /**
     * Builds a client that sends the `MediaBrowser` `Authorization` header on every request and
     * reads JSON leniently. Give a request [TokenAttribute] to sign it in, or pass a default [token].
     * [engine] is for tests; leave it out to use the platform's engine (OkHttp on Android, Darwin
     * on iOS).
     */
    fun create(
        clientInfo: suspend () -> ClientInfo,
        token: String? = null,
        timeouts: Timeouts = Timeouts.Api,
        engine: HttpClientEngine? = null,
    ): HttpClient {
        val configure: HttpClientConfig<*>.() -> Unit = {
            install(authorization(clientInfo, token))
            install(ContentNegotiation) { json(JellyfinJson) }
            install(HttpTimeout) {
                connectTimeoutMillis = timeouts.connect.inWholeMilliseconds
                requestTimeoutMillis = timeouts.request.inWholeMilliseconds
                socketTimeoutMillis = timeouts.socket.inWholeMilliseconds
            }
        }
        return if (engine != null) HttpClient(engine, configure) else HttpClient(configure)
    }

    private fun authorization(clientInfo: suspend () -> ClientInfo, defaultToken: String?) = createClientPlugin("JellyfinAuthorization") {
        onRequest { request, _ ->
            val token = request.attributes.getOrNull(TokenAttribute) ?: defaultToken
            request.headers[HttpHeaders.Authorization] = clientInfo().authorizationHeader(token)
        }
    }
}
