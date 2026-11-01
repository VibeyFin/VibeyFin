package app.vibeyfin.domain

import app.vibeyfin.data.seerr.SeerrCastDto
import app.vibeyfin.data.seerr.SeerrMediaInfoDto
import app.vibeyfin.data.seerr.SeerrMovieDto
import app.vibeyfin.data.seerr.SeerrQuotaDto
import app.vibeyfin.data.seerr.SeerrQuotaPartDto
import app.vibeyfin.data.seerr.SeerrRequestDto
import app.vibeyfin.data.seerr.SeerrResultDto
import app.vibeyfin.data.seerr.SeerrTvDto
import app.vibeyfin.data.seerr.SeerrUserDto
import app.vibeyfin.data.seerr.SeerrVideoDto
import app.vibeyfin.domain.model.QuotaLeft
import app.vibeyfin.domain.model.RequestQuota
import app.vibeyfin.domain.model.RequestState
import app.vibeyfin.domain.model.SeerrCastMember
import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.domain.model.SeerrPermissions
import app.vibeyfin.domain.model.SeerrRequest
import app.vibeyfin.domain.model.SeerrSeason
import app.vibeyfin.domain.model.SeerrTitle
import app.vibeyfin.domain.model.SeerrTitleDetails
import app.vibeyfin.domain.model.SeerrUser
import kotlin.time.Instant

// Turns Seerr's DTOs into the models the app uses. Artwork comes from TMDB's image server, as Seerr's own web app loads it.

private const val TMDB_IMAGES = "https://image.tmdb.org/t/p"
private const val POSTER_SIZE = "w342"
private const val BACKDROP_SIZE = "w1280"
private const val PROFILE_SIZE = "w185"
private const val CERTIFICATION_COUNTRY = "US"
private const val YEAR_LENGTH = 4

// Seerr's MediaStatus.
private const val MEDIA_PENDING = 2
private const val MEDIA_PROCESSING = 3
private const val MEDIA_PARTLY_AVAILABLE = 4
private const val MEDIA_AVAILABLE = 5

// Seerr's MediaRequestStatus.
private const val REQUEST_PENDING = 1
private const val REQUEST_APPROVED = 2
private const val REQUEST_DECLINED = 3
private const val REQUEST_FAILED = 4
private const val REQUEST_COMPLETED = 5

internal fun tmdbImage(path: String?, size: String): String? = path?.takeIf { it.isNotBlank() }?.let { "$TMDB_IMAGES/$size$it" }

private fun yearOf(date: String?): Int? = date?.take(YEAR_LENGTH)?.toIntOrNull()

private fun instantOf(date: String?): Instant? = date?.let { runCatching { Instant.parse(it) }.getOrNull() }

internal fun mediaTypeOf(value: String?): SeerrMediaType? = when (value) {
    "movie" -> SeerrMediaType.Movie
    "tv" -> SeerrMediaType.Show
    else -> null
}

internal fun SeerrMediaType.wireName(): String = if (this == SeerrMediaType.Movie) "movie" else "tv"

/**
 * Where a title stands, from Seerr's [status] for it and its [requests] (in the quality [is4k] names). A declined or failed
 * request leaves the title's own status at "unknown", so those two come from the latest request.
 */
internal fun requestState(status: Int, requests: List<SeerrRequestDto>, is4k: Boolean = false): RequestState {
    val latest = requests.filter { it.is4k == is4k }.maxByOrNull { it.id }
    return when (status) {
        MEDIA_AVAILABLE -> RequestState.Available

        MEDIA_PARTLY_AVAILABLE -> RequestState.PartlyAvailable

        MEDIA_PROCESSING -> if (latest?.status == REQUEST_FAILED) RequestState.Failed else RequestState.Approved

        MEDIA_PENDING -> RequestState.Requested

        // Seerr hasn't marked the title (or season) yet: an approved request it hasn't passed on still counts as asked for
        else -> when (latest?.status) {
            REQUEST_PENDING -> RequestState.Requested
            REQUEST_APPROVED -> RequestState.Approved
            REQUEST_DECLINED -> RequestState.Declined
            REQUEST_FAILED -> RequestState.Failed
            else -> RequestState.NotRequested
        }
    }
}

