package app.vibeyfin.domain

import app.vibeyfin.domain.model.MediaItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An external subtitle file on the server, as a path that hangs off the server's address. */
@Serializable
class SubtitleSource(
    val index: Int,
    val path: String,
    val mimeType: String,
    val language: String?,
    val label: String?,
)

/** An external subtitle file that has been downloaded: [file] is relative to the downloads folder. */
@Serializable
class OfflineSubtitle(
    val index: Int,
    val file: String,
    val mimeType: String,
    val language: String?,
    val label: String?,
)

/**
 * Everything the screens need of an item, kept on the device so it can be shown without the server: the item as the app
 * showed it, the pictures and subtitle files that were fetched, and the audio and subtitle tracks the player offers.
 * [sources] says where each picture is on the server, by role (`poster`, `backdrop`, `landscape`, `logo`, `cast:{id}`),
 * as a path that hangs off whichever address of the server answers; [images] is the same by role once downloaded.
 * The shape lives in this file and is stored as JSON, so it can change without a database migration.
 */
@Serializable
data class OfflineSnapshot(
    val item: MediaItem,
    val sources: Map<String, String> = emptyMap(),
    val images: Map<String, String> = emptyMap(),
    val subtitleSources: List<SubtitleSource> = emptyList(),
    val subtitles: List<OfflineSubtitle> = emptyList(),
    val audioTracks: List<PlaybackTrack> = emptyList(),
    val subtitleTracks: List<PlaybackTrack> = emptyList(),
    /**
     * The scrub-preview thumbnails of the downloaded file, which are fetched with it into `trickplay/{tile}.jpg` beside the video;
     * null when the server made none, when a tile was gone, and for a download saved before they were kept (those ask the server).
     */
    val trickplay: TrickplayTrack? = null,
    /** Every picture, subtitle file and trickplay tile in [sources], [subtitleSources] and [trickplay] has been fetched (or the server had none). */
    val assetsDone: Boolean = false,
    /** The video stream's codec as Jellyfin names it (`h264`, `av1`…), so a download can be refused before it starts if the device cannot play it. */
    val videoCodec: String? = null,
    /** The video's level (as the server counts it) and bit depth, so the player can be chosen for the file without the server. Null when the server did not say. */
    val videoLevel: Int? = null,
    val videoBitDepth: Int? = null,
    /**
     * The tracks carry their language and default and forced flags, so the player can pick them from the profile's language
     * preferences. A download saved before they did keeps the tracks the server picked when it was downloaded.
     */
    val tracksDescribed: Boolean = false,
) {
    fun encode(): String = JSON.encodeToString(serializer(), this)

    /**
     * The item with its pictures pointing at the files on the device, and nothing at the server: a picture that was not
     * downloaded is left out, so a screen never waits for a network that isn't there.
     */
    fun mediaItem(files: DownloadFiles): MediaItem = item.copy(
        posterUrl = images[POSTER]?.let(files::uri),
        backdropUrl = images[BACKDROP]?.let(files::uri),
        landscapeUrl = images[LANDSCAPE]?.let(files::uri),
        logoUrl = images[LOGO]?.let(files::uri),
        cast = item.cast.map { it.copy(imageUrl = images[castRole(it.id)]?.let(files::uri)) },
    )

    companion object {
        const val POSTER = "poster"
        const val BACKDROP = "backdrop"
        const val LANDSCAPE = "landscape"
        const val LOGO = "logo"

        fun castRole(personId: String) = "cast:$personId"

        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun decode(json: String): OfflineSnapshot = JSON.decodeFromString(serializer(), json)
    }
}
