package app.vibeyfin.data.seerr

import app.vibeyfin.data.api.ApiResult
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

// Finding titles: search, the discover lists and a title's details. All reads, so all are retried.

/**
 * `GET /search?query=`: movies, shows and people on TMDB, each with what Seerr knows about it. Seerr refuses a query with
 * any character a URL reserves, even `+` for a space or a bare `'`, so [term] is percent-encoded here, strictly.
 */
suspend fun SeerrApi.search(term: String, page: Int = 1): ApiResult<SeerrPageDto<SeerrResultDto>> = execute({ it.body() }, retry = true) {
    method = HttpMethod.Get
    url(endpoint("/search"))
    url.encodedParameters.append("query", seerrQueryEncode(term))
    parameter("page", page)
}

/** `GET /discover/trending`: movies, shows and people trending on TMDB today. */
suspend fun SeerrApi.trending(page: Int = 1): ApiResult<SeerrPageDto<SeerrResultDto>> = get("/discover/trending", mapOf("page" to "$page"), retry = true)

/** `GET /discover/movies`: popular movies. */
suspend fun SeerrApi.popularMovies(page: Int = 1): ApiResult<SeerrPageDto<SeerrResultDto>> = get("/discover/movies", mapOf("page" to "$page"), retry = true)

/** `GET /discover/tv`: popular shows. */
suspend fun SeerrApi.popularShows(page: Int = 1): ApiResult<SeerrPageDto<SeerrResultDto>> = get("/discover/tv", mapOf("page" to "$page"), retry = true)

/** `GET /discover/movies/upcoming`. */
suspend fun SeerrApi.upcomingMovies(page: Int = 1): ApiResult<SeerrPageDto<SeerrResultDto>> = get("/discover/movies/upcoming", mapOf("page" to "$page"), retry = true)

/** `GET /discover/tv/upcoming`. */
suspend fun SeerrApi.upcomingShows(page: Int = 1): ApiResult<SeerrPageDto<SeerrResultDto>> = get("/discover/tv/upcoming", mapOf("page" to "$page"), retry = true)

/** `GET /movie/{id}`, by TMDB id. */
suspend fun SeerrApi.movie(tmdbId: Int): ApiResult<SeerrMovieDto> = get("/movie/$tmdbId", retry = true)

/** `GET /tv/{id}`, by TMDB id. */
suspend fun SeerrApi.show(tmdbId: Int): ApiResult<SeerrTvDto> = get("/tv/$tmdbId", retry = true)

/** `GET /tv/{id}/season/{n}`: the season's episodes. */
suspend fun SeerrApi.showSeason(tmdbId: Int, season: Int): ApiResult<SeerrSeasonDetailsDto> = get("/tv/$tmdbId/season/$season", retry = true)

/** `GET /movie/{id}/ratingscombined`: the movie's IMDb (and Rotten Tomatoes) rating, which Seerr looks up when asked. */
suspend fun SeerrApi.movieRatings(tmdbId: Int): ApiResult<SeerrRatingsDto> = get("/movie/$tmdbId/ratingscombined", retry = true)

/** `GET /movie/{id}/recommendations`: what TMDB suggests for a movie, as "More like this". */
suspend fun SeerrApi.movieRecommendations(tmdbId: Int): ApiResult<SeerrPageDto<SeerrResultDto>> = get("/movie/$tmdbId/recommendations", retry = true)

/** `GET /tv/{id}/recommendations`. */
suspend fun SeerrApi.showRecommendations(tmdbId: Int): ApiResult<SeerrPageDto<SeerrResultDto>> = get("/tv/$tmdbId/recommendations", retry = true)

/** Percent-encodes everything but RFC 3986's unreserved characters, UTF-8 first, as Seerr's query validator wants. */
internal fun seerrQueryEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        if (c < 0x80 && (ch.isLetterOrDigit() || ch in "-._~")) {
            append(ch)
        } else {
            append('%')
            append(HEX[c shr 4])
            append(HEX[c and 0x0F])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
