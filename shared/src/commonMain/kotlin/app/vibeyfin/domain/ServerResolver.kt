package app.vibeyfin.domain

import app.vibeyfin.data.api.PublicSystemInfoDto
import app.vibeyfin.data.api.isTlsFailure
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration
import kotlin.time.TimeSource

/** A Jellyfin server found at [address], the base URL that worked. */
data class ResolvedServer(
    val id: String,
    val name: String,
    val version: String,
    val address: String,
    /** Other addresses that reached the same server, best first. */
    val otherAddresses: List<String> = emptyList(),
)

/** Why an address didn't work, specific enough for the screen to say what to fix. */
sealed interface ConnectionIssue {
    /** The input can't be an address, so nothing was tried. */
    data object InvalidAddress : ConnectionIssue

    /** Nothing answered: wrong host or port, server off, or a different network. */
    data object Unreachable : ConnectionIssue

    /** Something answered but the secure connection failed, e.g. an untrusted certificate. */
    data object TlsFailed : ConnectionIssue

    /** Something answered with a 5xx, typically a proxy whose Jellyfin is down or starting up. */
    data class ServerUnavailable(val httpStatus: Int) : ConnectionIssue

    /** Something answered, but it isn't a Jellyfin server. */
    data class NotJellyfin(val httpStatus: Int) : ConnectionIssue

    data class VersionTooOld(val found: String, val minimum: String) : ConnectionIssue
}

sealed interface ResolveResult {
    data class Found(val server: ResolvedServer) : ResolveResult

    data class Failed(val issue: ConnectionIssue) : ResolveResult
}

/**
 * Finds a Jellyfin server from whatever the user typed: builds the candidate addresses, probes
 * them all at once with `/System/Info/Public`, and picks the best that works.
 */
class ServerResolver(
    private val client: HttpClient,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val isTlsFailure: (Throwable) -> Boolean = Throwable::isTlsFailure,
    private val minimumVersion: JellyfinVersion = JellyfinVersion.MINIMUM,
) {
    suspend fun resolve(input: String): ResolveResult {
        val candidates = ServerAddresses.candidates(input)
        if (candidates.isEmpty()) return ResolveResult.Failed(ConnectionIssue.InvalidAddress)

        val probes = coroutineScope {
            candidates.mapIndexed { index, address -> async { probe(index, address) } }.awaitAll()
        }

        val reached = probes.filterIsInstance<Probe.Reached>().sortedWith(RANKING)
        val best = reached.firstOrNull()
            ?: return ResolveResult.Failed(probes.filterIsInstance<Probe.Failed>().map { it.issue }.mostUseful())
        return ResolveResult.Found(
            ResolvedServer(
                id = best.id,
                name = best.name,
                version = best.version,
                address = best.address,
                otherAddresses = reached.drop(1)
                    .filter { it.id == best.id && it.address != best.address }
                    .map { it.address }
                    .distinct(),
            ),
        )
    }

    private suspend fun probe(index: Int, candidate: String): Probe {
        val started = timeSource.markNow()
        val response = try {
            client.get("$candidate$INFO_PATH")
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            return Probe.Failed(if (isTlsFailure(e)) ConnectionIssue.TlsFailed else ConnectionIssue.Unreachable)
        }
        val elapsed = started.elapsedNow()

        val status = response.status.value
        if (status >= 500) return Probe.Failed(ConnectionIssue.ServerUnavailable(status))
        if (status !in 200..299) return Probe.Failed(ConnectionIssue.NotJellyfin(status))

        val info = try {
            response.body<PublicSystemInfoDto>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return Probe.Failed(ConnectionIssue.NotJellyfin(status))
        }

        val id = info.id?.takeIf { it.isNotBlank() }
        val isJellyfin = info.productName.equals(PRODUCT_NAME, ignoreCase = true)
        val version = info.version?.let(JellyfinVersion::parse)
        if (id == null || !isJellyfin || version == null) return Probe.Failed(ConnectionIssue.NotJellyfin(status))
        if (version < minimumVersion) {
            return Probe.Failed(ConnectionIssue.VersionTooOld(found = version.toString(), minimum = minimumVersion.toString()))
        }

        // A redirect (http to https, a proxy adding a base path) changes where the server lives.
        val finalUrl = response.call.request.url.toString()
        return Probe.Reached(
            index = index,
            address = if (finalUrl.endsWith(INFO_PATH)) finalUrl.removeSuffix(INFO_PATH) else candidate,
            id = id,
            name = info.serverName?.takeIf { it.isNotBlank() } ?: "Jellyfin",
            version = version.toString(),
            elapsed = elapsed,
        )
    }

    private sealed interface Probe {
        class Reached(
            val index: Int,
            val address: String,
            val id: String,
            val name: String,
            val version: String,
            val elapsed: Duration,
        ) : Probe

        class Failed(val issue: ConnectionIssue) : Probe
    }

    private companion object {
        const val INFO_PATH = "/System/Info/Public"
        const val PRODUCT_NAME = "Jellyfin Server"

        /** Secure first, then the quicker answer, then the order the candidates were generated in. */
        val RANKING = compareByDescending<Probe.Reached> { it.address.startsWith("https://") }
            .thenBy { it.elapsed }
            .thenBy { it.index }

        /**
         * When nothing worked, report the failure that says the most: an answer that wasn't
         * Jellyfin beats a timeout, because it tells the user the host is right and the port or
         * path isn't.
         */
        fun List<ConnectionIssue>.mostUseful(): ConnectionIssue = maxByOrNull { it.usefulness() } ?: ConnectionIssue.Unreachable

        fun ConnectionIssue.usefulness() = when (this) {
            is ConnectionIssue.VersionTooOld -> 5
            is ConnectionIssue.NotJellyfin -> 4
            is ConnectionIssue.ServerUnavailable -> 3
            ConnectionIssue.TlsFailed -> 2
            ConnectionIssue.Unreachable -> 1
            ConnectionIssue.InvalidAddress -> 0
        }
    }
}