// The Jellyfin item, only while Seerr says the title is (partly) in the library: Seerr keeps the id on media it later marks
// deleted or unknown, and that item no longer opens.
private fun SeerrMediaInfoDto?.libraryItemId(): String? = this?.jellyfinMediaId?.takeIf { status == MEDIA_AVAILABLE || status == MEDIA_PARTLY_AVAILABLE }

private fun SeerrMediaInfoDto?.state(is4k: Boolean = false): RequestState = this?.let { requestState(if (is4k) it.status4k else it.status, it.requests, is4k) }
    ?: RequestState.NotRequested

/** A movie or show from a list; null for a person or anything else that can't be requested. */
internal fun SeerrResultDto.toTitle(): SeerrTitle? {
    val type = mediaTypeOf(mediaType) ?: return null
    val name = (if (type == SeerrMediaType.Movie) title else name) ?: title ?: name ?: return null
    val date = if (type == SeerrMediaType.Movie) releaseDate else firstAirDate
    return SeerrTitle(
        tmdbId = id,
        type = type,
        title = name,
        year = yearOf(date),
        posterUrl = tmdbImage(posterPath, POSTER_SIZE),
        backdropUrl = tmdbImage(backdropPath, BACKDROP_SIZE),
        overview = overview?.takeIf { it.isNotBlank() },
        state = mediaInfo.state(),
        jellyfinItemId = mediaInfo.libraryItemId(),
        releaseDate = date?.takeIf { it.isNotBlank() },
    )
}

internal fun SeerrUserDto.toUser(): SeerrUser = SeerrUser(
    id = id,
    name = displayName ?: jellyfinUsername ?: "",
    permissions = SeerrPermissions(permissions),
)

/** A request; [type] is the title's when Seerr leaves the request's own out. */
internal fun SeerrRequestDto.toRequest(meId: Int?, type: SeerrMediaType? = null): SeerrRequest? {
    val kind = mediaTypeOf(this.type) ?: mediaTypeOf(media?.mediaType) ?: type ?: return null
    val mediaStatus = media?.let { if (is4k) it.status4k else it.status }
    val state = when (status) {
        REQUEST_PENDING -> RequestState.Requested

        REQUEST_DECLINED -> RequestState.Declined

        REQUEST_FAILED -> RequestState.Failed

        REQUEST_APPROVED, REQUEST_COMPLETED -> when (mediaStatus) {
            MEDIA_AVAILABLE -> RequestState.Available
            MEDIA_PARTLY_AVAILABLE -> RequestState.PartlyAvailable
            else -> if (status == REQUEST_COMPLETED) RequestState.Available else RequestState.Approved
        }

        else -> RequestState.NotRequested
    }
    return SeerrRequest(
        id = id,
        tmdbId = media?.tmdbId,
        type = kind,
        state = state,
        pendingApproval = status == REQUEST_PENDING,
        is4k = is4k,
        seasons = seasons.map { it.seasonNumber }.sorted(),
        requestedBy = requestedBy?.let { it.displayName ?: it.jellyfinUsername },
        byMe = meId != null && requestedBy?.id == meId,
        createdAt = instantOf(createdAt),
        updatedAt = instantOf(updatedAt),
    )
}

private fun SeerrCastDto.toCast(): SeerrCastMember? = name?.let { SeerrCastMember(id, it, character?.takeIf(String::isNotBlank), tmdbImage(profilePath, PROFILE_SIZE)) }

private fun List<SeerrVideoDto>.trailerUrl(): String? = firstOrNull { it.site == "YouTube" && it.type == "Trailer" && !it.key.isNullOrBlank() }
    ?.let { it.url ?: "https://www.youtube.com/watch?v=${it.key}" }

