package app.vibeyfin.testing

/** One item as the server sends it, with only the fields a test cares about. */
fun itemJson(
    id: String,
    name: String? = id,
    type: String = "Movie",
    isFolder: Boolean = false,
    /** On a library: `movies`, `tvshows`, `music`, ... */
    collectionType: String? = null,
    year: Int? = null,
    runtimeTicks: Long? = null,
    overview: String? = null,
    genres: List<String> = emptyList(),
    communityRating: Double? = null,
    criticRating: Double? = null,
    officialRating: String? = null,
    primaryTag: String? = null,
    backdropTag: String? = null,
    aspectRatio: Double? = null,
    seriesName: String? = null,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    seriesId: String? = null,
    /** For an episode or a season: the tag of the show's own poster. */
    seriesPrimaryImageTag: String? = null,
    thumbTag: String? = null,
    /** The item's `Logo` image tag. */
    logoTag: String? = null,
    /** `People` as the server sends them: each from [personJson]. */
    people: List<String> = emptyList(),
    /** On a series: how many seasons (`fields=ChildCount`). */
    childCount: Int? = null,
    /** Where the user stopped; with [played] this becomes the item's `UserData`. */
    positionTicks: Long? = null,
    played: Boolean? = null,
    /** On "My List": the item's `UserData.IsFavorite`. */
    favorite: Boolean? = null,
    /** How many trailers the server holds (`LocalTrailerCount`). */
    localTrailerCount: Int? = null,
    /** Trailers on the web: the `Url` of each. */
    remoteTrailers: List<String> = emptyList(),
    /** On a genre: how many movies and shows carry it (`fields=ItemCounts`). */
    movieCount: Int? = null,
    seriesCount: Int? = null,
    /** On a person: the birth date, as the server sends it. */
    premiereDate: String? = null,
    productionLocations: List<String> = emptyList(),
    /** `ImageBlurHashes` as the server sends it: image type, then image tag, then the hash. */
    blurHashes: Map<String, Map<String, String>> = emptyMap(),
    /** When the item reached the server (`fields=DateCreated`). */
    dateCreated: String? = null,
    /** The video track's `VideoRangeType` (`SDR`, `HDR10`, `DOVI`), sent as one stream (`fields=MediaStreams`). */
    videoRange: String? = null,
): String {
    fun string(value: String?) = value?.let { "\"$it\"" } ?: "null"
    val fields = listOf(
        """"Id":"$id"""",
        """"Name":${string(name)}""",
        """"Type":"$type"""",
        """"IsFolder":$isFolder""",
        """"CollectionType":${string(collectionType)}""",
        """"ProductionYear":${year ?: "null"}""",
        """"RunTimeTicks":${runtimeTicks ?: "null"}""",
        """"Overview":${string(overview)}""",
        """"Genres":[${genres.joinToString(",") { "\"$it\"" }}]""",
        """"CommunityRating":${communityRating ?: "null"}""",
        """"CriticRating":${criticRating ?: "null"}""",
        """"OfficialRating":${string(officialRating)}""",
        """"ImageTags":{${listOfNotNull(primaryTag?.let { "\"Primary\":\"$it\"" }, thumbTag?.let { "\"Thumb\":\"$it\"" }, logoTag?.let { "\"Logo\":\"$it\"" }).joinToString(",")}}""",
        """"BackdropImageTags":[${backdropTag?.let { "\"$it\"" } ?: ""}]""",
        """"ImageBlurHashes":{${
            blurHashes.entries.joinToString(",") { (type, byTag) ->
                "\"$type\":{${byTag.entries.joinToString(",") { (tag, hash) -> "\"$tag\":\"$hash\"" }}}"
            }
        }}""",
        """"PrimaryImageAspectRatio":${aspectRatio ?: "null"}""",
        """"SeriesName":${string(seriesName)}""",
        """"ParentIndexNumber":${seasonNumber ?: "null"}""",
        """"IndexNumber":${episodeNumber ?: "null"}""",
        """"SeriesId":${string(seriesId)}""",
        """"SeriesPrimaryImageTag":${string(seriesPrimaryImageTag)}""",
        """"UserData":${
            if (positionTicks == null && played == null && favorite == null) {
                "null"
            } else {
                """{"PlaybackPositionTicks":${positionTicks ?: 0},"Played":${played ?: false},"IsFavorite":${favorite ?: false}}"""
            }
        }""",
        """"LocalTrailerCount":${localTrailerCount ?: "null"}""",
        """"RemoteTrailers":[${remoteTrailers.joinToString(",") { "{\"Url\":\"$it\"}" }}]""",
        """"MovieCount":${movieCount ?: "null"}""",
        """"SeriesCount":${seriesCount ?: "null"}""",
        """"People":[${people.joinToString(",")}]""",
        """"ChildCount":${childCount ?: "null"}""",
        """"PremiereDate":${string(premiereDate)}""",
        """"ProductionLocations":[${productionLocations.joinToString(",") { "\"$it\"" }}]""",
        """"DateCreated":${string(dateCreated)}""",
        """"MediaStreams":[${videoRange?.let { "{\"Index\":0,\"Type\":\"Video\",\"VideoRangeType\":\"$it\"}" } ?: ""}]""",
        """"SomethingWeDontRead":true""",
    )
    return fields.joinToString(prefix = "{", postfix = "}")
}

/** A page of items: [total] counts the whole list, [startIndex] is where this page starts in it. */
fun itemsJson(vararg items: String, total: Int = items.size, startIndex: Int = 0) = """{"Items":[${items.joinToString(",")}],"TotalRecordCount":$total,"StartIndex":$startIndex}"""

/** Ticks in a minute: Jellyfin counts runtime in 100-nanosecond ticks. */
const val TICKS_PER_MINUTE = 600_000_000L

/** One person of an item's cast or crew, as the server sends them. */
fun personJson(id: String, name: String, type: String = "Actor", role: String? = null, imageTag: String? = null): String {
    fun string(value: String?) = value?.let { "\"$it\"" } ?: "null"
    return """{"Id":"$id","Name":"$name","Type":"$type","Role":${string(role)},"PrimaryImageTag":${string(imageTag)}}"""
}
