package app.vibeyfin.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceholderCountTest {
    @Test
    fun fillsTheVisibleWidthAndOnePeekingTile() {
        assertEquals(3, PlaceholderCount.forRow(visibleWidth = 360f, tileWidth = 120f, spacing = 12f, known = null))
    }

    @Test
    fun aRowThatExactlyFitsStillPeeks() {
        assertEquals(4, PlaceholderCount.forRow(visibleWidth = 384f, tileWidth = 120f, spacing = 12f, known = null))
    }

    @Test
    fun aKnownCountWinsOverTheWidth() {
        assertEquals(7, PlaceholderCount.forRow(visibleWidth = 360f, tileWidth = 120f, spacing = 12f, known = 7))
    }

    @Test
    fun anEmptyKnownRowHasNoPlaceholders() {
        assertEquals(0, PlaceholderCount.forRow(visibleWidth = 360f, tileWidth = 120f, spacing = 12f, known = 0))
    }

    @Test
    fun aWidthWithNoRoomStillGetsOneTile() {
        assertEquals(1, PlaceholderCount.forRow(visibleWidth = 50f, tileWidth = 120f, spacing = 12f, known = null))
    }
}
