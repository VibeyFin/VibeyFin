package app.vibeyfin.ui.whoswatching

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.domain.SignInResult
import app.vibeyfin.i18n.text

/** What went wrong signing in and what to try, worded once for every platform. */
internal object SignInMessages {
    val WRONG_PASSWORD: String get() = text("sign_in_wrong_password")
    val WRONG_CREDENTIALS: String get() = text("sign_in_wrong_credentials")
    val SESSION_ENDED: String get() = text("sign_in_session_ended")
    val UNREACHABLE: String get() = text("sign_in_unreachable")

    val QUICK_CONNECT_EXPIRED: String get() = text("sign_in_quick_connect_expired")
    val QUICK_CONNECT_OFF: String get() = text("sign_in_quick_connect_off")
    val QUICK_CONNECT_FAILED: String get() = text("sign_in_quick_connect_failed")
    val QUICK_CONNECT_CODE_UNKNOWN: String get() = text("sign_in_quick_connect_code_unknown")
    val QUICK_CONNECT_APPROVE_OFF: String get() = text("sign_in_quick_connect_approve_off")
    val QUICK_CONNECT_APPROVE_FAILED: String get() = text("sign_in_quick_connect_approve_failed")

    fun forQuickConnect(stop: QuickConnectStop): String = when (stop) {
        QuickConnectStop.Expired -> QUICK_CONNECT_EXPIRED
        QuickConnectStop.Off -> QUICK_CONNECT_OFF
        QuickConnectStop.Unreachable -> UNREACHABLE
        QuickConnectStop.Failed -> QUICK_CONNECT_FAILED
    }

    fun forFailure(result: SignInResult, passwordOnly: Boolean): String = when (result) {
        SignInResult.InvalidCredentials -> if (passwordOnly) WRONG_PASSWORD else WRONG_CREDENTIALS

        SignInResult.ServerUnreachable -> UNREACHABLE

        is SignInResult.Failed -> when (val error = result.error) {
            is ApiError.Server -> text("sign_in_for_failure_server", "status" to error.status)
            ApiError.Network -> UNREACHABLE
            else -> text("sign_in_for_failure_generic")
        }

        SignInResult.SignedIn -> ""
    }
}
