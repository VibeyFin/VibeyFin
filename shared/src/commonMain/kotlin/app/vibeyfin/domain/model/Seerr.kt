package app.vibeyfin.domain.model

import kotlin.jvm.JvmInline
import kotlin.time.Instant

enum class SeerrMediaType { Movie, Show }

/**
 * Where a title stands with Seerr, as its poster pill and request page show it. The order follows a request's life;
 * [Declined] and [Failed] end one that didn't make it.
 */
enum class RequestState {
    /** Nobody has asked for it, or every request was withdrawn. */
    NotRequested,

    /** Asked for, waiting for approval. */
    Requested,

    /** Approved: it arrives in the library when the download finishes. */
    Approved,

    /** Some seasons are in the library (a show only). */
    PartlyAvailable,

    /** In the library. */
    Available,

    /** The last request was declined; it can be asked for again. */
    Declined,

    /** The download failed; Seerr tries again. */
    Failed,
}

/** What a Seerr user may do: Seerr's permission bits. An administrator may do everything. */
@JvmInline
value class SeerrPermissions(val bits: Long) {
    val isAdmin: Boolean get() = has(ADMIN)

    /** Whether the user may pick the quality profile (and server) of a request; Seerr ignores it from anyone else. */
    val canChooseQuality: Boolean get() = isAdmin || has(MANAGE_REQUESTS) || has(REQUEST_ADVANCED)

    /** Whether the user may request [type] in standard quality. */
    fun canRequest(type: SeerrMediaType): Boolean = isAdmin || has(REQUEST) || has(if (type == SeerrMediaType.Movie) REQUEST_MOVIE else REQUEST_TV)

    /** Whether the user may request [type] in 4K; the Seerr must also have a 4K server for it ([SeerrFeatures]). */
    fun canRequest4k(type: SeerrMediaType): Boolean = isAdmin || has(REQUEST_4K) || has(if (type == SeerrMediaType.Movie) REQUEST_4K_MOVIE else REQUEST_4K_TV)

    private fun has(bit: Long) = bits and bit != 0L

    private companion object {
        const val ADMIN = 2L
        const val MANAGE_REQUESTS = 16L
        const val REQUEST = 32L
        const val REQUEST_ADVANCED = 8192L
        const val REQUEST_4K = 1024L
        const val REQUEST_4K_MOVIE = 2048L
        const val REQUEST_4K_TV = 4096L
        const val REQUEST_MOVIE = 262_144L
        const val REQUEST_TV = 524_288L
    }
}

/** The profile's user on Seerr. */
data class SeerrUser(
    val id: Int,
    val name: String,
    val permissions: SeerrPermissions,
)

/** What this Seerr offers beyond plain requests. */
data class SeerrFeatures(
    val movies4k: Boolean = false,
    val shows4k: Boolean = false,
) {
    fun has4k(type: SeerrMediaType): Boolean = if (type == SeerrMediaType.Movie) movies4k else shows4k
}

/** A movie or show as a Seerr poster shows it: in search, a discover row or "More like this". */
data class SeerrTitle(
    /** TMDB's id; with [type] it names the title on Seerr. */
    val tmdbId: Int,
    val type: SeerrMediaType,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val state: RequestState,
    /** The Jellyfin item once the title is in the library: tapping it opens the library's page. */
    val jellyfinItemId: String?,
    /** When it comes out ("2026-12-12"), for Coming soon. */
    val releaseDate: String? = null,
)

/**
 * Whether the title is one to offer for requesting, beside the library's own: not linked to a library item and not (partly)
 * available. "Not in your library" in search and Seerr's titles in "More like this" both use it.
 */
fun SeerrTitle.notInLibrary(): Boolean = jellyfinItemId == null && state != RequestState.Available && state != RequestState.PartlyAvailable

/** One page of [SeerrTitle]s. */
data class SeerrPage(
    val titles: List<SeerrTitle>,
    val page: Int,
    val totalPages: Int,
) {
    val hasMore: Boolean get() = page < totalPages
}

/** Which seasons of a show a request asks for. */
sealed interface SeerrSeasons {
    /** Every season not yet available or requested; Seerr works out which. */
    data object All : SeerrSeasons

    data class Only(val numbers: List<Int>) : SeerrSeasons
}

/** A quality profile on a Radarr or Sonarr server, chosen for a request. */
data class SeerrQuality(val serverId: Int, val profileId: Int)

/** An episode of a show on its Seerr page, from TMDB. */
data class SeerrEpisode(
    val season: Int,
    val number: Int,
    /** The name from TMDB; blank when it has none, and the screen then shows the number. */
    val title: String,
    val overview: String?,
    /** "2011-04-17". */
    val airDate: String?,
    val stillUrl: String?,
    /** TMDB's rating out of 10; null when it has none. */
    val rating: Double?,
)

/** A season of a show on its Seerr page. */
data class SeerrSeason(
    val number: Int,
    val name: String?,
    val episodeCount: Int,
    val year: Int?,
    val state: RequestState,
    val state4k: RequestState,
)

data class SeerrCastMember(
    val id: Int,
    val name: String,
    val character: String?,
    val photoUrl: String?,
)

/** A request for a title. [byMe] says whether the signed-in profile asked for it. */
data class SeerrRequest(
    val id: Int,
    val tmdbId: Int?,
    val type: SeerrMediaType,
    val state: RequestState,
    /** Seerr is still waiting for someone to approve it: the requester may withdraw it. */
    val pendingApproval: Boolean,
    val is4k: Boolean,
    /** The seasons asked for (a show only). */
    val seasons: List<Int>,
    val requestedBy: String?,
    val byMe: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

/** Everything the Seerr page of a title shows. */
data class SeerrTitleDetails(
    val title: SeerrTitle,
    val state4k: RequestState,
    val genres: List<String>,
    val runtimeMinutes: Int?,
    val certification: String?,
    val cast: List<SeerrCastMember>,
    /** A trailer to open in the system's viewer (YouTube), when TMDB links one. */
    val trailerUrl: String?,
    /** Every season, Specials (0) included; empty for a movie. */
    val seasons: List<SeerrSeason>,
    val requests: List<SeerrRequest>,
)

/** One page of the profile's requests. */
data class SeerrRequestPage(
    val requests: List<SeerrRequest>,
    val page: Int,
    val pages: Int,
) {
    val hasMore: Boolean get() = page < pages
}

/** How many requests of one kind are left: [limit] every [days] days. */
data class QuotaLeft(
    val limit: Int,
    val remaining: Int,
    val days: Int?,
)

/** The profile's request quota on Seerr; null for a kind it may request without limit. */
data class RequestQuota(
    val movies: QuotaLeft?,
    val shows: QuotaLeft?,
) {
    fun forType(type: SeerrMediaType): QuotaLeft? = if (type == SeerrMediaType.Movie) movies else shows
}

/** A quality a request can ask for: a Radarr or Sonarr quality profile ("HD-1080p"). */
data class QualityProfile(val id: Int, val name: String?)

/** The qualities offered for one kind and quality tier, on the server Seerr would use, and the one it uses by default. */
data class QualityOptions(
    val serverId: Int,
    val profiles: List<QualityProfile>,
    val defaultProfileId: Int?,
)
