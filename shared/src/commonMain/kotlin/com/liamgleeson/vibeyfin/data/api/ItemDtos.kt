package com.liamgleeson.vibeyfin.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `BaseItemDto` from the server's OpenAPI spec, with only the fields we read. */
@Serializable
data class BaseItemDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    /** `Movie`, `Series`, `Season`, `Episode`, `Folder`, `CollectionFolder`, `BoxSet`, ... */
    @SerialName("Type") val type: String? = null,
    @SerialName("IsFolder") val isFolder: Boolean = false,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("PrimaryImageAspectRatio") val primaryImageAspectRatio: Double? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
)

/** `BaseItemDtoQueryResult`: one page of items and how many there are in all. */
@Serializable
data class ItemsResultDto(
    @SerialName("Items") val items: List<BaseItemDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
    @SerialName("StartIndex") val startIndex: Int = 0,
)
