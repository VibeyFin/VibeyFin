package app.vibeyfin.data.discovery

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.core.writeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The real broadcast on a real network stack. A stand-in Jellyfin listens on the discovery port of
 * this same device, the way a server would, and the search finds it through 255.255.255.255 with
 * the real multicast lock. It needs the device to be on a network, and skips itself otherwise.
 */
class DeviceDiscoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aServerListeningOnTheDiscoveryPortIsFoundThroughTheRealBroadcast() = runBlocking {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        assumeTrue("The device needs a network to broadcast on", connectivity.activeNetwork != null)

        val selector = SelectorManager(Dispatchers.IO)
        val scope = CoroutineScope(Dispatchers.IO)
        val server = aSocket(selector).udp().bind(InetSocketAddress("0.0.0.0", UdpLanDiscovery.DISCOVERY_PORT))
        try {
            scope.launch {
                while (true) {
                    val question = server.receive()
                    if (question.packet.readText().contains("who is JellyfinServer?", ignoreCase = true)) {
                        val answer = """{"Address":"http://192.0.2.10:8096","Id":"device-test","Name":"Device test","EndpointAddress":null}"""
                        server.send(Datagram(buildPacket { writeText(answer) }, question.address))
                    }
                }
            }

            val found = UdpLanDiscovery(AndroidMulticastLock(context), window = 3.seconds).discover().toList()

            assertEquals(listOf("device-test"), found.map { it.id })
            assertEquals("http://192.0.2.10:8096", found.single().address)
        } finally {
            scope.cancel()
            server.close()
            selector.close()
        }
    }

    @Test
    fun theLockCanBeTakenAgainAfterASearchEndedOrFailed() = runBlocking {
        val lock = AndroidMulticastLock(context)
        var runs = 0

        lock.withLock { runs++ }
        runCatching { lock.withLock { error("the search failed") } }
        lock.withLock { runs++ }

        assertEquals(2, runs)
    }
}
