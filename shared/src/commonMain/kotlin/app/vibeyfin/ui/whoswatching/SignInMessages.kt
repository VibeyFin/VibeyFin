package app.vibeyfin.ui.whoswatching

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.domain.QuickConnectState
import app.vibeyfin.domain.SignInResult

/** What went wrong signing in and what to try, worded once for every platform. */
internal object SignInMessages {
    const val WRONG_PASSWORD = "That password didn't work. Check it and try again."
    const val WRONG_CREDENTIALS = "That username or password didn't work. Check them and try again."
    const val SESSION_ENDED = "Your session ended. Sign in again."
    const val UNREACHABLE =
        "Couldn't reach the server. Check that it's on and that this device can reach its network."

    const val QUICK_CONNECT_TIMED_OUT = "That code expired before it was approved. Try again."
    const val QUICK_CONNECT_UNAVAILABLE =
        "This server doesn't allow Quick Connect. Sign in with a password instead."
    const val QUICK_CONNECT_FAILED = "Quick Connect didn't work. Try again."

    fun forQuickConnect(state: QuickConnectState): String? = when (state) {
        QuickConnectState.TimedOut -> QUICK_CONNECT_TIMED_OUT
        QuickConnectState.Unavailable -> QUICK_CONNECT_UNAVAILABLE
        QuickConnectState.Unreachable -> UNREACHABLE
        is QuickConnectState.Failed -> QUICK_CONNECT_FAILED
        QuickConnectState.Starting, is QuickConnectState.Waiting, QuickConnectState.SignedIn -> null
    }

    fun forFailure(result: SignInResult, passwordOnly: Boolean): String = when (result) {
        SignInResult.InvalidCredentials -> if (passwordOnly) WRONG_PASSWORD else WRONG_CREDENTIALS

        SignInResult.ServerUnreachable -> UNREACHABLE

        is SignInResult.Failed -> when (val error = result.error) {
            is ApiError.Server -> "The server answered with an error (HTTP ${error.status}). Try again in a minute."
            ApiError.Network -> UNREACHABLE
            else -> "Something went wrong signing in. Try again."
        }

        SignInResult.SignedIn -> ""
    }
}
