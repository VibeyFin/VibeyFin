package app.vibeyfin.ui.common

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.i18n.text
import app.vibeyfin.ui.whoswatching.SignInMessages

/** What went wrong loading something from the server and what to try, worded once for every platform. */
internal object LoadMessages {
    val FORBIDDEN: String get() = text("load_forbidden")
    val NOT_FOUND: String get() = text("load_not_found")
    val UNEXPECTED: String get() = text("load_unexpected")

    val NO_COMPATIBLE_STREAM: String get() = text("load_no_compatible_stream")
    val PLAYBACK_FAILED: String get() = text("load_playback_failed")
    val ADD_TO_MY_LIST_FAILED: String get() = text("load_add_to_my_list_failed")
    val REMOVE_FROM_MY_LIST_FAILED: String get() = text("load_remove_from_my_list_failed")
    val MARK_WATCHED_FAILED: String get() = text("load_mark_watched_failed")
    val MARK_UNWATCHED_FAILED: String get() = text("load_mark_unwatched_failed")
    val REMOVE_FROM_CONTINUE_FAILED: String get() = text("load_remove_from_continue_failed")
    val RESTORE_TO_CONTINUE_FAILED: String get() = text("load_restore_to_continue_failed")
    val DOWNLOAD_FORBIDDEN: String get() = text("load_download_forbidden")
    val DOWNLOAD_NO_SPACE: String get() = text("load_download_no_space")
    val DOWNLOAD_STORAGE_LIMIT: String get() = text("load_download_storage_limit")
    val DOWNLOAD_INTERRUPTED: String get() = text("load_download_interrupted")
    val DOWNLOAD_SIGN_IN: String get() = text("load_download_sign_in")
    val DOWNLOAD_NOT_ON_SERVER: String get() = text("load_download_not_on_server")
    val DOWNLOAD_UNPLAYABLE: String get() = text("load_download_unplayable")
    val DOWNLOAD_NO_FILE: String get() = text("load_download_no_file")
    val NOT_DOWNLOADED: String get() = text("load_not_downloaded")
    val LANGUAGES_UNREACHABLE: String get() = text("load_languages_unreachable")
    val LANGUAGES_SAVE_FAILED: String get() = text("load_languages_save_failed")
    val SUBTITLES_NOT_ALLOWED_TITLE: String get() = text("load_subtitles_not_allowed_title")
    val SUBTITLES_NOT_ALLOWED_TEXT: String get() = text("load_subtitles_not_allowed_text")
    val SUBTITLES_UNREACHABLE_TITLE: String get() = text("load_subtitles_unreachable_title")
    val SUBTITLES_UNREACHABLE_TEXT: String get() = text("load_subtitles_unreachable_text")
    val SUBTITLES_SEARCH_FAILED_TITLE: String get() = text("load_subtitles_search_failed_title")
    val SUBTITLES_ADD_FAILED: String get() = text("load_subtitles_add_failed")
    val SUBTITLES_DID_NOT_ARRIVE_HAS_ONE: String get() = text("load_subtitles_did_not_arrive_has_one")
    val SUBTITLES_DID_NOT_ARRIVE: String get() = text("load_subtitles_did_not_arrive")
    val SUBTITLE_DELETE_FAILED: String get() = text("load_subtitle_delete_failed")
    val SUBTITLE_DELETE_NOT_ALLOWED: String get() = text("load_subtitle_delete_not_allowed")
    val SUBTITLE_ALREADY_GONE: String get() = text("load_subtitle_already_gone")
    val PLAYBACK_NETWORK: String get() = text("load_playback_network")

    fun forError(error: ApiError): String = when (error) {
        ApiError.Network -> SignInMessages.UNREACHABLE
        ApiError.Unauthorized -> SignInMessages.SESSION_ENDED
        ApiError.Forbidden -> FORBIDDEN
        ApiError.NotFound -> NOT_FOUND
        is ApiError.Server -> text("load_for_error_server", "status" to error.status)
        is ApiError.Unexpected, ApiError.BadResponse -> UNEXPECTED
    }
}
