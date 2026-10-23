package app.vibeyfin.domain.model

/** What a search match is, which decides the group it is listed in and the page it opens. */
enum class SearchKind { Movie, Show, Episode, Person, Collection }

/** One match of a search, ready to draw: a picture, a title and one line under it. */
data class SearchResult(
    val id: String,
    val title: String,
    val kind: SearchKind,
    /** The year, or for an episode its show and number ("Severance · S1 E4"); null when there is nothing to add. */
    val subtitle: String?,
    val imageUrl: String?,
    /** The wide picture, when the match has one: a collection's page opens with it already loading. */
    val backdropUrl: String? = null,
    /** Width over height of [imageUrl]'s picture: tall for a poster or a portrait, wide for an episode's still. */
    val aspectRatio: Float,
)

/**
 * The match as an item, enough for a poster's menu (its title and what it is, the rest is read when the menu needs it); null for a
 * person, who has no trailer, no list and no page of titles to open from a menu.
 */
fun SearchResult.asMediaItem(): MediaItem? {
    val kind = when (kind) {
        SearchKind.Movie -> ItemKind.Movie
        SearchKind.Show -> ItemKind.Series
        SearchKind.Episode -> ItemKind.Episode
        SearchKind.Collection -> ItemKind.Collection
        SearchKind.Person -> return null
    }
    return MediaItem(
        id = id,
        title = title,
        kind = kind,
        isFolder = kind == ItemKind.Series || kind == ItemKind.Collection,
        year = null,
        runtimeMinutes = null,
        genres = emptyList(),
        overview = null,
        rating = null,
        ageRating = null,
        seriesName = null,
        seasonNumber = null,
        episodeNumber = null,
        posterAspectRatio = aspectRatio,
        posterUrl = imageUrl,
        backdropUrl = backdropUrl,
    )
}
