package app.vibeyfin.ui.search

import app.vibeyfin.domain.model.SeerrTitle

/** The "Not in your library" group: what Seerr found for the search. */
sealed interface SeerrSearch {
    /** Seerr hasn't answered yet: the group shows its heading over a few grey tiles. */
    data object Searching : SeerrSearch

    /** Movies and shows that aren't in the library; Seerr's own people and library titles are left out. */
    data class Found(val titles: List<SeerrTitle>) : SeerrSearch

    /** Seerr didn't answer: the group says so and offers to try again. The library's groups stay. */
    data class Failed(val message: String) : SeerrSearch
}
