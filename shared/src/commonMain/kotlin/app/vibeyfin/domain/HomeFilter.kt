package app.vibeyfin.domain

import kotlin.time.Duration.Companion.days

/** What Home is limited to; at most one at a time, and none when Home opens. */
sealed interface HomeFilter {
    /** Shows: series, and episodes in Continue watching. */
    data object Shows : HomeFilter

    data object Movies : HomeFilter

    /** Titles added in the last [NEW_WINDOW], newest first. */
    data object New : HomeFilter

    data class Studio(val id: String, val name: String) : HomeFilter

    data class Genre(val id: String, val name: String) : HomeFilter
}

/** One entry of the Studios or Categories sheet. */
data class FilterOption(val id: String, val name: String)

/** How far back "New" reaches, by `DateCreated`. The server has no creation-date filter, so this is applied on the client. */
val NEW_WINDOW = 30.days
