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
    /** BlurHash strings by image type, then by image tag: `{"Primary": {"<tag>": "<hash>"}}`. */
    @SerialName("ImageBlurHashes") val imageBlurHashes: Map<String, Map<String, String>> = emptyMap(),
    @SerialName("PrimaryImageAspectRatio") val primaryImageAspectRatio: Double? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    /** For an episode or a season: the show it belongs to. */
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("UserData") val userData: UserItemDataDto? = null,
    /** The tracks of the item's file; only sent when asked for (see `DETAIL_FIELDS`). */
    @SerialName("MediaStreams") val mediaStreams: List<MediaStreamDto> = emptyList(),
    /** Trickplay thumbnails by media source id, then by width in pixels; only sent when asked for. */
    @SerialName("Trickplay") val trickplay: Map<String, Map<String, TrickplayInfoDto>> = emptyMap(),
)

/** `UserItemDataDto`: what this user has done with an item. Only the fields we read. */
@Serializable
data class UserItemDataDto(
    /** Where the user stopped, in 100 ns ticks; 0 when they haven't started it or have finished it. */
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerialName("Played") val played: Boolean = false,
)


/** One set of trickplay thumbnails: [thumbnailCount] pictures of [width] by [height], packed into tiles of [tileWidth] by [tileHeight]. */
@Serializable
data class TrickplayInfoDto(
    @SerialName("Width") val width: Int,
    @SerialName("Height") val height: Int,
    @SerialName("TileWidth") val tileWidth: Int,
    @SerialName("TileHeight") val tileHeight: Int,
    @SerialName("ThumbnailCount") val thumbnailCount: Int,
    /** Milliseconds between two thumbnails. */
    @SerialName("Interval") val interval: Int,
)

/** `BaseItemDtoQueryResult`: one page of items and how many there are in all. */
@Serializable
data class ItemsResultDto(
    @SerialName("Items") val items: List<BaseItemDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
    @SerialName("StartIndex") val startIndex: Int = 0,
)
