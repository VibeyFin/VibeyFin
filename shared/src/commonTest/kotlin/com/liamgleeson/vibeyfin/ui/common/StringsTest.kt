package com.liamgleeson.vibeyfin.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class StringsTest {
    @Test
    fun cardLabelNamesTheTitleAndYear() {
        assertEquals("Heat, 1995", Strings.cardLabel("Heat", 1995))
    }

    @Test
    fun cardLabelLeavesOutAMissingYear() {
        assertEquals("Movies", Strings.cardLabel("Movies", null))
    }

    @Test
    fun resumeSaysHowMuchIsLeftInMinutesOrHoursAndMinutes() {
        assertEquals("Resume · 38m left", Strings.resumeTimeLeft(38 * 60_000L))
        assertEquals("Resume · 1h 52m left", Strings.resumeTimeLeft((112 * 60_000L)))
        assertEquals("Resume · 2h left", Strings.resumeTimeLeft(120 * 60_000L))
    }

    @Test
    fun resumeRoundsToTheNearestMinuteAndNeverSaysZero() {
        assertEquals("Resume · 49m left", Strings.resumeTimeLeft(48 * 60_000L + 48_000))
        assertEquals("Resume · 1m left", Strings.resumeTimeLeft(10_000L))
        assertEquals("Resume · 1m left", Strings.resumeTimeLeft(-5_000L))
    }
}
