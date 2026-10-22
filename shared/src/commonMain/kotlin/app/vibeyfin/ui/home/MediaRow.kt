package app.vibeyfin.ui.home

import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.domain.model.MediaItem

/** What a row's "See all" opens. */
sealed interface SeeAll {
    /** A library's own list. */
    data class Library(val library: MediaItem) : SeeAll

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