internal fun SeerrMovieDto.toDetails(meId: Int?): SeerrTitleDetails {
    val certifications = releases?.results.orEmpty()
    val certification = (certifications.firstOrNull { it.country == CERTIFICATION_COUNTRY } ?: certifications.firstOrNull())
        ?.releaseDates?.firstNotNullOfOrNull { it.certification?.takeIf(String::isNotBlank) }
    return SeerrTitleDetails(
        title = SeerrTitle(
            tmdbId = id,
            type = SeerrMediaType.Movie,
            title = title.orEmpty(),
            year = yearOf(releaseDate),
            posterUrl = tmdbImage(posterPath, POSTER_SIZE),
            backdropUrl = tmdbImage(backdropPath, BACKDROP_SIZE),
            overview = overview?.takeIf { it.isNotBlank() },
            state = mediaInfo.state(),
            jellyfinItemId = mediaInfo.libraryItemId(),
            releaseDate = releaseDate,
        ),
        state4k = mediaInfo.state(is4k = true),
        genres = genres.mapNotNull { it.name },
        runtimeMinutes = runtime?.takeIf { it > 0 },
        certification = certification,
        cast = credits?.cast.orEmpty().mapNotNull { it.toCast() },
        trailerUrl = relatedVideos.trailerUrl(),
        seasons = emptyList(),
        requests = mediaInfo?.requests.orEmpty().mapNotNull { it.toRequest(meId, SeerrMediaType.Movie) },
    )
}

internal fun SeerrTvDto.toDetails(meId: Int?): SeerrTitleDetails {
    val ratings = contentRatings?.results.orEmpty()
    val certification = (ratings.firstOrNull { it.country == CERTIFICATION_COUNTRY } ?: ratings.firstOrNull())?.rating?.takeIf(String::isNotBlank)
    val seasonStatus = mediaInfo?.seasons.orEmpty().associateBy { it.seasonNumber }
    val requests = mediaInfo?.requests.orEmpty()
    return SeerrTitleDetails(
        title = SeerrTitle(
            tmdbId = id,
            type = SeerrMediaType.Show,
            title = name.orEmpty(),
            year = yearOf(firstAirDate),
            posterUrl = tmdbImage(posterPath, POSTER_SIZE),
            backdropUrl = tmdbImage(backdropPath, BACKDROP_SIZE),
            overview = overview?.takeIf { it.isNotBlank() },
            state = mediaInfo.state(),
            jellyfinItemId = mediaInfo.libraryItemId(),
            releaseDate = firstAirDate,
        ),
        state4k = mediaInfo.state(is4k = true),
        genres = genres.mapNotNull { it.name },
        runtimeMinutes = episodeRunTime.firstOrNull()?.takeIf { it > 0 },
        certification = certification,
        cast = credits?.cast.orEmpty().mapNotNull { it.toCast() },
        trailerUrl = relatedVideos.trailerUrl(),
        seasons = seasons.sortedBy { it.seasonNumber }.map { season ->
            val status = seasonStatus[season.seasonNumber]
            SeerrSeason(
                number = season.seasonNumber,
                name = season.name,
                episodeCount = season.episodeCount,
                year = yearOf(season.airDate),
                state = seasonState(status?.status, season.seasonNumber, requests, is4k = false),
                state4k = seasonState(status?.status4k, season.seasonNumber, requests, is4k = true),
            )
        },
        requests = requests.mapNotNull { it.toRequest(meId, SeerrMediaType.Show) },
    )
}

// A season's own status where Seerr has one; otherwise whether an open request asks for it.
private fun seasonState(status: Int?, number: Int, requests: List<SeerrRequestDto>, is4k: Boolean): RequestState {
    val asking = requests.filter { request -> request.is4k == is4k && request.seasons.any { it.seasonNumber == number } }
    return requestState(status ?: 1, asking, is4k)
}

internal fun SeerrQuotaDto.toQuota(): RequestQuota = RequestQuota(movies = movie?.toLeft(), shows = tv?.toLeft())

private fun SeerrQuotaPartDto.toLeft(): QuotaLeft? {
    val limit = limit?.takeIf { it > 0 } ?: return null
    return QuotaLeft(limit = limit, remaining = remaining ?: (limit - used).coerceAtLeast(0), days = days)
}
