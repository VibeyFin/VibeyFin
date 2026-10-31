package app.vibeyfin.data.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Shapes from Seerr's OpenAPI spec (`seerr-api.yml`, base path `/api/v1`), with only the fields we read. Several fields Seerr
// sends are missing from its spec (`jellyfinUserId`, `displayName`, `mediaInfo.jellyfinMediaId`, `mediaInfo.seasons`); they
// were checked against Seerr 3.5.1 on the test server.

/** `GET /status`. */
@Serializable
data class SeerrStatusDto(
    val version: String? = null,
)

/** `GET /settings/public`: what any client may know before signing in. */
@Serializable
data class SeerrPublicSettingsDto(
    val initialized: Boolean = false,
    val applicationTitle: String? = null,
    /** 1 Plex, 2 Jellyfin, 3 Emby, 4 not set up. */
    val mediaServerType: Int? = null,
    val movie4kEnabled: Boolean = false,
    val series4kEnabled: Boolean = false,
    val partialRequestsEnabled: Boolean = true,
    val enableSpecialEpisodes: Boolean = false,
)

/** A Seerr user, from `/auth/me` and the sign-in calls. [permissions] is Seerr's permission bit set. */
@Serializable
data class SeerrUserDto(
    val id: Int,
    val displayName: String? = null,
    val jellyfinUsername: String? = null,
    /** The Jellyfin user this Seerr user signs in as, in Jellyfin's own form (32 hex digits, no dashes). */
    val jellyfinUserId: String? = null,
    val permissions: Long = 0,
)

/** `POST /auth/jellyfin/quickconnect/initiate`: [code] is approved on the Jellyfin server, [secret] finishes the sign-in. */
@Serializable
data class SeerrQuickConnectDto(
    val code: String,
    val secret: String,
)

@Serializable
internal data class SeerrSecretDto(val secret: String)

@Serializable
internal data class SeerrPasswordDto(val username: String, val password: String)

/** One page of a list: search, discover. */
@Serializable
data class SeerrPageDto<T>(
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
    val results: List<T> = emptyList(),
)

/**
 * A movie, show or person in a search or discover list. Seerr's `MovieResult`, `TvResult` and `PersonResult` in one shape:
 * [mediaType] says which, movies fill [title] and [releaseDate], shows [name] and [firstAirDate].
 */
@Serializable
data class SeerrResultDto(
    val id: Int,
    val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String? = null,
    /** Absent for a title Seerr has never seen requested or in the library. */
    val mediaInfo: SeerrMediaInfoDto? = null,
)

/** What Seerr knows about a title: whether it is in the library and its requests. Statuses are Seerr's `MediaStatus`. */
@Serializable
data class SeerrMediaInfoDto(
    val tmdbId: Int? = null,
    val mediaType: String? = null,
    /** 1 unknown, 2 pending, 3 processing, 4 partly available, 5 available, 6 blocklisted, 7 deleted. */
    val status: Int = 1,
    val status4k: Int = 1,
    /** The Jellyfin item, once the title is in the library. */
    val jellyfinMediaId: String? = null,
    val seasons: List<SeerrSeasonStatusDto> = emptyList(),
    val requests: List<SeerrRequestDto> = emptyList(),
)

@Serializable
data class SeerrSeasonStatusDto(
    val seasonNumber: Int,
    val status: Int = 1,
    val status4k: Int = 1,
)

/** A request. [status] is Seerr's `MediaRequestStatus`: 1 pending approval, 2 approved, 3 declined, 4 failed, 5 completed. */
@Serializable
data class SeerrRequestDto(
    val id: Int,
    val status: Int,
    val is4k: Boolean = false,
    /** `movie` or `tv`. */
    val type: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val requestedBy: SeerrUserDto? = null,
    val media: SeerrMediaInfoDto? = null,
    val seasons: List<SeerrRequestSeasonDto> = emptyList(),
)

@Serializable
data class SeerrRequestSeasonDto(
    val seasonNumber: Int,
    val status: Int = 1,
)

/** `GET /request`. */
@Serializable
data class SeerrRequestPageDto(
    val pageInfo: SeerrPageInfoDto = SeerrPageInfoDto(),
    val results: List<SeerrRequestDto> = emptyList(),
)

@Serializable
data class SeerrPageInfoDto(
    val pages: Int = 1,
    val page: Int = 1,
    val results: Int = 0,
)

/**
 * `POST /request`. [seasons] is a list of season numbers or the string `all`, so it stays JSON; null for a movie.
 */
