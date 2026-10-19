package com.liamgleeson.vibeyfin.data.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
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

        val Api = Timeouts(connect = 10.seconds, request = 30.seconds, socket = 30.seconds)
    }
}

object JellyfinHttpClient {
    /**
     * Builds a client that sends the `MediaBrowser` `Authorization` header on every request and
     * reads JSON leniently. Pass [token] for a signed-in session. [engine] is for tests; leave it
     * out to use the platform's engine (OkHttp on Android, Darwin on iOS).
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

    private fun authorization(clientInfo: suspend () -> ClientInfo, token: String?) =
        createClientPlugin("JellyfinAuthorization") {
            onRequest { request, _ ->
                request.headers[HttpHeaders.Authorization] = clientInfo().authorizationHeader(token)
            }
        }
}
