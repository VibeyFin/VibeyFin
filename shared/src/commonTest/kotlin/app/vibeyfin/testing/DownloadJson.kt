package app.vibeyfin.testing

/**
 * A movie or an episode as the server sends it when asked for a download: one media source of [size] bytes, a picture, and
 * optionally an external subtitle. Ids are made up as `ms-{id}` for the source.
 */
fun downloadItemJson(
    id: String,
    name: String = id,
    type: String = "Movie",
    size: Long? = 10,
    container: String = "mkv",
    primaryTag: String? = "p1",
    seriesId: String? = null,
    seriesName: String? = null,
    seasonId: String? = null,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    played: Boolean = false,
    subtitle: Boolean = false,
    cast: Boolean = false,
    videoCodec: String = "h264",
): String {
    val streams = buildList {
        add("""{"Index":0,"Type":"Video","Codec":"$videoCodec","Width":640,"Height":360}""")
        add("""{"Index":1,"Type":"Audio","Codec":"aac","Language":"eng","DisplayTitle":"English - AAC"}""")
        if (subtitle) {
            add(
                // As the server sends an item: a file next to the video, flagged external, with no delivery path.
                """{"Index":2,"Type":"Subtitle","Codec":"subrip","Language":"eng","DisplayTitle":"English","IsExternal":true}""",
            )
        }
    }.joinToString(",")
    val fields = listOfNotNull(
        """"Id":"$id"""",
        """"Name":"$name"""",
        """"Type":"$type"""",
        primaryTag?.let { """"ImageTags":{"Primary":"$it"}""" },
        seriesId?.let { """"SeriesId":"$it"""" },
        seriesName?.let { """"SeriesName":"$it"""" },
        seasonId?.let { """"SeasonId":"$it"""" },
        seasonNumber?.let { """"ParentIndexNumber":$it""" },
        episodeNumber?.let { """"IndexNumber":$it""" },
        """"UserData":{"Played":$played,"PlaybackPositionTicks":0}""",
        if (cast) """"People":[{"Id":"pe1","Name":"Ada Vance","Type":"Actor","Role":"Captain","PrimaryImageTag":"pt1"}]""" else null,
        """"MediaSources":[{"Id":"ms-$id","Container":"$container",${size?.let { """"Size":$it,""" } ?: ""}"ETag":"etag-$id","MediaStreams":[$streams]}]""",
    )
    return "{" + fields.joinToString(",") + "}"
}

fun itemsPage(vararg items: String) = """{"Items":[${items.joinToString(",")}],"TotalRecordCount":${items.size}}"""

/** A show or a season, with the fields a snapshot keeps. */
fun parentJson(id: String, name: String, type: String, primaryTag: String? = "pp", number: Int? = null, seriesId: String? = null) = "{" +
    listOfNotNull(
        """"Id":"$id"""", """"Name":"$name"""", """"Type":"$type"""", """"IsFolder":true""",
        primaryTag?.let { """"ImageTags":{"Primary":"$it"}""" },
        number?.let { """"IndexNumber":$it""" },
        seriesId?.let { """"SeriesId":"$it"""" },
    ).joinToString(",") + "}"

/**
 * Alex is signed in and the server has the show "Saga" (s1, season se1) with episodes e1 (watched, 100 bytes) and e2 (200 bytes),
 * and the movie m1 "Heat" (50 bytes). The page of each is read with `itemJson`, the download plan with the media sources.
 */
suspend fun TestApp.sagaServer(): FakeServer {
    val server = signedIn()
    fun episode(id: String, size: Long, played: Boolean, number: Int) = downloadItemJson(
        id, "Episode $number", "Episode", size = size, seriesId = "s1", seriesName = "Saga", seasonId = "se1",
        seasonNumber = 1, episodeNumber = number, played = played,
    )
    server.on("GET", "/Items") { request ->
        val forDownload = request.query["fields"]?.contains("MediaSources") == true
        when (request.query["ids"]) {
            "m1" -> Reply.json(itemsPage(if (forDownload) downloadItemJson("m1", "Heat", size = 50) else itemJson("m1", "Heat", type = "Movie", primaryTag = "p1")))
            "s1" -> Reply.json(itemsPage(itemJson("s1", "Saga", type = "Series", isFolder = true, primaryTag = "p1", childCount = 1)))
            "e1" -> Reply.json(itemsPage(episode("e1", 100, played = true, number = 1)))
            "s1,se1" -> Reply.json(itemsPage(parentJson("s1", "Saga", "Series"), parentJson("se1", "Season 1", "Season", number = 1, seriesId = "s1")))
            else -> Reply.json(itemsPage())
        }
    }
    server.on("GET", "/Shows/s1/Episodes") {
        Reply.json(itemsPage(episode("e1", 100, played = true, number = 1), episode("e2", 200, played = false, number = 2)))
    }
    server.on("GET", "/Shows/s1/Seasons", Reply.json(itemsPage(parentJson("se1", "Season 1", "Season", number = 1, seriesId = "s1"))))
    return server
}
