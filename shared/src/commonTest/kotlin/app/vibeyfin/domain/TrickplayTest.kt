package app.vibeyfin.domain

import app.vibeyfin.data.api.TrickplayInfoDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrickplayTest {
    /** 250 thumbnails, one every 10 seconds, in tiles of 10 columns by 10 rows (100 thumbnails a tile). */
    private val track = TrickplayTrack(
        mediaSourceId = "ms-1",
        width = 320,
        tileWidth = 10,
        tileHeight = 10,
        thumbWidth = 320,
        thumbHeight = 180,
        thumbnailCount = 250,
        intervalMs = 10_000L,
    )

    @Test
    fun aTileThatFailedIsTriedAgainAfterAShortWhile() {
        assertFalse(track.mayRetryTile(failedAtMs = 1_000, nowMs = 1_000))
        assertFalse(track.mayRetryTile(failedAtMs = 1_000, nowMs = 1_000 + TRICKPLAY_TILE_RETRY_MS - 1))
        assertTrue(track.mayRetryTile(failedAtMs = 1_000, nowMs = 1_000 + TRICKPLAY_TILE_RETRY_MS))
    }

    @Test
    fun anEntryWithMissingSizesIsSkipped() {
        val partial = TrickplayInfoDto(width = 320, tileWidth = 10, tileHeight = 10, thumbnailCount = 250, interval = 10_000)

        assertNull(listOf(partial).toTrickplayTrack("ms-1"))
        assertNull(listOf(TrickplayInfoDto()).toTrickplayTrack("ms-1"))
    }

    @Test
    fun locatesThumbnailInsideTheRightTile() {
        // Thumbnail 0: first cell of the first tile.
        assertEquals(TrickplayThumb(tileIndex = 0, column = 0, row = 0), track.locate(0L))
        // 125 000 ms is thumbnail 12: still the first tile, column 2 of row 1.
        assertEquals(TrickplayThumb(tileIndex = 0, column = 2, row = 1), track.locate(125_000L))
        // 1 250 000 ms is thumbnail 125: tile 1, cell 25 of it, so column 5 of row 2.
        assertEquals(TrickplayThumb(tileIndex = 1, column = 5, row = 2), track.locate(1_250_000L))
    }

    @Test
    fun aPositionPastTheEndClampsToTheLastThumbnail() {
        // The last thumbnail is 249: tile 2, cell 49, so column 9 of row 4.
        assertEquals(TrickplayThumb(tileIndex = 2, column = 9, row = 4), track.locate(9_999_999L))
    }

    @Test
    fun aNegativePositionClampsToTheFirstThumbnail() {
        assertEquals(TrickplayThumb(tileIndex = 0, column = 0, row = 0), track.locate(-5_000L))
    }

    @Test
    fun tileUrlHangsOffTheSetsAddress() {
        assertEquals(
            "http://h/Videos/m1/Trickplay/320/2.jpg?mediaSourceId=ms-2",
            track.copy(mediaSourceId = "ms-2", tilesUrl = "http://h/Videos/m1/Trickplay/320").tileUrl(2),
        )
    }

    @Test
    fun tilePathNamesTheWidthAndTheTile() {
        assertEquals("/Videos/m1/Trickplay/320/2.jpg", track.tilePath("m1", 2))
    }
}
