package app.vibeyfin.app.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.vibeyfin.domain.TRICKPLAY_COMPRESSED_CACHE_BYTES
import app.vibeyfin.domain.TrickplayPreviewSize
import app.vibeyfin.domain.TrickplayTrack
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TILE_CONNECT_TIMEOUT_MS = 10_000
private const val TILE_READ_TIMEOUT_MS = 30_000

/**
 * The scrub-preview thumbnails of one playback. The tile images are fetched ahead of use, one at a time on a low-priority thread, from
 * [aim] (the whole film, or only the tiles near the position when the data is limited); what arrives is kept as the compressed bytes,
 * bounded, and only the tile being scrubbed and the next one in its direction are decoded, off the main thread. One thumbnail is then
 * cropped out by the shared maths. Make a new one for a new track (another episode) and [close] the old: that stops the fetching and
 * drops the cache. A tile that can't be had counts as missing for a short while (scrubbing carries on with the timestamp alone), then
 * it is tried again.
 */
class TrickplayTiles(private val track: TrickplayTrack, private val headers: Map<String, String>) {
    private val lock = Any()
    private val failed = ConcurrentHashMap<Int, Long>()
    private val fetching = mutableMapOf<Int, Deferred<ByteArray?>>()
    private val decoding = mutableMapOf<Int, Deferred<Bitmap?>>()
    private val decoded = ConcurrentHashMap<Int, Bitmap>()
    private val compressed = object : LruCache<Int, ByteArray>(TRICKPLAY_COMPRESSED_CACHE_BYTES) {
        override fun sizeOf(key: Int, value: ByteArray): Int = value.size
    }

    private val prefetchExecutor: ExecutorService = Executors.newSingleThreadExecutor { task -> Thread(task, "trickplay-prefetch").apply { priority = Thread.MIN_PRIORITY } }
    private val prefetchDispatcher: CoroutineDispatcher = prefetchExecutor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var prefetchJob: Job? = null
    private var aimedTile: Int = -1
    private var aimedLimited: Boolean = false
    private var scrubTile: Int = -1
    private var cameFromTile: Int? = null

    @Volatile
    private var keptDecoded: List<Int> = emptyList()

    /** How big the preview is drawn over a bar [barWidth] dp wide on a screen of [screenScale] pixels to the dp. */
    fun previewSize(barWidth: Float, screenScale: Float): TrickplayPreviewSize = track.previewSize(barWidth, screenScale)

    /** Which tile image holds the thumbnail for [positionMs]. */
    fun tileAt(positionMs: Long): Int = track.locate(positionMs).tileIndex

    /**
     * Points the prefetching at [positionMs]: nothing changes while the position stays in the same tile on the same kind of
     * connection, otherwise the tiles still to fetch are ordered again from here.
     */
    fun aim(positionMs: Long, dataLimited: Boolean) {
        val tileIndex: Int = tileAt(positionMs)

        if (tileIndex == aimedTile && dataLimited == aimedLimited) return

        aimedTile = tileIndex
        aimedLimited = dataLimited
        prefetchJob?.cancel()
        prefetchJob = scope.launch(prefetchDispatcher) {
            for (index in track.prefetchTiles(positionMs, dataLimited)) bytes(index, prefetchDispatcher)
        }
    }

    /** Stops the fetching and drops everything held. */
    fun close() {
        scope.cancel()
        prefetchExecutor.shutdownNow()
        compressed.evictAll()
        decoded.clear()
    }

    /** The thumbnail for [positionMs], or null while its tile is loading and when it can't be had. */
    suspend fun thumbnail(positionMs: Long): ImageBitmap? {
        val at = track.locate(positionMs)

        if (at.tileIndex != scrubTile) {
            cameFromTile = scrubTile.takeIf { it >= 0 }
            scrubTile = at.tileIndex
        }

        val wanted: List<Int> = track.decodedTiles(scrubTile, cameFromTile)

        keptDecoded = wanted
        decoded.keys.retainAll(wanted.toSet())
        wanted.filter { it != scrubTile }.forEach { next -> scope.launch(Dispatchers.Default) { tile(next) } }

        val tile: Bitmap = tile(at.tileIndex) ?: return null
        val x: Int = at.column * track.thumbWidth
        val y: Int = at.row * track.thumbHeight

        if (x + track.thumbWidth > tile.width || y + track.thumbHeight > tile.height) return null
        return Bitmap.createBitmap(tile, x, y, track.thumbWidth, track.thumbHeight).asImageBitmap()
    }

    private suspend fun tile(index: Int): Bitmap? {
        decoded[index]?.let { return it }

        val job: Deferred<Bitmap?> = synchronized(lock) {
            decoding.getOrPut(index) { scope.async(Dispatchers.Default) { decode(index) } }
        }

        return job.await()
    }

    private suspend fun decode(index: Int): Bitmap? {
        try {
            val data: ByteArray = bytes(index, Dispatchers.IO) ?: return null
            val bitmap: Bitmap? = BitmapFactory.decodeByteArray(data, 0, data.size)

            if (bitmap == null) {
                failed[index] = SystemClock.elapsedRealtime()
                compressed.remove(index)
            } else if (index in keptDecoded) {
                decoded[index] = bitmap
            }

            return bitmap
        } finally {
            synchronized(lock) { decoding.remove(index) }
        }
    }

    private suspend fun bytes(index: Int, dispatcher: CoroutineDispatcher): ByteArray? {
        compressed.get(index)?.let { return it }

        val failedAt: Long? = failed[index]

        if (failedAt != null) {
            if (!track.mayRetryTile(failedAt, SystemClock.elapsedRealtime())) return null

            failed.remove(index)
        }

        val job: Deferred<ByteArray?> = synchronized(lock) {
            fetching.getOrPut(index) { scope.async(dispatcher) { fetch(index) } }
        }

        return job.await()
    }

    private fun fetch(index: Int): ByteArray? {
        try {
            val data: ByteArray? = download(track.tileUrl(index))

            if (data == null) failed[index] = SystemClock.elapsedRealtime() else compressed.put(index, data)
            return data
        } finally {
            synchronized(lock) { fetching.remove(index) }
        }
    }

    private fun download(address: String): ByteArray? {
        var connection: HttpURLConnection? = null

        return try {
            val opened = URL(address).openConnection()

            connection = opened as? HttpURLConnection
            opened.connectTimeout = TILE_CONNECT_TIMEOUT_MS
            opened.readTimeout = TILE_READ_TIMEOUT_MS
            headers.forEach { (name, value) -> opened.setRequestProperty(name, value) }

            if (connection == null || connection.responseCode == HttpURLConnection.HTTP_OK) opened.getInputStream().use { it.readBytes() } else null
        } catch (_: IOException) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
