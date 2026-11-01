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
    const val ALREADY_REQUESTED = "Someone already asked for this."
    const val NOT_ALLOWED = "Your Seerr account can’t request this. Ask whoever runs Seerr."
    const val REQUEST_UNREACHABLE = "Can’t reach Seerr. Check your connection, then try again."
    const val REQUEST_FAILED = "Seerr couldn’t take the request. Try again."
    const val CANCEL_FAILED = "Couldn’t cancel the request. Try again."
    const val TITLE_NOT_FOUND = "Seerr doesn’t know this title."

    /** The request limit is used up: how many this profile may ask for, and how often, as Seerr counts them. */
    fun quotaReached(limit: Int?, days: Int?, movies: Boolean): String {
        if (limit == null) return "Your request limit is reached. Try again later."
        val what = if (movies) (if (limit == 1) "1 movie" else "$limit movies") else (if (limit == 1) "1 season" else "$limit seasons")
        val often = when (days) {
            null -> ""
            1 -> " a day"
            7 -> " a week"
            else -> " every $days days"
        }
        return "Your request limit is reached. You can request $what$often."
    }

    /** A show's request asked for more seasons than the limit has left. */
    fun tooManySeasons(left: Int) = "You can request ${if (left == 1) "1 more season" else "$left more seasons"} for now. Pick fewer seasons."

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
