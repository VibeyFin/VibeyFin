package app.vibeyfin.data.api

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
    /** On a library: `movies`, `tvshows`, `music`, `boxsets`, ...; absent for a library of mixed content. */
    @SerialName("CollectionType") val collectionType: String? = null,
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
    /** For an episode or a season: the tag of the show's own poster, which is the picture a row of shows wants. */
    @SerialName("SeriesPrimaryImageTag") val seriesPrimaryImageTag: String? = null,
    /** On an episode: the season it belongs to. */
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("UserData") val userData: UserItemDataDto? = null,
    /** The cast and crew; only sent when asked for (`fields=People`, see `DETAIL_FIELDS`). */
    @SerialName("People") val people: List<PersonDto> = emptyList(),
    /** On a series: how many seasons it has; only sent when asked for (`fields=ChildCount`). */
    @SerialName("ChildCount") val childCount: Int? = null,
    /** How many trailers the server holds for the item itself (`GET /Items/{id}/LocalTrailers`). */
    @SerialName("LocalTrailerCount") val localTrailerCount: Int? = null,
    /** Trailers on the web, usually YouTube pages. */
    @SerialName("RemoteTrailers") val remoteTrailers: List<TrailerDto> = emptyList(),
    /** On a genre: how many movies and shows carry it; only sent when asked for (`fields=ItemCounts`). */
    @SerialName("MovieCount") val movieCount: Int? = null,
    @SerialName("SeriesCount") val seriesCount: Int? = null,
    /** On a person: the birth date (`1999-10-17T00:00:00.0000000Z`); on a title, its release date. */
    @SerialName("PremiereDate") val premiereDate: String? = null,
    /** When the item was added to the library (`2023-11-14T20:13:20.0000000Z`); only sent when asked for (`fields=DateCreated`). */
    @SerialName("DateCreated") val dateCreated: String? = null,
    /** On a person: where they were born. */
    @SerialName("ProductionLocations") val productionLocations: List<String> = emptyList(),
    /** The tracks of the item's file; only sent when asked for (see `DETAIL_FIELDS`). */
    @SerialName("MediaStreams") val mediaStreams: List<MediaStreamDto> = emptyList(),
    /** The versions of the item's file with their size; only sent when asked for (`fields=MediaSources`), as a download does. */
    @SerialName("MediaSources") val mediaSources: List<MediaSourceDto> = emptyList(),
    /** Trickplay thumbnails by media source id, then by width in pixels; only sent when asked for. */
    @SerialName("Trickplay") val trickplay: Map<String, Map<String, TrickplayInfoDto>> = emptyMap(),
    /** Ids on other sites by name (`Tmdb`, `Imdb`, ...); only sent when asked for (`fields=ProviderIds`). */
    @SerialName("ProviderIds") val providerIds: Map<String, String?> = emptyMap(),
)

/** A trailer on the web: [url] is a page to open. */
@Serializable
data class TrailerDto(
    @SerialName("Url") val url: String? = null,
    @SerialName("Name") val name: String? = null,
)

/** One person of an item's cast or crew: [type] is `Actor`, `GuestStar`, `Director`, `Writer`, ... */
@Serializable
data class PersonDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    /** The part they play, for an actor. */
    @SerialName("Role") val role: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
)

/** `UserItemDataDto`: what this user has done with an item. Only the fields we read. */
@Serializable
data class UserItemDataDto(
    /** Where the user stopped, in 100 ns ticks; 0 when they haven't started it or have finished it. */
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerialName("Played") val played: Boolean = false,
    /** The user's favourite: "My List". */
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    /** When the user last watched it, as the server writes a date (`2026-10-23T18:00:00.0000000Z`); null when never. */
    @SerialName("LastPlayedDate") val lastPlayedDate: String? = null,
)

/**
 * One set of trickplay thumbnails: [thumbnailCount] pictures of [width] by [height], packed into tiles of [tileWidth] by [tileHeight].
 * A field the server leaves out is 0, so one odd entry doesn't fail the whole item; a set missing its sizes or interval is never used.
 */
@Serializable
data class TrickplayInfoDto(
    @SerialName("Width") val width: Int = 0,
    @SerialName("Height") val height: Int = 0,
    @SerialName("TileWidth") val tileWidth: Int = 0,
    @SerialName("TileHeight") val tileHeight: Int = 0,
    @SerialName("ThumbnailCount") val thumbnailCount: Int = 0,
    /** Milliseconds between two thumbnails. */
    @SerialName("Interval") val interval: Int = 0,
)

/** `BaseItemDtoQueryResult`: one page of items and how many there are in all. */
@Serializable
data class ItemsResultDto(
    @SerialName("Items") val items: List<BaseItemDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
    @SerialName("StartIndex") val startIndex: Int = 0,
)
