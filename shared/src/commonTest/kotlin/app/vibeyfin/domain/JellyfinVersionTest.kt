package app.vibeyfin.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JellyfinVersionTest {
    @Test
    fun parsesThreePartVersions() {
        assertEquals(JellyfinVersion(10, 10, 7), JellyfinVersion.parse("10.10.7"))
        assertEquals(JellyfinVersion(12, 2, 0), JellyfinVersion.parse("12.2.0"))
    }

    @Test
    fun ignoresAFourthPartAndASuffix() {
        assertEquals(JellyfinVersion(10, 8, 13), JellyfinVersion.parse("10.8.13.0"))
        assertEquals(JellyfinVersion(10, 11, 0), JellyfinVersion.parse("10.11.0-rc1"))
    }

    @Test
    fun aMissingPatchCountsAsZero() {
        assertEquals(JellyfinVersion(10, 10, 0), JellyfinVersion.parse("10.10"))
    }

    @Test
    fun rejectsAnythingElse() {
        for (text in listOf("", "10", "banana", "v10.10.7", "10..7", ".10.7")) {
            assertNull(JellyfinVersion.parse(text), "'$text' should not parse")
        }
    }

    @Test
    fun comparesNumericallyNotAsText() {
        assertTrue(JellyfinVersion(10, 9, 11) < JellyfinVersion.MINIMUM)
        assertTrue(JellyfinVersion(10, 10, 0) >= JellyfinVersion.MINIMUM)
        assertTrue(JellyfinVersion(10, 9, 0) < JellyfinVersion(10, 10, 0))
        assertTrue(JellyfinVersion(11, 0, 0) > JellyfinVersion(10, 99, 99))
        assertTrue(JellyfinVersion(10, 10, 7) > JellyfinVersion(10, 10, 6))
    }

    @Test
    fun printsAsThreeParts() {
        assertEquals("10.10.7", JellyfinVersion(10, 10, 7).toString())
    }
}
