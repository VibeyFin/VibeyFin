package com.liamgleeson.vibeyfin.data.discovery

import com.liamgleeson.vibeyfin.domain.DiscoveredServer
import com.liamgleeson.vibeyfin.testing.FakeMulticastLock
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * A pretend Jellyfin on the loopback interface. [answer] gets each question and its sequence
 * number (1 for the first) and returns the datagrams to send back.
 */
private class FakeJellyfinUdp(private val answer: (question: String, count: Int) -> List<String>) {
    private val selector = SelectorManager(Dispatchers.IO)
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var socket: BoundDatagramSocket

    val questions = mutableListOf<String>()
    val address get() = InetSocketAddress("127.0.0.1", (socket.localAddress as InetSocketAddress).port)

    suspend fun start() {
        socket = aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0))
        scope.launch {
            while (true) {
                val datagram = socket.receive()
                val question = datagram.packet.readText()
                questions += question
                for (reply in answer(question, questions.size)) {
                    socket.send(Datagram(buildPacket { writeText(reply) }, datagram.address))
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
        socket.close()
        selector.close()
    }
}

private fun reply(id: String, name: String = "Home", address: String = "http://192.168.1.20:8096") =
    """{"Address":"$address","Id":"$id","Name":"$name","EndpointAddress":null}"""

class UdpLanDiscoveryTest {
    private val lock = FakeMulticastLock()

    /** Real sockets and real timeouts, so the test runs off the virtual-time scheduler. */
    private fun runUdpTest(
        answer: (String, Int) -> List<String>,
        block: suspend (FakeJellyfinUdp) -> Unit,
    ) = runTest {
        withContext(Dispatchers.Default) {
            val server = FakeJellyfinUdp(answer)
            server.start()
            try {
                block(server)
            } finally {
                server.stop()
            }
        }
    }

    private fun discovery(server: FakeJellyfinUdp, window: Int = 600) = UdpLanDiscovery(
        multicastLock = lock,
        target = server.address,
        window = window.milliseconds,
        resendEvery = 150.milliseconds,
    )

    @Test
    fun findsAServerThatAnswers() = runUdpTest({ _, _ -> listOf(reply("srv-1", "Home")) }) { server ->
        val found = discovery(server).discover().toList()

        assertEquals(listOf(DiscoveredServer("srv-1", "Home", "http://192.168.1.20:8096")), found)
    }

    @Test
    fun asksTheJellyfinQuestion() = runUdpTest({ _, _ -> listOf(reply("srv-1")) }) { server ->
        discovery(server).discover().toList()

        assertEquals("who is JellyfinServer?", server.questions.first())
    }

    @Test
    fun asksAgainSoOneLostQuestionIsNotTheEnd() = runUdpTest(
        // The first two questions "get lost"; only the third is answered.
        { _, count -> if (count >= 3) listOf(reply("srv-1")) else emptyList() },
    ) { server ->
        val found = discovery(server, window = 1_200).discover().toList()

        assertEquals(listOf("srv-1"), found.map { it.id })
        assertTrue(server.questions.size >= 3)
    }

    @Test
    fun aServerHeardSeveralTimesIsReportedOnce() = runUdpTest({ _, _ -> listOf(reply("srv-1"), reply("srv-1")) }) { server ->
        val found = discovery(server, window = 800).discover().toList()

        assertTrue(server.questions.size > 1, "the question should have been repeated")
        assertEquals(listOf("srv-1"), found.map { it.id })
    }

    @Test
    fun differentServersAreAllReported() = runUdpTest(
        { _, _ -> listOf(reply("srv-1", "Home"), reply("srv-2", "Office", "http://192.168.1.30:8096")) },
    ) { server ->
        val found = discovery(server).discover().toList()

        assertEquals(setOf("srv-1", "srv-2"), found.map { it.id }.toSet())
    }

    @Test
    fun answersThatAreNotJellyfinAreIgnored() = runUdpTest(
        { _, _ -> listOf("hello", """{"Name":"no id"}""", "[1,2,3]", reply("srv-1")) },
    ) { server ->
        val found = discovery(server).discover().toList()

        assertEquals(listOf("srv-1"), found.map { it.id })
    }

    @Test
    fun usesWhereTheAnswerCameFromWhenTheServerSaysNoAddress() = runUdpTest(
        { _, _ -> listOf("""{"Id":"srv-1","Name":"Home"}""") },
    ) { server ->
        val found = discovery(server).discover().toList()

        assertEquals("http://127.0.0.1:8096", found.single().address)
    }

    @Test
    fun endsAfterTheWindowWhenNobodyAnswers() = runUdpTest({ _, _ -> emptyList() }) { server ->
        val started = TimeSource.Monotonic.markNow()

        val found = discovery(server, window = 400).discover().toList()

        val elapsed = started.elapsedNow()
        assertEquals(emptyList(), found)
        assertTrue(elapsed >= 350.milliseconds, "ended after only $elapsed")
        assertTrue(elapsed < 3_000.milliseconds, "took $elapsed")
    }

    @Test
    fun theMulticastLockIsHeldOnlyWhileSearching() = runUdpTest({ _, _ -> listOf(reply("srv-1")) }) { server ->
        assertFalse(lock.held)

        discovery(server).discover().toList()

        assertEquals(1, lock.acquired)
        assertFalse(lock.held, "the lock should be released when the search ends")
    }

    @Test
    fun theMulticastLockIsReleasedWhenTheCollectorStopsEarly() = runUdpTest({ _, _ -> listOf(reply("srv-1")) }) { server ->
        discovery(server, window = 5_000).discover().first()

        assertFalse(lock.held, "the lock should be released when the collector stops early")
    }

    @Test
    fun anUnreachableTargetEndsTheSearchEmptyInsteadOfFailing() = runUdpTest({ _, _ -> emptyList() }) { _ ->
        val discovery = UdpLanDiscovery(
            multicastLock = lock,
            target = InetSocketAddress("no-such-host.invalid", 7359),
            window = 300.milliseconds,
        )

        assertEquals(emptyList(), discovery.discover().toList())
        assertFalse(lock.held)
    }
}

class DiscoveryReplyParsingTest {
    private fun parse(text: String, source: String? = "192.168.1.20") = UdpLanDiscovery.parseReply(text, source)

    @Test
    fun readsTheFieldsJellyfinSends() {
        val server = parse(reply("abc123", "Living room", "http://192.168.1.20:8096"))

        assertEquals(DiscoveredServer("abc123", "Living room", "http://192.168.1.20:8096"), server)
    }

    @Test
    fun ignoresFieldsItDoesNotKnow() {
        val server = parse("""{"Address":"http://nas:8096","Id":"abc","Name":"Home","Version":"12.0.0","Extra":{"a":1}}""")

        assertEquals("abc", server?.id)
    }

    @Test
    fun aMissingNameBecomesJellyfin() {
        assertEquals("Jellyfin", parse("""{"Address":"http://nas:8096","Id":"abc"}""")?.name)
        assertEquals("Jellyfin", parse("""{"Address":"http://nas:8096","Id":"abc","Name":" "}""")?.name)
    }

    @Test
    fun anAddressThatIsNotAnAddressFallsBackToTheSourceOnTheDefaultPort() {
        val server = parse("""{"Address":"not an address","Id":"abc","Name":"Home"}""", source = "192.168.1.20")

        assertEquals("http://192.168.1.20:8096", server?.address)
    }

    @Test
    fun withNeitherAnAddressNorASourceThereIsNothingToConnectTo() {
        assertEquals(null, parse("""{"Id":"abc","Name":"Home"}""", source = null))
    }

    @Test
    fun surroundingSpacesInTheAddressAreTrimmed() {
        assertEquals("http://nas:8096", parse("""{"Address":"  http://nas:8096 ","Id":"abc"}""")?.address)
    }

    @Test
    fun anythingThatIsNotAJellyfinAnswerIsNull() {
        for (text in listOf("", "hello", "[]", "{", """{"Id":""}""", """{"Id":"  "}""", """{"Name":"x"}""", "null")) {
            assertEquals(null, parse(text), "'$text' should not parse")
        }
    }
}
