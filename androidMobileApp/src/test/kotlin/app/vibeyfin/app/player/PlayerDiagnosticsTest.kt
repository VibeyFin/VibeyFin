package app.vibeyfin.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerDiagnosticsTest {
    @Test
    fun notKnownValuesBecomeNull() {
        assertNull(knownKbps(-1))
        assertNull(knownKbps(0L))
        assertNull(knownFrameRate(-1f))
        assertEquals(4500, knownKbps(4_500_000))
        assertEquals(23.976f, knownFrameRate(23.976f))
    }
}
