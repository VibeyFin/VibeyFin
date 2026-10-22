package app.vibeyfin.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /Search/Hints`: what matched, ranked by the server. */
@Serializable
data class SearchHintsDto(
    @SerialName("SearchHints") val hints: List<SearchHintDto> = emptyList(),
)

/** One match: [type] is `Movie`, `Series`, `Episode`, `Person`, `BoxSet`, `Studio`, ... */
@Serializable
data class SearchHintDto(
    @SerialName("ItemId") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("BackdropImageTag") val backdropImageTag: String? = null,
    @SerialName("BackdropImageItemId") val backdropImageItemId: String? = null,
    @SerialName("PrimaryImageAspectRatio") val primaryImageAspectRatio: Double? = null,
    /** For an episode: the name of its show. */
    @SerialName("Series") val seriesName: String? = null,
    @SerialName("ParentIndexNumber") val seasonNumber: Int? = null,
    @SerialName("IndexNumber") val episodeNumber: Int? = null,
)

/** `GET /Search/Hints?searchTerm=`: movies, shows, episodes, people and collections that match, at most [limit]. */
suspend fun JellyfinApi.searchHints(userId: String, term: String, limit: Int): ApiResult<SearchHintsDto> = get(
    "/Search/Hints",
    mapOf(
        "userId" to userId,
        "searchTerm" to term,
        "limit" to limit.toString(),
        "includeItemTypes" to "Movie,Series,Episode,Person,BoxSet",
    ),
    retry = true,
)
