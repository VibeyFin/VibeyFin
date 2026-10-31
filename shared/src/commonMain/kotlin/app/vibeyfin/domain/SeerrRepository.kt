package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.map
import app.vibeyfin.data.seerr.SeasonChoice
import app.vibeyfin.data.seerr.SeerrPageDto
import app.vibeyfin.data.seerr.SeerrResultDto
import app.vibeyfin.data.seerr.deleteRequest
import app.vibeyfin.data.seerr.movie
import app.vibeyfin.data.seerr.popularMovies
import app.vibeyfin.data.seerr.popularShows
import app.vibeyfin.data.seerr.quota
import app.vibeyfin.data.seerr.requestMovie
import app.vibeyfin.data.seerr.requestShow
import app.vibeyfin.data.seerr.requests
import app.vibeyfin.data.seerr.search
import app.vibeyfin.data.seerr.show
import app.vibeyfin.data.seerr.trending
import app.vibeyfin.data.seerr.upcomingMovies
import app.vibeyfin.data.seerr.upcomingShows
import app.vibeyfin.domain.model.RequestQuota
import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.domain.model.SeerrPage
import app.vibeyfin.domain.model.SeerrRequest
import app.vibeyfin.domain.model.SeerrRequestPage
import app.vibeyfin.domain.model.SeerrTitleDetails

/** The discover lists Home shows as rows. */
enum class DiscoverList { Trending, PopularMovies, PopularShows, UpcomingMovies, UpcomingShows }

/** How asking for a title went. */
sealed interface RequestOutcome {
    /** Sent; [request] is null when Seerr found nothing left to ask for, which leaves the title as it was. */
    data class Sent(val request: SeerrRequest?) : RequestOutcome

    /** The profile has used up its requests of this kind for now; [quota] says how many it gets, when Seerr told. */
    data class QuotaReached(val quota: RequestQuota?) : RequestOutcome

    /** Someone already asked for it. */
    data object AlreadyRequested : RequestOutcome

    /** The profile may not request this, or not in this quality. */
    data object NotAllowed : RequestOutcome

    data class Failed(val error: ApiError) : RequestOutcome
}

/**
 * What features ask of Seerr, as the signed-in profile, through [SeerrConnection.call]. Not signed in to Seerr is
 * [ApiError.Unauthorized]. Lists hold movies and shows only: people can't be requested.
 */
class SeerrRepository(private val seerr: SeerrConnection) {
    suspend fun search(term: String, page: Int = 1): ApiResult<SeerrPage> = seerr.call { api, _ -> api.search(term, page).map { it.toPage() } }

    suspend fun discover(list: DiscoverList, page: Int = 1): ApiResult<SeerrPage> = seerr.call { api, _ ->
        when (list) {
            DiscoverList.Trending -> api.trending(page)
            DiscoverList.PopularMovies -> api.popularMovies(page)
            DiscoverList.PopularShows -> api.popularShows(page)
            DiscoverList.UpcomingMovies -> api.upcomingMovies(page)
            DiscoverList.UpcomingShows -> api.upcomingShows(page)
        }.map { it.toPage() }
    }

    /** Everything the Seerr page of a title shows. */
    suspend fun title(type: SeerrMediaType, tmdbId: Int): ApiResult<SeerrTitleDetails> = seerr.call { api, me ->
        when (type) {
            SeerrMediaType.Movie -> api.movie(tmdbId).map { it.toDetails(me.id) }
            SeerrMediaType.Show -> api.show(tmdbId).map { it.toDetails(me.id) }
        }
    }

    /**
     * Asks for a movie, or for [seasons] of a show (every missing season when left out), in 4K when [is4k]. Never repeated:
     * a request is a write. Seerr says only "forbidden" both for a quota that is used up and for a missing permission, so
     * the quota is read to tell them apart.
     */
    suspend fun request(type: SeerrMediaType, tmdbId: Int, seasons: SeasonChoice? = null, is4k: Boolean = false): RequestOutcome {
        val sent = seerr.call { api, me ->
            when (type) {
                SeerrMediaType.Movie -> api.requestMovie(tmdbId, is4k)
                SeerrMediaType.Show -> api.requestShow(tmdbId, seasons ?: SeasonChoice.All, is4k)
            }.map { it?.toRequest(me.id, type) }
        }
        return when (sent) {
            is ApiResult.Success -> RequestOutcome.Sent(sent.value)

            is ApiResult.Failure -> when (val error = sent.error) {
                ApiError.Forbidden -> {
                    val quota = quota().let { (it as? ApiResult.Success)?.value }
                    val left = quota?.forType(type)
                    if (left != null && left.remaining <= 0) RequestOutcome.QuotaReached(quota) else RequestOutcome.NotAllowed
                }

                ApiError.Unexpected(CONFLICT) -> RequestOutcome.AlreadyRequested

                else -> RequestOutcome.Failed(error)
            }
        }
    }

    /** Withdraws a request that still waits for approval. */
    suspend fun cancel(requestId: Int): ApiResult<Unit> = seerr.call { api, _ -> api.deleteRequest(requestId) }

    /** The profile's own requests, most recently changed first; [page] counts from 1. */
    suspend fun myRequests(page: Int = 1, pageSize: Int = PAGE_SIZE): ApiResult<SeerrRequestPage> = seerr.call { api, me ->
        api.requests(requestedBy = me.id, take = pageSize, skip = (page - 1) * pageSize).map { result ->
            SeerrRequestPage(
                requests = result.results.mapNotNull { it.toRequest(me.id) },
                page = page,
                pages = result.pageInfo.pages,
            )
        }
    }

    suspend fun quota(): ApiResult<RequestQuota> = seerr.call { api, me -> api.quota(me.id).map { it.toQuota() } }

    /** The Seerr's version, as Settings shows it; null when it doesn't say. */
    suspend fun version(): ApiResult<String?> = seerr.call { api, _ -> api.status().map { it.version } }

    private fun SeerrPageDto<SeerrResultDto>.toPage() = SeerrPage(titles = results.mapNotNull { it.toTitle() }, page = page, totalPages = totalPages)

    private companion object {
        const val CONFLICT = 409
        const val PAGE_SIZE = 20
    }
}
