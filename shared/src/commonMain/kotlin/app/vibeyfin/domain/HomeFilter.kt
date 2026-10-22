package app.vibeyfin.domain

/** What Home is limited to; at most one at a time, and none when Home opens. */
sealed interface HomeFilter {
    /** Shows: series, and episodes in Continue watching. */
    data object Shows : HomeFilter

    data object Movies : HomeFilter

    /** Movies and shows by release year, newest first. */
    data object New : HomeFilter

    data class Genre(val id: String, val name: String) : HomeFilter
}

/** One entry of the Categories dropdown. */
data class FilterOption(val id: String, val name: String)
