package com.liamgleeson.vibeyfin.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerDiagnosticsTest {
    @Test
    fun codecNamesAreShort() {
        assertEquals("h264", codecName("video/avc"))
        assertEquals("hevc", codecName("video/hevc"))
        assertEquals("aac", codecName("audio/mp4a-latm"))
        assertEquals("eac3 atmos", codecName("audio/eac3-joc"))
    }

    @Test
    fun anUnknownTypeShowsItsSubtype() {
        assertEquals("whatever", codecName("audio/x-whatever"))
    }

    @Test
    fun noTypeIsNoName() {
        assertNull(codecName(null))
        assertNull(codecName("garbage"))
    }

    @Test
    fun notKnownValuesBecomeNull() {
        assertNull(knownKbps(-1))
        assertNull(knownKbps(0L))
        assertNull(knownFrameRate(-1f))
        assertEquals(4500, knownKbps(4_500_000))
        assertEquals(23.976f, knownFrameRate(23.976f))
    }
}
