package app.vibeyfin.dev

import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpMethod

/** Which requests wait while the gate is closed: reads of data. Writes, sign-in, discovery, session checks, playback and file transfers go through. */
object HeldRequests {
    private const val SEERR_API_PATH: String = "/api/v1"

    private val jellyfinPassing: List<String> = listOf("/System/", "/Users/Public", "/Users/Me", "/Users/Authenticate", "/QuickConnect/", "/Sessions/", "/Localization/")
    private val seerrPassing: List<String> = listOf("/status", "/settings/public", "/auth/")

    fun jellyfin(method: HttpMethod, path: String): Boolean = method == HttpMethod.Get && jellyfinPassing.none { path.contains(it) } && !path.endsWith("/Download")

    fun seerr(method: HttpMethod, path: String): Boolean {
        val apiPath: String = path.substringAfter(SEERR_API_PATH)
        return method == HttpMethod.Get && seerrPassing.none { apiPath.startsWith(it) }
    }
}

/**
 * Holds the requests [isHeld] picks while [gate] is closed. It suspends before the request is sent, so the client's timeouts only
 * start once the gate opens.
 */
fun skeletonHold(gate: SkeletonGate, isHeld: (HttpMethod, String) -> Boolean): ClientPlugin<Unit> = createClientPlugin("SkeletonHold") {
    onRequest { request, _ ->
        if (isHeld(request.method, request.url.build().encodedPath)) gate.awaitOpen()
    }
}
