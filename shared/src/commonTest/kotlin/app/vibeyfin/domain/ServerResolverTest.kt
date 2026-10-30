package app.vibeyfin.domain

import app.vibeyfin.testing.MockJellyfinNetwork
import app.vibeyfin.testing.Route
import app.vibeyfin.testing.ScriptedTimeSource
import app.vibeyfin.testing.httpStatus
import app.vibeyfin.testing.jellyfin
import app.vibeyfin.testing.tlsFailure
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class ServerResolverTest {
    private var network = MockJellyfinNetwork()
    private val requested get() = network.requested
    private val authorization get() = network.authorization

    private fun resolver(
        vararg routes: Pair<String, Route>,
        timeSource: TimeSource = ScriptedTimeSource(),
    ): ServerResolver {
        network = MockJellyfinNetwork(*routes)
        return network.resolver(timeSource)
    }

    private fun status(code: HttpStatusCode, body: String = "") = httpStatus(code, body)

    private suspend fun ServerResolver.found(input: String): ResolvedServer = assertIs<ResolveResult.Found>(resolve(input)).server

    private suspend fun ServerResolver.failure(input: String): ConnectionIssue = assertIs<ResolveResult.Failed>(resolve(input)).issue

    // Candidates for "nas", in order: https://nas, https://nas:8920, http://nas, http://nas:8096

    @Test
    fun prefersHttpsOverAFasterHttpAddress() = runTest {
        val resolver = resolver(
            "https://nas" to jellyfin(),
            "http://nas:8096" to jellyfin(),
            timeSource = ScriptedTimeSource(100, 0, 0, 10),
        )

        val server = resolver.found("nas")

        assertEquals("https://nas", server.address)
        assertEquals(listOf("http://nas:8096"), server.otherAddresses)
    }

    @Test
    fun fallsBackToHttpWhenTheSecureConnectionFails() = runTest {
        val resolver = resolver(
            "https://nas" to tlsFailure(),
            "https://nas:8920" to tlsFailure(),
            "http://nas:8096" to jellyfin(),
        )

        val server = resolver.found("nas")

        assertEquals("http://nas:8096", server.address)
        assertEquals(emptyList(), server.otherAddresses)
    }

    // Candidates for "http://nas": http://nas, http://nas:8096

    @Test
    fun theFasterAddressWinsWhenTheSchemeIsTheSame() = runTest {
        val resolver = resolver(
            "http://nas" to jellyfin(),
            "http://nas:8096" to jellyfin(),
            timeSource = ScriptedTimeSource(80, 20),
        )

        val server = resolver.found("http://nas")

        assertEquals("http://nas:8096", server.address)
        assertEquals(listOf("http://nas"), server.otherAddresses)
    }

    @Test
    fun equalSpeedFallsBackToTheOrderOfTheCandidates() = runTest {
        val resolver = resolver(
            "http://nas" to jellyfin(),
            "http://nas:8096" to jellyfin(),
            timeSource = ScriptedTimeSource(30, 30),
        )

        assertEquals("http://nas", resolver.found("http://nas").address)
    }

    @Test
    fun aDifferentServerAtAnotherAddressIsNotListedAsTheSameServer() = runTest {
        val resolver = resolver(
            "http://nas" to jellyfin(id = "srv-1"),
            "http://nas:8096" to jellyfin(id = "srv-2", name = "Other"),
            timeSource = ScriptedTimeSource(10, 50),
        )

        val server = resolver.found("http://nas")

        assertEquals("srv-1", server.id)
        assertEquals(emptyList(), server.otherAddresses)
    }

    @Test
    fun reportsTheServersIdNameAndVersion() = runTest {
        val resolver = resolver("http://nas:8096" to jellyfin(id = "abc", name = "Living room", version = "10.11.2"))

        val server = resolver.found("http://nas:8096")

        assertEquals(ResolvedServer("abc", "Living room", "10.11.2", "http://nas:8096"), server)
    }

    @Test
    fun aBlankServerNameFallsBackToJellyfin() = runTest {
        val resolver = resolver("http://nas:8096" to jellyfin(name = " "))

        assertEquals("Jellyfin", resolver.found("http://nas:8096").name)
    }

    @Test
    fun everyCandidateIsProbed() = runTest {
        val resolver = resolver("https://nas" to jellyfin())

        resolver.resolve("nas")

        assertEquals(
            setOf(
                "https://nas/System/Info/Public",
                "https://nas:8920/System/Info/Public",
                "http://nas/System/Info/Public",
                "http://nas:8096/System/Info/Public",
            ),
            requested.toSet(),
        )
    }

    @Test
    fun followsARedirectAndReportsWhereTheServerActuallyLives() = runTest {
        val resolver = resolver(
            "http://nas:8096" to {
                respond(
                    content = "",
                    status = HttpStatusCode.MovedPermanently,
                    headers = headersOf(HttpHeaders.Location, "https://jelly.example.com/System/Info/Public"),
                )
            },
            "https://jelly.example.com" to jellyfin(),
        )

        assertEquals("https://jelly.example.com", resolver.found("http://nas:8096").address)
    }

    @Test
    fun aRedirectThatAddsABasePathKeepsThePath() = runTest {
        val resolver = resolver(
            "http://nas:8096" to {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "http://nas:8096/jellyfin/System/Info/Public"),
                )
            },
            "http://nas:8096/jellyfin" to jellyfin(),
        )

        assertEquals("http://nas:8096/jellyfin", resolver.found("http://nas:8096").address)
    }

    @Test
    fun sendsTheMediaBrowserHeaderWithoutATokenWhileProbing() = runTest {
        val resolver = resolver("http://nas:8096" to jellyfin())

        resolver.resolve("http://nas:8096")

        assertEquals(
            "MediaBrowser Client=\"VibeyFin\", Device=\"Test phone\", DeviceId=\"device-1\", Version=\"1.0.0\"",
            authorization.single(),
        )
    }

    // Failures

    @Test
    fun anAddressThatCannotBeParsedFailsWithoutAnyRequest() = runTest {
        val resolver = resolver("http://nas:8096" to jellyfin())

        assertEquals(ConnectionIssue.InvalidAddress, resolver.failure("ftp://nas"))
        assertEquals(ConnectionIssue.InvalidAddress, resolver.failure("  "))
        assertTrue(requested.isEmpty())
    }

    @Test
    fun reportsUnreachableWhenNothingAnswers() = runTest {
        val resolver = resolver()

        assertEquals(ConnectionIssue.Unreachable, resolver.failure("nas"))
    }

    @Test
    fun reportsATlsProblemWhenOnlyTheSecureConnectionFailed() = runTest {
        val resolver = resolver("https://nas" to tlsFailure())

        assertEquals(ConnectionIssue.TlsFailed, resolver.failure("nas"))
    }

    @Test
    fun reportsNotJellyfinForAnotherProduct() = runTest {
        val resolver = resolver("http://nas:8096" to jellyfin(product = "Emby Server"))

        assertEquals(ConnectionIssue.NotJellyfin(200), resolver.failure("http://nas:8096"))
    }

    @Test
    fun reportsNotJellyfinForAWebPageOrAnUnknownPath() = runTest {
        val page = resolver("http://nas:8096" to status(HttpStatusCode.OK, "<html>Welcome to nginx</html>"))
        val missing = resolver("http://nas:8096" to status(HttpStatusCode.NotFound))
        val denied = resolver("http://nas:8096" to status(HttpStatusCode.Unauthorized))

        assertEquals(ConnectionIssue.NotJellyfin(200), page.failure("http://nas:8096"))
        assertEquals(ConnectionIssue.NotJellyfin(404), missing.failure("http://nas:8096"))
        assertEquals(ConnectionIssue.NotJellyfin(401), denied.failure("http://nas:8096"))
    }

    @Test
    fun reportsNotJellyfinWhenTheAnswerLacksAnIdOrAReadableVersion() = runTest {
        val noId = resolver("http://nas:8096" to jellyfin(id = ""))
        val badVersion = resolver("http://nas:8096" to jellyfin(version = "banana"))

        assertEquals(ConnectionIssue.NotJellyfin(200), noId.failure("http://nas:8096"))
        assertEquals(ConnectionIssue.NotJellyfin(200), badVersion.failure("http://nas:8096"))
    }

    @Test
    fun reportsAProxyErrorAsTheServerBeingUnavailable() = runTest {
        val resolver = resolver("http://nas:8096" to status(HttpStatusCode.BadGateway))

        assertEquals(ConnectionIssue.ServerUnavailable(502), resolver.failure("http://nas:8096"))
    }

    @Test
    fun reportsAVersionBelowTheMinimum() = runTest {
        val resolver = resolver("http://nas:8096" to jellyfin(version = "10.9.11"))

        assertEquals(ConnectionIssue.VersionTooOld("10.9.11", "10.10.0"), resolver.failure("http://nas:8096"))
    }

    @Test
    fun theMinimumVersionItselfIsAccepted() = runTest {
        val resolver = resolver("http://nas:8096" to jellyfin(version = "10.10.0"))

        assertEquals("10.10.0", resolver.found("http://nas:8096").version)
    }

    @Test
    fun aWorkingAddressWinsOverOneThatFailed() = runTest {
        val resolver = resolver(
            "https://nas" to jellyfin(version = "10.8.13"),
            "https://nas:8920" to status(HttpStatusCode.NotFound),
            "http://nas" to tlsFailure(),
            "http://nas:8096" to jellyfin(version = "10.11.0"),
        )

        assertEquals("http://nas:8096", resolver.found("nas").address)
    }

    @Test
    fun theMostUsefulFailureIsReported() = runTest {
        // https: TLS fails. http: something answers but isn't Jellyfin. That beats TLS and timeouts.
        val notJellyfin = resolver(
            "https://nas" to tlsFailure(),
            "http://nas" to status(HttpStatusCode.NotFound),
        )
        assertEquals(ConnectionIssue.NotJellyfin(404), notJellyfin.failure("nas"))

        val tooOld = resolver(
            "https://nas" to status(HttpStatusCode.NotFound),
            "http://nas:8096" to jellyfin(version = "10.8.0"),
        )
        assertEquals(ConnectionIssue.VersionTooOld("10.8.0", "10.10.0"), tooOld.failure("nas"))

        val unavailable = resolver(
            "https://nas" to tlsFailure(),
            "http://nas" to status(HttpStatusCode.ServiceUnavailable),
        )
        assertEquals(ConnectionIssue.ServerUnavailable(503), unavailable.failure("nas"))

        val tls = resolver("https://nas" to tlsFailure())
        assertFalse(tls.failure("nas") == ConnectionIssue.Unreachable)
    }
}