@Serializable
internal data class SeerrNewRequestDto(
    val mediaType: String,
    val mediaId: Int,
    val seasons: JsonElement? = null,
    val is4k: Boolean = false,
)

@Serializable
data class SeerrGenreDto(val id: Int, val name: String? = null)

@Serializable
data class SeerrCreditsDto(val cast: List<SeerrCastDto> = emptyList())

@Serializable
data class SeerrCastDto(
    val id: Int,
    val name: String? = null,
    val character: String? = null,
    val profilePath: String? = null,
)

/** A video TMDB links to a title. A trailer on YouTube has [site] `YouTube`, [type] `Trailer` and the video id in [key]. */
@Serializable
data class SeerrVideoDto(
    val url: String? = null,
    val key: String? = null,
    val name: String? = null,
    val type: String? = null,
    val site: String? = null,
)

/** `GET /movie/{id}`. */
@Serializable
data class SeerrMovieDto(
    val id: Int,
    val title: String? = null,
    val releaseDate: String? = null,
    /** Minutes. */
    val runtime: Int? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val genres: List<SeerrGenreDto> = emptyList(),
    val credits: SeerrCreditsDto? = null,
    val relatedVideos: List<SeerrVideoDto> = emptyList(),
    val releases: SeerrReleasesDto? = null,
    val mediaInfo: SeerrMediaInfoDto? = null,
)

@Serializable
data class SeerrReleasesDto(val results: List<SeerrCountryReleasesDto> = emptyList())

@Serializable
data class SeerrCountryReleasesDto(
    @SerialName("iso_3166_1") val country: String? = null,
    @SerialName("release_dates") val releaseDates: List<SeerrReleaseDateDto> = emptyList(),
)

@Serializable
data class SeerrReleaseDateDto(val certification: String? = null)

/** `GET /tv/{id}`. */
@Serializable
data class SeerrTvDto(
    val id: Int,
    val name: String? = null,
    val firstAirDate: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val genres: List<SeerrGenreDto> = emptyList(),
    /** Minutes, one per typical episode length. */
    val episodeRunTime: List<Int> = emptyList(),
    val seasons: List<SeerrSeasonDto> = emptyList(),
    val credits: SeerrCreditsDto? = null,
    val relatedVideos: List<SeerrVideoDto> = emptyList(),
    val contentRatings: SeerrContentRatingsDto? = null,
    val mediaInfo: SeerrMediaInfoDto? = null,
)

@Serializable
data class SeerrSeasonDto(
    val seasonNumber: Int,
    val name: String? = null,
    val episodeCount: Int = 0,
    val airDate: String? = null,
)

@Serializable
data class SeerrContentRatingsDto(val results: List<SeerrContentRatingDto> = emptyList())

@Serializable
data class SeerrContentRatingDto(
    @SerialName("iso_3166_1") val country: String? = null,
    val rating: String? = null,
)

/** `GET /user/{id}/quota`. */
@Serializable
data class SeerrQuotaDto(
    val movie: SeerrQuotaPartDto? = null,
    val tv: SeerrQuotaPartDto? = null,
)

/** One kind of quota: [limit] requests every [days] days, [remaining] of them left. No [limit]: unlimited. */
@Serializable
data class SeerrQuotaPartDto(
    val days: Int? = null,
    val limit: Int? = null,
    val used: Int = 0,
    val remaining: Int? = null,
    val restricted: Boolean = false,
)

/** `GET /movie/{id}/ratingscombined`: Rotten Tomatoes and IMDb. Seerr has no IMDb rating for shows. */
@Serializable
data class SeerrRatingsDto(val imdb: SeerrImdbRatingDto? = null)

/** IMDb's rating out of 10 (Seerr calls it `criticsScore`) and how many votes it rests on. */
@Serializable
data class SeerrImdbRatingDto(
    val criticsScore: Double? = null,
    val criticsScoreCount: Long? = null,
)

/** `GET /tv/{id}/season/{n}`: a season's episodes. Seerr sends `stillPath` as a whole TMDB URL. */
@Serializable
data class SeerrSeasonDetailsDto(
    val seasonNumber: Int = 0,
    val episodes: List<SeerrEpisodeDto> = emptyList(),
)

@Serializable
data class SeerrEpisodeDto(
    val episodeNumber: Int,
    val seasonNumber: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    val airDate: String? = null,
    val stillPath: String? = null,
    val voteAverage: Double? = null,
)
