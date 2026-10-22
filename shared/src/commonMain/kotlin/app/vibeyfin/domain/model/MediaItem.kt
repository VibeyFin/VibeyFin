package app.vibeyfin.domain.model

import app.vibeyfin.domain.VideoFormat

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
    /** For an episode or a season: the show it belongs to. */
    val seriesId: String? = null,
    val runtimeMs: Long? = null,
    /** Where the server says the user stopped; 0 when they haven't started it, or have finished it. */
    val resumePositionMs: Long = 0,
    /** The server's own verdict on whether the user has watched it; it decides this from its resume thresholds. */
    val played: Boolean = false,
    /** A 16:9 picture for a card: the item's Thumb, else its Backdrop, else its Primary image. */
    val landscapeUrl: String? = null,
    /** The BlurHash of [posterUrl]'s picture, drawn in its place while it loads. */
    val posterBlurHash: String? = null,
    /** The BlurHash of [backdropUrl]'s picture. */
    val backdropBlurHash: String? = null,
    /** The BlurHash of [landscapeUrl]'s picture, whichever image type that turned out to be. */
    val landscapeBlurHash: String? = null,
    /** The file's video picture; null when the server wasn't asked for streams or the item has no video. */
    val videoFormat: VideoFormat? = null,
    /** On a library: what it holds (`movies`, `tvshows`, `music`, ...); null for mixed content or anything that is not a library. */
    val collectionType: String? = null,
) {
    /** How far in the user is, from 0 to 1; null when they haven't started or the length isn't known. */
    val progress: Float?
        get() = runtimeMs?.takeIf { it > 0 && resumePositionMs > 0 }
            ?.let { (resumePositionMs.toFloat() / it).coerceIn(0f, 1f) }

    /**
     * The server's picture for a library has its name written on it, so a caption under it would
     * say the name twice. Screens leave the caption off and describe the picture with the title.
     */
    val nameIsOnPoster: Boolean get() = kind == ItemKind.Library && posterUrl != null

    /** A library of films or shows (or a mix), which Home gives a "Recently added" row; music, books, photos and live TV do not. */
    val holdsVideo: Boolean
        get() = kind == ItemKind.Library && (collectionType == null || collectionType in VIDEO_COLLECTIONS)

    /** Movies and episodes play. Other kinds, such as music or extras, are not offered yet. */
    val canPlay: Boolean get() = kind == ItemKind.Movie || kind == ItemKind.Episode
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

private val VIDEO_COLLECTIONS = setOf("movies", "tvshows", "homevideos", "musicvideos", "boxsets")
