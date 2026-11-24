package app.vibeyfin.domain

import app.vibeyfin.data.api.TrickplayInfoDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrickplayTest {
    // 250 thumbnails, one every 10 seconds, in tiles of 10 columns by 10 rows (100 thumbnails a tile).
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
        assertEquals("/Videos/m1/Trickplay/320/2.jpg?mediaSourceId=ms-1", track.tilePath("m1", 2))
    }

    @Test
    fun aSmallTileSetStaysAtTheDesignWidthOnEveryScreen() {
        assertEquals(TrickplayPreviewSize(width = 160f, height = 90f), track.previewSize(barWidth = 800f, screenScale = 3f))
        assertEquals(TrickplayPreviewSize(width = 160f, height = 90f), track.previewSize(barWidth = 1_100f, screenScale = 2f))
    }

    @Test
    fun aLargerTileSetGrowsToAQuarterOfTheBar() {
        val large = track.copy(width = 640, thumbWidth = 640, thumbHeight = 360)

        assertEquals(TrickplayPreviewSize(width = 275f, height = 154.6875f), large.previewSize(barWidth = 1_100f, screenScale = 2f))
    }

    @Test
    fun thePreviewNeverDrawsWiderThanTheSourcePixelsAllow() {
        val large = track.copy(width = 640, thumbWidth = 640, thumbHeight = 360)

        assertEquals(TrickplayPreviewSize(width = 320f, height = 180f), large.previewSize(barWidth = 2_000f, screenScale = 2f))
    }

    @Test
    fun aNarrowBarNeverShrinksThePreviewBelowTheDesignWidth() {
        assertEquals(160f, track.previewSize(barWidth = 300f, screenScale = 3f).width)
    }

    @Test
    fun theHeightFollowsTheThumbnailsOwnAspect() {
        val fourByThree = track.copy(thumbHeight = 240)

        assertEquals(120f, fourByThree.previewSize(barWidth = 800f, screenScale = 3f).height)
    }

    @Test
    fun theTileCountRoundsUpToWholeTiles() {
        assertEquals(3, track.tileCount)
        assertEquals(1, track.copy(thumbnailCount = 100).tileCount)
        assertEquals(2, track.copy(thumbnailCount = 101).tileCount)
        assertEquals(0, track.copy(thumbnailCount = 0).tileCount)
    }

    @Test
    fun prefetchStartsAtTheCurrentTileAndWorksOutwardsAlternating() {
        val long = track.copy(thumbnailCount = 700)

        assertEquals(listOf(3, 4, 2, 5, 1, 6, 0), long.prefetchOrder(positionMs = 3_500_000L))
    }

    @Test
    fun prefetchContinuesOnTheOtherSideWhenOneRunsOut() {
        assertEquals(listOf(0, 1, 2), track.prefetchOrder(positionMs = 0L))
        assertEquals(listOf(2, 1, 0), track.prefetchOrder(positionMs = 9_999_999L))
        assertEquals(listOf(1, 2, 0), track.prefetchOrder(positionMs = 1_250_000L))
    }

    @Test
    fun prefetchOrderListsEveryTileOnce() {
        val long = track.copy(thumbnailCount = 1_234)

        assertEquals((0 until long.tileCount).toList(), long.prefetchOrder(positionMs = 5_000_000L).sorted())
    }

    @Test
    fun aTrackWithoutThumbnailsPrefetchesNothing() {
        assertEquals(emptyList(), track.copy(thumbnailCount = 0).prefetchOrder(positionMs = 0L))
    }

    @Test
    fun onANormalConnectionTheWholeFilmIsPrefetched() {
        assertEquals(track.prefetchOrder(1_250_000L), track.prefetchTiles(1_250_000L, dataLimited = false))
    }

    @Test
    fun onALimitedConnectionOnlyTheCurrentAndNeighbouringTilesAreFetched() {
        val long = track.copy(thumbnailCount = 700)

        assertEquals(listOf(3, 4, 2), long.prefetchTiles(positionMs = 3_500_000L, dataLimited = true))
        assertEquals(listOf(0, 1), long.prefetchTiles(positionMs = 0L, dataLimited = true))
        assertEquals(listOf(6, 5), long.prefetchTiles(positionMs = 9_999_999L, dataLimited = true))
    }

    @Test
    fun theDecodedTilesAreTheOneBeingScrubbedAndTheNextInTheDirectionOfTravel() {
        assertEquals(listOf(1, 2), track.decodedTiles(tileIndex = 1, previousTileIndex = 0))
        assertEquals(listOf(1, 0), track.decodedTiles(tileIndex = 1, previousTileIndex = 2))
        assertEquals(listOf(1, 2), track.decodedTiles(tileIndex = 1, previousTileIndex = null))
        assertEquals(listOf(1, 2), track.decodedTiles(tileIndex = 1, previousTileIndex = 1))
    }

    @Test
    fun theDecodedTilesStayInsideTheTileRange() {
        assertEquals(listOf(2), track.decodedTiles(tileIndex = 2, previousTileIndex = 1))
        assertEquals(listOf(0), track.decodedTiles(tileIndex = 0, previousTileIndex = 1))
    }

    @Test
    fun aTrackOnTheDeviceHasATileAddressForEveryTile() {
        val offline = track.copy(tilesUrl = "file:///d/0.jpg", tileUris = listOf("file:///d/0.jpg", "file:///d/1.jpg", "file:///d/2.jpg"))

        assertEquals("file:///d/2.jpg", offline.tileUrl(2))
    }
}
