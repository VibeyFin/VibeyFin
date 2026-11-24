package app.vibeyfin.testing

import app.vibeyfin.data.db.DOWNLOAD_DONE
import app.vibeyfin.data.db.DownloadEntity
import app.vibeyfin.data.db.DownloadItemEntity
import app.vibeyfin.domain.OfflineSnapshot
import app.vibeyfin.domain.OfflineSubtitle
import app.vibeyfin.domain.PlaybackTrack
import app.vibeyfin.domain.TrickplayTrack
import app.vibeyfin.domain.model.CastMember
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem

/**
 * Puts an item on the device as a finished download of the profile "srv-1" / "u1" (Alex), with its file, picture, subtitle file and
 * saved copy, as the downloader leaves them; no server is asked. Returns the folder it is in. [runtimeMs] defaults to 100 s.
 */
suspend fun TestApp.downloaded(
    id: String,
    title: String = id,
    type: String = "Movie",
    seriesId: String? = null,
    seriesName: String? = null,
    season: Int? = null,
    episode: Int? = null,
    positionMs: Long = 0,
    played: Boolean = false,
    runtimeMs: Long = 100_000,
    subtitle: Boolean = false,
    cast: List<String> = emptyList(),
    state: String = DOWNLOAD_DONE,
    withFile: Boolean = true,
    userDataAt: Long = 1_000,
    /** The tracks carry their languages and flags, as a download saved by this build does. */
    described: Boolean = false,
    /** The container the file is in, and what the server said of its video, as the download saved them. */
    extension: String = "mkv",
    videoCodec: String? = null,
    videoLevel: Int? = null,
    videoBitDepth: Int? = null,
    /** The download kept this many tile images of scrub-preview thumbnails (250 thumbnails need three), as the downloader leaves them. */
    trickplayTiles: Int? = null,
): String {
    val folder = "srv-1/u1/$id"
    val kind = if (type == "Movie") ItemKind.Movie else ItemKind.Episode
    val item = MediaItem(
        id = id, title = title, kind = kind, isFolder = false, year = 2020, runtimeMinutes = null, genres = emptyList(), overview = null, rating = null,
        ageRating = null, seriesName = seriesName, seasonNumber = season, episodeNumber = episode, posterAspectRatio = 2f / 3f,
        posterUrl = "http://nas:8096/Items/$id/Images/Primary", backdropUrl = null, seriesId = seriesId, runtimeMs = runtimeMs,
        landscapeUrl = "http://nas:8096/Items/$id/Images/Thumb",
        cast = cast.mapIndexed { index, name -> CastMember("p$index", name, null, null) },
    )
    val snapshot = OfflineSnapshot(
        item = item,
        images = mapOf(OfflineSnapshot.POSTER to "$folder/images/poster.img", OfflineSnapshot.LANDSCAPE to "$folder/images/landscape.img"),
        audioTracks = listOf(PlaybackTrack(1, "English", true, "aac", "eng".takeIf { described }, default = described), PlaybackTrack(2, "German", false, "aac", "ger".takeIf { described })),
        subtitleTracks = if (subtitle) listOf(PlaybackTrack(3, "English", true, language = "eng".takeIf { described })) else emptyList(),
        subtitles = if (subtitle) listOf(OfflineSubtitle(3, "$folder/subs/3.srt", "application/x-subrip", "eng", "English")) else emptyList(),
        trickplay = trickplayTiles?.let {
            TrickplayTrack("ms-$id", 320, 10, 10, 320, 180, thumbnailCount = it * 100 - 50, intervalMs = 10_000)
        },
        assetsDone = true,
        tracksDescribed = described,
        videoCodec = videoCodec,
        videoLevel = videoLevel,
        videoBitDepth = videoBitDepth,
    )
    if (withFile) {
        files.contents["$folder/video.$extension"] = StringBuilder("video")
        files.contents["$folder/images/poster.img"] = StringBuilder("p")
        if (subtitle) files.contents["$folder/subs/3.srt"] = StringBuilder("1\n")
        repeat(trickplayTiles ?: 0) { files.contents["$folder/trickplay/$it.jpg"] = StringBuilder("tile$it") }
    }
    downloads.upsert(
        DownloadEntity(
            serverId = "srv-1", userId = "u1", itemId = id, kind = kind.name, seriesId = seriesId, seasonId = null, state = state,
            bytesDone = 5, bytesTotal = 5, mediaSourceId = "ms-$id", container = extension, etag = null, filePath = "$folder/video.$extension", error = null,
            addedAt = 10, updatedAt = 10, completedAt = 10,
        ),
    )
    downloads.upsertItems(
        listOf(
            DownloadItemEntity(
                serverId = "srv-1", userId = "u1", itemId = id, kind = kind.name, title = title, seriesId = seriesId, seriesName = seriesName,
                seasonId = null, seasonNumber = season, episodeNumber = episode,
                searchText = (listOfNotNull(title, seriesName) + cast).joinToString(" ").lowercase(),
                positionTicks = positionMs * 10_000, played = played, userDataAt = userDataAt, snapshotJson = snapshot.encode(),
            ),
        ),
    )
    return folder
}

/** The show an episode downloaded with [downloaded] sits under, as the downloader saves it. */
suspend fun TestApp.downloadedShow(id: String, title: String) {
    val item = MediaItem(
        id = id, title = title, kind = ItemKind.Series, isFolder = true, year = null, runtimeMinutes = null, genres = emptyList(), overview = null,
        rating = null, ageRating = null, seriesName = null, seasonNumber = null, episodeNumber = null, posterAspectRatio = 2f / 3f,
        posterUrl = "http://nas:8096/Items/$id/Images/Primary", backdropUrl = null,
    )
    files.contents["srv-1/u1/$id/images/poster.img"] = StringBuilder("p")
    downloads.upsertItems(
        listOf(
            DownloadItemEntity(
                serverId = "srv-1", userId = "u1", itemId = id, kind = ItemKind.Series.name, title = title, seriesId = null, seriesName = null,
                seasonId = null, seasonNumber = null, episodeNumber = null, searchText = title.lowercase(), positionTicks = 0, played = false,
                userDataAt = 0,
                snapshotJson = OfflineSnapshot(item = item, images = mapOf(OfflineSnapshot.POSTER to "srv-1/u1/$id/images/poster.img"), assetsDone = true).encode(),
            ),
        ),
    )
}
