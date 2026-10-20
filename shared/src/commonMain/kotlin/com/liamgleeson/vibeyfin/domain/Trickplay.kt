package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.api.TrickplayInfoDto
import kotlin.math.abs

/** The trickplay width we ask for: sharp enough for a scrub preview, small enough to download fast. */
internal const val TRICKPLAY_TARGET_WIDTH = 320

/**
 * The thumbnails the server cut from one media source: [thumbnailCount] pictures of
 * [thumbWidth] by [thumbHeight], one every [intervalMs], packed into tile images of
 * [tileWidth] by [tileHeight] thumbnails, row by row.
 */
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
) {
    /**
     * The full address of tile image [tileIndex]; fetch it with the stream's headers. It names the media
     * source, which the server needs to find the tiles of a version that is not the item's default.
     */
    fun tileUrl(tileIndex: Int): String = "$tilesUrl/$tileIndex.jpg?mediaSourceId=$mediaSourceId"

    /** Where the thumbnail for [positionMs] sits: which tile image, and which cell of it. */
    fun locate(positionMs: Long): TrickplayThumb {
        val index = (positionMs / intervalMs).coerceIn(0L, (thumbnailCount - 1).toLong().coerceAtLeast(0L)).toInt()
        val perTile = tileWidth * tileHeight
        val inTile = index % perTile
        return TrickplayThumb(tileIndex = index / perTile, column = inTile % tileWidth, row = inTile / tileWidth)
    }

    /** The path on the server of tile image [tileIndex]; it needs the same authorisation as any request. */
    fun tilePath(itemId: String, tileIndex: Int): String = "/Videos/$itemId/Trickplay/$width/$tileIndex.jpg"
}

/** One thumbnail's place: the [tileIndex]th tile image, at [column] and [row] counted from its top left. */
data class TrickplayThumb(val tileIndex: Int, val column: Int, val row: Int)

internal fun Collection<TrickplayInfoDto>.toTrickplayTrack(mediaSourceId: String): TrickplayTrack? {
    val info = filter { it.interval > 0 && it.tileWidth > 0 && it.tileHeight > 0 }
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
