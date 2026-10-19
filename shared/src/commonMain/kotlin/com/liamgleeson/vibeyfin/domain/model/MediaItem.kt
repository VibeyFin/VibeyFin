package com.liamgleeson.vibeyfin.domain.model

enum class ItemKind {
    /** A library: Movies, Shows, Music. */
    Library,

    /** A plain folder inside a library. */
    Folder,
    Movie,
    Series,
    Season,
    Episode,

    /** Anything else; opens like a folder if the server says it holds items. */
    Other,
}

/** A library, folder, series, season, movie or episode, as the screens show it. */
data class MediaItem(
    val id: String,
    val title: String,
    val kind: ItemKind,
    /** Holds other items, so it opens a list. Anything else opens its detail page. */
    val isFolder: Boolean,
    val year: Int?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val overview: String?,
    /** Out of 10, from the server's community rating. */
    val rating: Double?,
    /** An age rating such as PG-13. */
    val ageRating: String?,
    /** For an episode: the show's name. */
    val seriesName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    /** Width over height of [posterUrl]; posters are tall, episode stills and libraries are wide. */
    val posterAspectRatio: Float,
    val posterUrl: String?,
    val backdropUrl: String?,
) {
    /**
     * The server's picture for a library has its name written on it, so a caption under it would
     * say the name twice. Screens leave the caption off and describe the picture with the title.
     */
    val nameIsOnPoster: Boolean get() = kind == ItemKind.Library && posterUrl != null
}

/** One page of a list that can be longer than we ask for at once. */
data class ItemPage(
    val items: List<MediaItem>,
    /** How many items the list holds in all, across every page. */
    val total: Int,
    val startIndex: Int,
) {
    val hasMore: Boolean get() = items.isNotEmpty() && startIndex + items.size < total
}
