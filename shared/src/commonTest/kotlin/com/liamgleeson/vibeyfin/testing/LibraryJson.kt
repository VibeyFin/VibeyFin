package com.liamgleeson.vibeyfin.testing

/** One item as the server sends it, with only the fields a test cares about. */
fun itemJson(
    id: String,
    name: String? = id,
    type: String = "Movie",
    isFolder: Boolean = false,
    year: Int? = null,
    runtimeTicks: Long? = null,
    overview: String? = null,
    genres: List<String> = emptyList(),
    communityRating: Double? = null,
    officialRating: String? = null,
    primaryTag: String? = null,
    backdropTag: String? = null,
    aspectRatio: Double? = null,
    seriesName: String? = null,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    seriesId: String? = null,
    thumbTag: String? = null,
    /** Where the user stopped; with [played] this becomes the item's `UserData`. */
    positionTicks: Long? = null,
    played: Boolean? = null,
): String {
    fun string(value: String?) = value?.let { "\"$it\"" } ?: "null"
    val fields = listOf(
        """"Id":"$id"""",
        """"Name":${string(name)}""",
        """"Type":"$type"""",
        """"IsFolder":$isFolder""",
        """"ProductionYear":${year ?: "null"}""",
        """"RunTimeTicks":${runtimeTicks ?: "null"}""",
        """"Overview":${string(overview)}""",
        """"Genres":[${genres.joinToString(",") { "\"$it\"" }}]""",
        """"CommunityRating":${communityRating ?: "null"}""",
        """"OfficialRating":${string(officialRating)}""",
        """"ImageTags":{${listOfNotNull(primaryTag?.let { "\"Primary\":\"$it\"" }, thumbTag?.let { "\"Thumb\":\"$it\"" }).joinToString(",")}}""",
        """"BackdropImageTags":[${backdropTag?.let { "\"$it\"" } ?: ""}]""",
        """"PrimaryImageAspectRatio":${aspectRatio ?: "null"}""",
        """"SeriesName":${string(seriesName)}""",
        """"ParentIndexNumber":${seasonNumber ?: "null"}""",
        """"IndexNumber":${episodeNumber ?: "null"}""",
        """"SeriesId":${string(seriesId)}""",
        """"UserData":${
            if (positionTicks == null && played == null) {
                "null"
            } else {
                """{"PlaybackPositionTicks":${positionTicks ?: 0},"Played":${played ?: false}}"""
            }
        }""",
        """"SomethingWeDontRead":true""",
    )
    return fields.joinToString(prefix = "{", postfix = "}")
}

/** A page of items: [total] counts the whole list, [startIndex] is where this page starts in it. */
fun itemsJson(vararg items: String, total: Int = items.size, startIndex: Int = 0) =
    """{"Items":[${items.joinToString(",")}],"TotalRecordCount":$total,"StartIndex":$startIndex}"""

/** Ticks in a minute: Jellyfin counts runtime in 100-nanosecond ticks. */
const val TICKS_PER_MINUTE = 600_000_000L
