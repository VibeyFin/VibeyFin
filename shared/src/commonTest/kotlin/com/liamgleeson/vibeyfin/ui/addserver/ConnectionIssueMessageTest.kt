package com.liamgleeson.vibeyfin.ui.addserver

import com.liamgleeson.vibeyfin.domain.ConnectionIssue
import kotlin.test.Test
import kotlin.test.assertTrue

class ConnectionIssueMessageTest {
    private fun assertMentions(issue: ConnectionIssue, vararg expected: String) {
        val message = issue.message()
        for (text in expected) assertTrue(message.contains(text), "'$message' should mention '$text'")
    }

    @Test
    fun anInvalidAddressShowsWhatAGoodOneLooksLike() {
        assertMentions(ConnectionIssue.InvalidAddress, "192.168.1.20:8096", "https://jellyfin.example.com")
    }

    @Test
    fun unreachableSaysWhatToCheck() {
        assertMentions(ConnectionIssue.Unreachable, "address and port", "server is on")
    }

    @Test
    fun aTlsFailureSuggestsHttpOnTheHomeNetwork() {
        assertMentions(ConnectionIssue.TlsFailed, "certificate", "http://", "8096")
    }

    @Test
    fun aServerErrorShowsTheStatus() {
        assertMentions(ConnectionIssue.ServerUnavailable(502), "502", "Try again")
    }

    @Test
    fun notJellyfinShowsTheStatusAndThePortToTry() {
        assertMentions(ConnectionIssue.NotJellyfin(404), "isn't a Jellyfin server", "404", "8096")
    }

    @Test
    fun anOldServerShowsBothVersions() {
        assertMentions(ConnectionIssue.VersionTooOld("10.9.11", "10.10.0"), "10.9.11", "10.10.0", "Update the server")
    }

    @Test
    fun everyMessageIsAFullSentenceWithoutCodeNames() {
        val issues = listOf(
            ConnectionIssue.InvalidAddress,
            ConnectionIssue.Unreachable,
            ConnectionIssue.TlsFailed,
            ConnectionIssue.ServerUnavailable(503),
            ConnectionIssue.NotJellyfin(200),
            ConnectionIssue.VersionTooOld("10.8.0", "10.10.0"),
        )

        for (issue in issues) {
            val message = issue.message()
            assertTrue(message.endsWith("."), "'$message' should end with a full stop")
            assertTrue(message.first().isUpperCase(), "'$message' should start with a capital")
            assertTrue("ConnectionIssue" !in message && "Exception" !in message, "'$message' leaks a code name")
        }
    }
}
