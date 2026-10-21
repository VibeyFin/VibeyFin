package app.vibeyfin.data.discovery

import app.vibeyfin.data.api.JellyfinJson
import app.vibeyfin.domain.DiscoveredServer
import app.vibeyfin.domain.LanDiscovery
import app.vibeyfin.domain.ServerAddresses
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.SocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.core.writeText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Jellyfin's auto-discovery: a UDP broadcast of `who is JellyfinServer?` to port 7359. Each
 * server on the network answers the sender directly with a small JSON document.
 *
 * Replies are collected for [window]. The question is asked again every [resendEvery] because UDP
 * can drop it; a server that hears it twice is still reported once.
 */
class UdpLanDiscovery(
    private val multicastLock: MulticastLock,
    private val target: InetSocketAddress = InetSocketAddress(BROADCAST_ADDRESS, DISCOVERY_PORT),
    private val window: Duration = 3.seconds,
    private val resendEvery: Duration = 1.seconds,
) : LanDiscovery {
    override val isSupported = true

    override fun discover(): Flow<DiscoveredServer> = channelFlow {
        try {
            multicastLock.withLock { listen { send(it) } }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No network, or the radio refused the broadcast. Discovery is best effort, so the
            // search just ends with nothing found; adding a server by address still works.
        }
    }

    private suspend fun listen(onFound: suspend (DiscoveredServer) -> Unit) = coroutineScope {
        val selector = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selector).udp().bind(InetSocketAddress("0.0.0.0", 0)) { broadcast = true }
        try {
            val asking = launch {
                while (true) {
                    socket.send(Datagram(buildPacket { writeText(QUESTION) }, target))
                    delay(resendEvery)
                }
            }
            val seen = mutableSetOf<String>()
            withTimeoutOrNull(window) {
                while (true) {
                    val reply = socket.receive()
                    val server = parseReply(reply.packet.readText(), reply.address.ipv4())
                    if (server != null && seen.add(server.id)) onFound(server)
                }
            }
            asking.cancel()
        } finally {
            socket.close()
            selector.close()
        }
    }

    internal companion object {
        const val DISCOVERY_PORT = 7359
        const val BROADCAST_ADDRESS = "255.255.255.255"
        const val QUESTION = "who is JellyfinServer?"

        /** Port Jellyfin listens on when a reply doesn't say. */
        private const val DEFAULT_PORT = 8096

        /**
         * Reads one reply, or returns null for anything that isn't a Jellyfin answer: other
         * traffic on the port, truncated JSON, a missing ID. [sourceHost] is where the datagram came
         * from, used when the server doesn't say how to reach it.
         */
        fun parseReply(text: String, sourceHost: String?): DiscoveredServer? {
            val reply = try {
                JellyfinJson.decodeFromString<DiscoveryReply>(text)
            } catch (_: Exception) {
                return null
            }
            val id = reply.id?.takeIf { it.isNotBlank() } ?: return null
            val address = reply.address?.trim()?.takeIf { ServerAddresses.candidates(it).isNotEmpty() }
                ?: sourceHost?.let { "http://$it:$DEFAULT_PORT" }
                ?: return null
            return DiscoveredServer(id = id, name = reply.name?.takeIf { it.isNotBlank() } ?: "Jellyfin", address = address)
        }
    }
}

/**
 * The sender's IPv4 address as text, or null. Built from the raw bytes: `hostname` does a reverse
 * DNS lookup on the JVM, which can block for seconds and returns a name such as "localhost".
 */
private fun SocketAddress.ipv4(): String? =
    (this as? InetSocketAddress)?.resolveAddress()?.takeIf { it.size == 4 }
        ?.joinToString(".") { (it.toInt() and 0xFF).toString() }

/** `MediaBrowser.Model.ApiClient.ServerDiscoveryInfo`, which is all the server sends. */
@Serializable
internal data class DiscoveryReply(
    @SerialName("Address") val address: String? = null,
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
)
