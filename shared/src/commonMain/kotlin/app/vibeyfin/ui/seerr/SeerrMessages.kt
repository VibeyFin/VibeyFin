package app.vibeyfin.ui.seerr

import app.vibeyfin.domain.SeerrConnectResult
import app.vibeyfin.domain.SeerrPasswordResult
import app.vibeyfin.domain.SeerrProblem

/** What went wrong with Seerr and what to do about it, worded once for every platform. */
internal object SeerrMessages {
    const val INVALID_ADDRESS = "Enter Seerr’s address, such as requests.example.org."
    const val UNREACHABLE_ADDRESS = "Can’t reach Seerr at this address. Check it and try again."
    const val NOT_SEERR = "This isn’t a Seerr address. Check it and try again."
    const val WRONG_SERVER = "This Seerr is linked to a different Jellyfin server."
    const val NO_ACCESS = "Seerr didn’t let this Jellyfin user in. Ask whoever runs Seerr to allow it."
    const val FAILED = "Seerr couldn’t sign you in. Try again."
    const val UNREACHABLE_NOW = "Can’t reach Seerr right now."
    const val QUICK_CONNECT_OFF = "Quick Connect is off on your Jellyfin server."
    const val WRONG_PASSWORD = "That password didn’t work. Try again."
    const val PASSWORD_UNREACHABLE = "Can’t reach Seerr. Check your connection and try again."

    /** Why connecting failed; null when it didn't. */
    fun forConnect(result: SeerrConnectResult): String? = when (result) {
        SeerrConnectResult.Connected, SeerrConnectResult.NeedsPassword -> null
        SeerrConnectResult.Unreachable -> UNREACHABLE_ADDRESS
        SeerrConnectResult.NotSeerr -> NOT_SEERR
        SeerrConnectResult.WrongServer -> WRONG_SERVER
        SeerrConnectResult.NoAccess -> NO_ACCESS
        SeerrConnectResult.Failed -> FAILED
    }

    /** Why the profile isn't signed in. */
    fun forProblem(problem: SeerrProblem): String = when (problem) {
        SeerrProblem.Unreachable -> UNREACHABLE_NOW
        SeerrProblem.WrongServer -> WRONG_SERVER
        SeerrProblem.NoAccess -> NO_ACCESS
        SeerrProblem.Failed -> FAILED
    }

    /** Why the password sign-in failed; null when it didn't. */
    fun forPassword(result: SeerrPasswordResult): String? = when (result) {
        SeerrPasswordResult.SignedIn -> null
        SeerrPasswordResult.WrongPassword -> WRONG_PASSWORD
        SeerrPasswordResult.Unreachable -> PASSWORD_UNREACHABLE
        SeerrPasswordResult.WrongServer -> WRONG_SERVER
        SeerrPasswordResult.Failed -> FAILED
    }
}
