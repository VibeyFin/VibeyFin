package app.vibeyfin.data.seerr

import app.vibeyfin.data.api.Timeouts
import app.vibeyfin.dev.HeldRequests
import app.vibeyfin.dev.SkeletonGate
import app.vibeyfin.dev.skeletonHold
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Seerr sends camelCase JSON with many fields we don't read, and null for much of what we do. */
val SeerrJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * The HTTP client for Seerr, kept apart from Jellyfin's: it sends no `MediaBrowser` header, and its failures never reach
 * [app.vibeyfin.data.api.UnauthorizedEvents], so a Seerr 401 can't lock a Jellyfin profile.
 *
 * Seerr's session is the `connect.sid` cookie, which [SeerrApi] sends by hand on each request. Neither platform engine keeps
 * cookies of its own (OkHttp has no cookie jar by default, and Ktor's Darwin engine turns `NSHTTPCookieStorage` off), so a
 * session never leaks between profiles.
 */
object SeerrHttpClient {
    /** [engine] is for tests; leave it out to use the platform's engine. [gate] holds data requests in debug builds. */
    fun create(timeouts: Timeouts = Timeouts.Api, engine: HttpClientEngine? = null, gate: SkeletonGate? = null): HttpClient {
        val configure: HttpClientConfig<*>.() -> Unit = {
            gate?.let { install(skeletonHold(it, HeldRequests::seerr)) }
            install(ContentNegotiation) { json(SeerrJson) }
            install(HttpTimeout) {
                connectTimeoutMillis = timeouts.connect.inWholeMilliseconds
                requestTimeoutMillis = timeouts.request.inWholeMilliseconds
                socketTimeoutMillis = timeouts.socket.inWholeMilliseconds
            }
        }
        return if (engine != null) HttpClient(engine, configure) else HttpClient(configure)
    }
}
