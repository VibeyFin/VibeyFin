package app.vibeyfin.data.api

import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// What a download needs of an item beyond its page: the files, with their sizes, and the streams inside them.
private const val DOWNLOAD_FIELDS = "MediaSources,MediaStreams,Overview,Genres,People,ProviderIds"

/** `GET /Items?ids=`: [ids] with their media sources (file sizes included), for the download confirmation and the download itself. */
suspend fun JellyfinApi.itemsForDownload(userId: String, ids: List<String>): ApiResult<ItemsResultDto> = get("/Items", mapOf("ids" to ids.joinToString(","), "userId" to userId, "fields" to DOWNLOAD_FIELDS), retry = true)

/** `GET /Shows/{id}/Episodes`: every episode of a show, Specials included, with its media sources. */
suspend fun JellyfinApi.episodesForDownload(userId: String, seriesId: String): ApiResult<ItemsResultDto> = get("/Shows/$seriesId/Episodes", mapOf("userId" to userId, "fields" to DOWNLOAD_FIELDS), retry = true)

/** `GET /Shows/{id}/Episodes?seasonId=`: the episodes of one season, with their media sources. */
suspend fun JellyfinApi.seasonEpisodesForDownload(userId: String, seriesId: String, seasonId: String): ApiResult<ItemsResultDto> = get(
    "/Shows/$seriesId/Episodes",
    mapOf("userId" to userId, "seasonId" to seasonId, "fields" to DOWNLOAD_FIELDS),
    retry = true,
)

/** How the bytes of a download begin: [resumed] is false when the server sent the whole file although a later part was asked for. */
class DownloadStart(
    /** The whole file's size, when the server said. */
    val totalBytes: Long?,
    val resumed: Boolean,
    /** What identifies this version of the file: its `ETag`, or else `Last-Modified` (Jellyfin sends the latter). A resume that sees it change starts over. */
    val validator: String?,
)

/** Takes a download's bytes as they arrive. An exception it throws ends the download as it is and reaches the caller of [download]. */
interface DownloadSink {
    /** Called once, before any [write]. When [DownloadStart.resumed] is false the sink starts its file over. */
    suspend fun begin(start: DownloadStart)

    suspend fun write(bytes: ByteArray, count: Int)
}

/** A [DownloadSink] that failed (the disk is full, say); that is not a network problem, so it is not turned into [ApiError.Network]. */
internal class SinkFailure(cause: Throwable) : RuntimeException(cause)

private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
private const val DOWNLOAD_SOCKET_TIMEOUT_MS = 60_000L

/**
 * `GET /Items/{id}/Download`: the item's original file, from byte [offset] on, handed to [sink] as it arrives. It needs the
 * user's download permission (a refusal is [ApiError.Forbidden]). A file can be many gigabytes, so there is no request
 * timeout, only a socket timeout that ends a transfer which stops sending. On a network failure the bytes written so far stay
 * with the sink; ask again from where it got to.
 */
suspend fun JellyfinApi.download(itemId: String, offset: Long, sink: DownloadSink): ApiResult<Unit> = transfer("/Items/$itemId/Download", offset, long = true, sink)

/**
 * `GET` of a small file the server holds for an item, such as an image or a subtitle, given as a path with its query that
 * hangs off the server's address. Ends in the same way as [download], with the ordinary timeouts.
 */
suspend fun JellyfinApi.fetch(path: String, sink: DownloadSink): ApiResult<Unit> = transfer(path, offset = 0, long = false, sink)

private suspend fun JellyfinApi.transfer(path: String, offset: Long, long: Boolean, sink: DownloadSink): ApiResult<Unit> = stream(
    configure = {
        method = HttpMethod.Get
        url(baseUrl.trimEnd('/') + path)
        if (offset > 0) header(HttpHeaders.Range, "bytes=$offset-")
        if (long) {
            timeout {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = DOWNLOAD_SOCKET_TIMEOUT_MS
            }
        }
    },
    read = { response ->
        val resumed = response.status.value == 206
        val total = if (resumed) {
            // Content-Range: bytes 100-999/1000
            response.headers[HttpHeaders.ContentRange]?.substringAfterLast('/')?.toLongOrNull()
        } else {
            response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        }
        val validator = response.headers[HttpHeaders.ETag] ?: response.headers[HttpHeaders.LastModified]
        sinkCall { sink.begin(DownloadStart(total, resumed = resumed || offset == 0L, validator = validator)) }
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
        while (true) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read < 0) break
            if (read > 0) sinkCall { sink.write(buffer, read) }
        }
    },
)

private inline fun sinkCall(block: () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        throw SinkFailure(e)
    }
}

/** `UpdateUserItemDataDto`, with the fields offline progress sets. */
@Serializable
data class UpdateUserItemDataDto(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long,
    /** Left out, the server keeps what it has. */
    @SerialName("Played") val played: Boolean? = null,
    /** When the user last watched it, in the server's date format. */
    @SerialName("LastPlayedDate") val lastPlayedDate: String? = null,
)

/**
 * `POST /UserItems/{id}/UserData`: sets where the user stopped, whether they finished, and when they last watched. This is how
 * progress made without a connection reaches the server: there was no play session to report it through.
 */
suspend fun JellyfinApi.updateUserData(userId: String, itemId: String, data: UpdateUserItemDataDto): ApiResult<Unit> = post<UpdateUserItemDataDto, Unit>("/UserItems/$itemId/UserData", data, mapOf("userId" to userId))
