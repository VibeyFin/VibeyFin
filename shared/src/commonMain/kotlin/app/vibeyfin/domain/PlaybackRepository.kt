package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.ClientInfoProvider
import app.vibeyfin.data.api.MediaSourceDto
import app.vibeyfin.data.api.MediaStreamDto
import app.vibeyfin.data.api.PlaybackInfoRequestDto
import app.vibeyfin.data.api.authorizationHeader
import app.vibeyfin.data.api.itemsById
import app.vibeyfin.data.api.mediaSegments
import app.vibeyfin.data.api.playbackInfo
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.flow.first

/**
 * Asks the server how to play an item on this device, and turns its answer into something a player
 * can open. Reads the current session from [ConnectionManager] on every call. With nobody signed in
 * the answer is [ApiError.Unauthorized].
 */
class PlaybackRepository(
    private val connection: ConnectionManager,
    private val capabilities: PlaybackCapabilitiesSource,
    private val clientInfo: ClientInfoProvider,
    private val profileSettings: ProfileSettings,
) {
    /**
     * `PlaybackInfo` with this device's profile. With [forceTranscode] the server is told not to
     * direct play or direct stream, for when the device failed to play what it offered first.
     *
     * The server decides: direct play, then direct stream, then transcode.
     *
     * [audioStreamIndex] and [subtitleStreamIndex] are the user's choices ([SUBTITLES_OFF] for no
     * subtitles) for the media source [mediaSourceId]. The server ignores them without the source's
     * id, so they only count together; with none, it picks by the user's own settings.
     */
    suspend fun negotiate(
        itemId: String,
        forceTranscode: Boolean = false,
        mediaSourceId: String? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackNegotiation {
        val connected = connection.state.value as? ConnectionState.Connected
            ?: return PlaybackNegotiation.Failed(ApiError.Unauthorized)
        val api = connected.api
        // Read once per call, so one negotiation never mixes two qualities. No profile means Maximum.
        val maxBitrate = effectiveBitrate(profileSettings.maxStreamingBitrate.first() ?: 0)
        val request = PlaybackInfoRequestDto(
            userId = connected.account.userId,
            deviceProfile = capabilities.current().toDeviceProfile(maxBitrate),
            maxStreamingBitrate = maxBitrate,
            enableDirectPlay = !forceTranscode,
            enableDirectStream = !forceTranscode,
            enableTranscoding = true,
            autoOpenLiveStream = false,
            mediaSourceId = mediaSourceId,
            audioStreamIndex = audioStreamIndex.takeIf { mediaSourceId != null },
            subtitleStreamIndex = subtitleStreamIndex.takeIf { mediaSourceId != null },
        )
        val response = when (val result = api.playbackInfo(itemId, request)) {
            is ApiResult.Failure -> return PlaybackNegotiation.Failed(result.error)
            is ApiResult.Success -> result.value
        }
        val sessionId = response.playSessionId ?: return PlaybackNegotiation.Failed(ApiError.BadResponse)
        val directPlay = response.mediaSources.firstOrNull { it.supportsDirectPlay && !forceTranscode }
        val transcodes = response.mediaSources.filter { it.transcodingUrl != null }
        val source = directPlay
            ?: transcodes.firstOrNull()
            ?: return PlaybackNegotiation.NoCompatibleStream

        val method = when {
            directPlay != null -> PlayMethod.DirectPlay
            source.supportsDirectStream && !forceTranscode -> PlayMethod.DirectStream
            else -> PlayMethod.Transcode
        }
        val url = if (directPlay != null) {
            directPlayUrl(api.baseUrl, itemId, source, clientInfo.current().deviceId, sessionId)
        } else {
            serverUrl(api.baseUrl, checkNotNull(source.transcodingUrl))
        }
        val tracks = source.tracks(api.baseUrl)
        return PlaybackNegotiation.Ready(
            PlaybackPlan(
                itemId = itemId,
                mediaSourceId = source.id,
                playSessionId = sessionId,
                method = method,
                url = url,
                // The player sends this on every request; no URL here carries the token.
                headers = mapOf(HttpHeaders.Authorization to clientInfo.current().authorizationHeader(api.token)),
                runtimeMs = source.runTimeTicks?.div(TICKS_PER_MILLISECOND),
                audioTracks = tracks.audio,
                subtitleTracks = tracks.subtitles,
                subtitleFiles = tracks.subtitleFiles,
                subtitleBurnedIn = tracks.subtitleBurnedIn,
                audioStreamIndex = tracks.audio.firstOrNull { it.selected }?.index,
                subtitleStreamIndex = tracks.subtitles.firstOrNull { it.selected }?.index,
                videoFormat = source.mediaStreams.firstOrNull { it.type == "Video" }?.toVideoFormat(),
            ),
        )
    }

    /**
     * The skippable parts of an item (intro, credits, recap) the server has marked. Empty when there
     * are none, and on any failure: a server without the endpoint (before 10.10) answers 404, and a
     * missing skip button isn't worth an error.
     */
    suspend fun segments(itemId: String): List<MediaSegment> {
        val connected = connection.state.value as? ConnectionState.Connected ?: return emptyList()
        return when (val result = connected.api.mediaSegments(itemId)) {
            is ApiResult.Success -> result.value.items.map { it.toMediaSegment() }
            is ApiResult.Failure -> emptyList()
        }
    }

    /**
     * The trickplay thumbnails of media source [mediaSourceId], in the width closest to
     * [TRICKPLAY_TARGET_WIDTH]; null when the server has made none for it, or can't be asked.
     */
    suspend fun trickplay(itemId: String, mediaSourceId: String): TrickplayTrack? {
        val connected = connection.state.value as? ConnectionState.Connected ?: return null
        val item = when (val result = connected.api.itemsById(itemId, connected.account.userId)) {
            is ApiResult.Success -> result.value.items.firstOrNull() ?: return null
            is ApiResult.Failure -> return null
        }
        val track = item.trickplay[mediaSourceId]?.values?.toTrickplayTrack(mediaSourceId) ?: return null
        return track.copy(tilesUrl = serverUrl(connected.api.baseUrl, "/Videos/$itemId/Trickplay/${track.width}"))
    }

    companion object {
        /** The `SubtitleStreamIndex` for "no subtitles". */
        const val SUBTITLES_OFF = -1
    }
}

internal class Tracks(
    val audio: List<PlaybackTrack>,
    val subtitles: List<PlaybackTrack>,
    val subtitleFiles: List<SubtitleFile>,
    val subtitleBurnedIn: Boolean,
)

/**
 * The audio and subtitle tracks of this source. The server says which are selected: the user's
 * choice if the request named this source, else what it picked from the user's settings.
 *
 * Each subtitle has a delivery method that says what would happen if it were shown: `External` is a
 * file for the player to load, `Encode` is burned into the video. So every file is listed, for
 * switching without asking again, and a selected subtitle that is `Encode` is a burn-in.
 */
internal fun MediaSourceDto.tracks(baseUrl: String): Tracks {
    val audio = mediaStreams.filter { it.type == "Audio" }
    val subtitles = mediaStreams.filter { it.type == "Subtitle" }
    val audioIndex = defaultAudioStreamIndex?.takeIf { it >= 0 } ?: audio.firstOrNull()?.index
    val subtitleIndex = defaultSubtitleStreamIndex?.takeIf { it >= 0 }
    return Tracks(
        audio = audio.map { PlaybackTrack(it.index, it.name(), it.index == audioIndex, it.codec) },
        subtitles = subtitles.map { PlaybackTrack(it.index, it.name(), it.index == subtitleIndex) },
        subtitleFiles = subtitles.filter { it.deliveryMethod == "External" }.mapNotNull { it.toSubtitleFile(baseUrl) },
        subtitleBurnedIn = subtitles.firstOrNull { it.index == subtitleIndex }?.deliveryMethod == "Encode",
    )
}

/** The server's own name for the track; failing that, what the file says about it. */
internal fun MediaStreamDto.name(): String? =
    displayTitle?.takeIf { it.isNotBlank() }
        ?: title?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(language?.takeIf { it.isNotBlank() }, codec?.uppercase()).joinToString(" · ").ifEmpty { null }

private fun MediaStreamDto.toSubtitleFile(baseUrl: String): SubtitleFile? {
    val path = deliveryUrl ?: return null
    val extension = path.substringBefore('?').substringAfterLast('.', "").lowercase()
    val mimeType = when (extension) {
        "vtt" -> "text/vtt"
        "srt" -> "application/x-subrip"
        "ass", "ssa" -> "text/x-ssa"
        else -> return null
    }
    return SubtitleFile(index, serverUrl(baseUrl, path), mimeType, language?.takeIf { it.isNotBlank() }, name())
}

/** `GET /Videos/{id}/stream.{container}?static=true`: the file as it is on the server. */
private fun directPlayUrl(baseUrl: String, itemId: String, source: MediaSourceDto, deviceId: String, playSessionId: String): String {
    // The extension helps players that choose a parser by file name; ffprobe-style names can list several.
    val container = source.container?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
    return URLBuilder(baseUrl).apply {
        appendPathSegments("Videos", itemId, if (container != null) "stream.$container" else "stream")
        parameters.append("static", "true")
        parameters.append("mediaSourceId", source.id)
        parameters.append("deviceId", deviceId)
        parameters.append("playSessionId", playSessionId)
        source.etag?.let { parameters.append("tag", it) }
    }.buildString()
}

/** The server gives paths such as `/videos/{id}/master.m3u8?...`; they hang off its address. */
private fun serverUrl(baseUrl: String, path: String): String = when {
    path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true) -> path
    path.startsWith("/") -> baseUrl.trimEnd('/') + path
    else -> baseUrl.trimEnd('/') + "/" + path
}
