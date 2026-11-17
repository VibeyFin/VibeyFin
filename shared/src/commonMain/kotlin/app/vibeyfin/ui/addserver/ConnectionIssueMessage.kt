package app.vibeyfin.ui.addserver

import app.vibeyfin.domain.ConnectionIssue
import app.vibeyfin.i18n.text

/** What went wrong and what to try, in the words shown under the address field. */
fun ConnectionIssue.message(): String = when (this) {
    ConnectionIssue.InvalidAddress -> text("connection_issue_invalid_address")
    ConnectionIssue.Unreachable -> text("connection_issue_unreachable")
    ConnectionIssue.TlsFailed -> text("connection_issue_tls_failed")
    is ConnectionIssue.ServerUnavailable -> text("connection_issue_server_unavailable", "status" to httpStatus)
    is ConnectionIssue.NotJellyfin -> text("connection_issue_not_jellyfin", "status" to httpStatus)
    is ConnectionIssue.VersionTooOld -> text("connection_issue_version_too_old", "found" to found, "minimum" to minimum)
}
