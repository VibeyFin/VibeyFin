package app.vibeyfin.domain

import app.vibeyfin.data.api.TrickplayInfoDto
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** The trickplay width we ask for: sharp enough for a scrub preview, small enough to download fast. */
internal const val TRICKPLAY_TARGET_WIDTH = 320

/** How long a tile image that failed to load counts as missing before the players try it again (a network blip passes). */
internal const val TRICKPLAY_TILE_RETRY_MS = 10_000L

internal const val TRICKPLAY_PREVIEW_MIN_WIDTH = 160f

internal const val TRICKPLAY_PREVIEW_BAR_SHARE = 0.25f

/** How much memory the players keep of fetched tile images, as the compressed bytes; the decoded tiles are kept to the one scrubbed and the next. */
const val TRICKPLAY_COMPRESSED_CACHE_BYTES = 48 * 1024 * 1024

/** How many tiles ahead and behind the current one are fetched when the connection is limited. */
internal const val TRICKPLAY_LIMITED_REACH = 1

/**
 * The thumbnails the server cut from one media source: [thumbnailCount] pictures of
 * [thumbWidth] by [thumbHeight], one every [intervalMs], packed into tile images of
 * [tileWidth] by [tileHeight] thumbnails, row by row.
 */
@Serializable
data class TrickplayTrack(
    val mediaSourceId: String,
    /** The width the server names this set by; it is part of the tile's path. */
    val width: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbWidth: Int,
    val thumbHeight: Int,
    val thumbnailCount: Int,
    val intervalMs: Long,
    /** The server's address of this set, `https://host/Videos/{itemId}/Trickplay/{width}`; set when the item is known. */
    val tilesUrl: String = "",
    /** The addresses of the tile images on the device when the item plays from a download, one per tile; empty when they are on the server. */
    val tileUris: List<String> = emptyList(),
) {
    /** How many tile images the thumbnails fill. */
    val tileCount: Int get() = (thumbnailCount + tileWidth * tileHeight - 1) / (tileWidth * tileHeight)

    /**
     * The full address of tile image [tileIndex]; fetch it with the stream's headers. It names the media
     * source, which the server needs to find the tiles of a version that is not the item's default.
     * A track on the device names its own files instead.
     */
    fun tileUrl(tileIndex: Int): String = tileUris.getOrNull(tileIndex) ?: "$tilesUrl/$tileIndex.jpg?mediaSourceId=$mediaSourceId"

    /** Where the thumbnail for [positionMs] sits: which tile image, and which cell of it. */
    fun locate(positionMs: Long): TrickplayThumb {
        val index = (positionMs / intervalMs).coerceIn(0L, (thumbnailCount - 1).toLong().coerceAtLeast(0L)).toInt()
        val perTile = tileWidth * tileHeight
        val inTile = index % perTile
        return TrickplayThumb(tileIndex = index / perTile, column = inTile % tileWidth, row = inTile / tileWidth)
    }

    /**
     * Whether a tile image that failed to load at [failedAtMs] may be fetched again at [nowMs], both on one
     * monotonic clock. Until then the players show the timestamp alone instead of asking again on every move.
     */
    fun mayRetryTile(failedAtMs: Long, nowMs: Long): Boolean = nowMs - failedAtMs >= TRICKPLAY_TILE_RETRY_MS

    /** The path on the server of tile image [tileIndex]; it needs the same authorisation as any request. */
    fun tilePath(itemId: String, tileIndex: Int): String = "/Videos/$itemId/Trickplay/$width/$tileIndex.jpg?mediaSourceId=$mediaSourceId"

    /** Every tile image, the one holding [positionMs] first, then its neighbours outward (after, before, after...), so the nearest come first. */
    fun prefetchOrder(positionMs: Long): List<Int> {
        if (tileCount == 0) return emptyList()
        val current: Int = locate(positionMs).tileIndex
        val order: MutableList<Int> = mutableListOf(current)

        for (distance in 1 until tileCount) {
            if (current + distance < tileCount) order += current + distance
            if (current - distance >= 0) order += current - distance
        }

        return order
    }

    /**
     * The tile images to fetch ahead of use at [positionMs], in order: the whole film on a normal connection, and on a limited one
     * (mobile data, Low Data Mode, data saver) only the current tile and its neighbours.
     */
    fun prefetchTiles(positionMs: Long, dataLimited: Boolean): List<Int> {
        val order: List<Int> = prefetchOrder(positionMs)

        return if (dataLimited) order.filter { abs(it - order.first()) <= TRICKPLAY_LIMITED_REACH } else order
    }

    /**
     * The tile images worth keeping decoded while [tileIndex] is scrubbed: it and the next one in the direction the scrub moves,
     * judged from the tile [previousTileIndex] it came from (forward when unknown or unchanged).
     */
    fun decodedTiles(tileIndex: Int, previousTileIndex: Int?): List<Int> {
        val step: Int = if (previousTileIndex != null && previousTileIndex > tileIndex) -1 else 1

        return listOf(tileIndex, tileIndex + step).filter { it in 0 until tileCount }
    }

    fun previewSize(barWidth: Float, screenScale: Float): TrickplayPreviewSize {
        val maxSharpWidth: Float = maxOf(thumbWidth / screenScale, TRICKPLAY_PREVIEW_MIN_WIDTH)
        val previewWidth: Float = (barWidth * TRICKPLAY_PREVIEW_BAR_SHARE).coerceIn(TRICKPLAY_PREVIEW_MIN_WIDTH, maxSharpWidth)

        return TrickplayPreviewSize(width = previewWidth, height = previewWidth * thumbHeight / thumbWidth)
    }
}

data class TrickplayPreviewSize(val width: Float, val height: Float)

/** One thumbnail's place: the [tileIndex]th tile image, at [column] and [row] counted from its top left. */
data class TrickplayThumb(val tileIndex: Int, val column: Int, val row: Int)

internal fun Collection<TrickplayInfoDto>.toTrickplayTrack(mediaSourceId: String): TrickplayTrack? {
    val info = filter { it.interval > 0 && it.tileWidth > 0 && it.tileHeight > 0 && it.width > 0 && it.height > 0 }
        .minByOrNull { abs(it.width - TRICKPLAY_TARGET_WIDTH) } ?: return null
    return TrickplayTrack(
        mediaSourceId = mediaSourceId,
        width = info.width,
        tileWidth = info.tileWidth,
        tileHeight = info.tileHeight,
        thumbWidth = info.width,
        thumbHeight = info.height,
        thumbnailCount = info.thumbnailCount,
        intervalMs = info.interval.toLong(),
    )
}
