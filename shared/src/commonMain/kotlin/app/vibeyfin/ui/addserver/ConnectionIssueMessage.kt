package app.vibeyfin.ui.addserver

import app.vibeyfin.domain.ConnectionIssue

/** What went wrong and what to try, in the words shown under the address field. */
fun ConnectionIssue.message(): String = when (this) {
    ConnectionIssue.InvalidAddress ->
        "That doesn't look like a server address. Try an IP address like 192.168.1.20:8096, " +
            "or a web address like https://jellyfin.example.com."

    ConnectionIssue.Unreachable ->
        "Couldn't reach a server at that address. Check the address and port, make sure the " +
            "server is on, and that this device can reach its network."

    ConnectionIssue.TlsFailed ->
        "The secure connection failed, which usually means a self-signed or expired certificate. " +
            "If the server is on your home network, try http:// with port 8096. Otherwise, fix " +
            "the server's certificate."

    is ConnectionIssue.ServerUnavailable ->
        "The server answered with an error (HTTP $httpStatus). It may still be starting up, or " +
            "the proxy in front of it can't reach Jellyfin. Try again in a minute."

    is ConnectionIssue.NotJellyfin ->
        "Something answered at that address, but it isn't a Jellyfin server (HTTP $httpStatus). " +
            "Check the port: Jellyfin usually listens on 8096."

    is ConnectionIssue.VersionTooOld ->
        "This server runs Jellyfin $found. VibeyFin needs $minimum or newer. Update the server, " +
            "then try again."
}
