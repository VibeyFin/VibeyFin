package app.vibeyfin.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SubtitleStyleTest {
    @Test
    fun `following the system returns the user style from the OS caption settings`() {
        // The explicit values are carried but flagged unused; the extension reads CaptioningManager.
        assertTrue(subtitleStyle(followSystem = true, size = 1, color = 0, background = 1).followSystem)
        assertEquals(false, subtitleStyle(followSystem = false, size = 1, color = 0, background = 1).followSystem)
    }

    @Test
    fun `size, colour and background map to distinct values`() {
        val sizes = (0..3).map { subtitleStyle(false, it, 0, 0).sizeFraction }
        assertEquals(sizes.sorted(), sizes)
        assertEquals(4, sizes.toSet().size)
        assertEquals(3, (0..2).map { subtitleStyle(false, 1, it, 0).foreground }.toSet().size)

        val none = subtitleStyle(false, 1, 0, 0)
        val shadow = subtitleStyle(false, 1, 0, 1)
        val box = subtitleStyle(false, 1, 0, 2)
        assertEquals(SUBTITLE_TRANSPARENT, none.background)
        assertEquals(SUBTITLE_EDGE_NONE, none.edgeType)
        assertEquals(SUBTITLE_EDGE_DROP_SHADOW, shadow.edgeType)
        assertEquals(SUBTITLE_BOX, box.background)
        assertNotEquals(shadow, box)
    }
}
