package app.vibeyfin.ui.home

import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.domain.model.MediaItem

/** What a row's "See all" opens. */
sealed interface SeeAll {
    /** A library's own list. */
    data class Library(val library: MediaItem) : SeeAll

    /** The user's My List page. */
    data object MyList : SeeAll

    /** A genre's category page. */
    data class Genre(val filter: HomeFilter.Genre) : SeeAll
}

/**
 * One row of posters on Home: "Recently added in Movies", a genre. Each loads on its own: [loading] shows grey posters,
 * [message] says why it failed (with Try again) and the other rows are untouched, and a row that came back empty is not
 * [isVisible] at all, so Home never shows "No items".
 */
data class MediaRow(
    val id: String,
    val title: String,
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
    /** Opens the whole list; null when the row has no such list. */
    val seeAll: SeeAll? = null,
) {
    val isVisible: Boolean get() = loading || message != null || items.isNotEmpty()
}

/**
 * Lets the rows appear from top to bottom, however they come back: the rows load at the same time, but one is shown as
 * grey posters until every row above it has finished ([aboveLoading] says whether Continue watching, above all of them,
 * has not). A row that is empty once reached then leaves, instead of everything below it jumping up while rows above are
 * still loading. Empty rows are only dropped after the order is applied.
 */
internal fun revealInOrder(rows: List<MediaRow>, aboveLoading: Boolean): List<MediaRow> {
    var blocked = aboveLoading
    return rows.map { row ->
        val shown = if (blocked && !row.loading) row.copy(items = emptyList(), message = null, loading = true) else row
        if (row.loading) blocked = true
        shown
    }
}
