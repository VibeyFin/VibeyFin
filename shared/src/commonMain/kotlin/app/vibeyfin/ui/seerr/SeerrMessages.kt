package app.vibeyfin.ui.seerr

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.domain.SeerrConnectResult
import app.vibeyfin.domain.SeerrPasswordResult
import app.vibeyfin.domain.SeerrProblem
import app.vibeyfin.i18n.plural
import app.vibeyfin.i18n.text
import app.vibeyfin.ui.common.Strings

/** What went wrong with Seerr and what to do about it, worded once for every platform. */
internal object SeerrMessages {
    val INVALID_ADDRESS: String get() = text("seerr_message_invalid_address")
    val UNREACHABLE_ADDRESS: String get() = text("seerr_message_unreachable_address")
    val NOT_SEERR: String get() = text("seerr_message_not_seerr")
    val WRONG_SERVER: String get() = text("seerr_message_wrong_server")
    val NO_ACCESS: String get() = text("seerr_message_no_access")
    val FAILED: String get() = text("seerr_message_failed")
    val UNREACHABLE_NOW: String get() = text("seerr_message_unreachable_now")
    val QUICK_CONNECT_OFF: String get() = text("seerr_message_quick_connect_off")
    val WRONG_PASSWORD: String get() = text("seerr_message_wrong_password")
    val PASSWORD_UNREACHABLE: String get() = text("seerr_message_password_unreachable")
    val ALREADY_REQUESTED: String get() = text("seerr_message_already_requested")
    val NOT_ALLOWED: String get() = text("seerr_message_not_allowed")
    val REQUEST_UNREACHABLE: String get() = text("seerr_message_request_unreachable")
    val REQUEST_FAILED: String get() = text("seerr_message_request_failed")
    val CANCEL_FAILED: String get() = text("seerr_message_cancel_failed")
    val TITLE_NOT_FOUND: String get() = text("seerr_message_title_not_found")
    val SIGNED_OUT: String get() = text("seerr_message_signed_out")
    val FORBIDDEN: String get() = text("seerr_message_forbidden")
    val UNEXPECTED: String get() = text("seerr_message_unexpected")

    /** Why a read from Seerr failed and what to try, as [app.vibeyfin.ui.common.LoadMessages.forError] does for Jellyfin. */
    fun forError(error: ApiError): String = when (error) {
        ApiError.Network -> UNREACHABLE_NOW
        ApiError.Unauthorized -> SIGNED_OUT
        ApiError.Forbidden -> FORBIDDEN
        ApiError.NotFound -> TITLE_NOT_FOUND
        is ApiError.Server -> text("seerr_message_for_error_server", "status" to error.status)
        is ApiError.Unexpected, ApiError.BadResponse -> UNEXPECTED
    }

    /** The request limit is used up: how many this profile may ask for, and how often, as Seerr counts them. */
    fun quotaReached(limit: Int?, days: Int?, movies: Boolean): String = when {
        limit == null -> text("seerr_message_quota_reached_try_later")
        movies -> quotaReachedMovies(Strings.collectionMovieCount(limit), days)
        else -> quotaReachedSeasons(Strings.seasonCount(limit), days)
    }

    private fun quotaReachedMovies(movies: String, days: Int?): String = when (days) {
        null -> text("seerr_message_quota_reached_movies", "movies" to movies)
        1 -> text("seerr_message_quota_reached_movies_a_day", "movies" to movies)
        7 -> text("seerr_message_quota_reached_movies_a_week", "movies" to movies)
        else -> plural("seerr_message_quota_reached_movies_every_days", days, "movies" to movies)
    }

    private fun quotaReachedSeasons(seasons: String, days: Int?): String = when (days) {
        null -> text("seerr_message_quota_reached_seasons", "seasons" to seasons)
        1 -> text("seerr_message_quota_reached_seasons_a_day", "seasons" to seasons)
        7 -> text("seerr_message_quota_reached_seasons_a_week", "seasons" to seasons)
        else -> plural("seerr_message_quota_reached_seasons_every_days", days, "seasons" to seasons)
    }

    /** A show's request asked for more seasons than the limit has left. */
    fun tooManySeasons(left: Int): String = plural("seerr_message_too_many_seasons", left)

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
