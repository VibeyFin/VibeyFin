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
    fun timeLeftIsShortAndNeverUnderAMinute() {
        assertEquals("38m left", Strings.timeLeftShort(38 * 60_000L))
        assertEquals("1h 52m left", Strings.timeLeftShort(112 * 60_000L))
        assertEquals("1m left", Strings.timeLeftShort(0))
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

    @Test
    fun theTimeLeftInThePlayerStartsWithATrueMinusNotAHyphen() {
        assertEquals("\u22121:12", Strings.timeLeft(positionMs = 48_000, durationMs = 120_000))
        assertEquals("\u22121:24:30", Strings.timeLeft(positionMs = 0, durationMs = (84 * 60 + 30) * 1000L))
        // Past the end it stops at zero rather than counting up.
        assertEquals("\u22120:00", Strings.timeLeft(positionMs = 130_000, durationMs = 120_000))
    }

    @Test
    fun stillWatchingAfterNamesTheEpisodeCount() {
        assertEquals("After 4 episodes", Strings.stillWatchingAfter(4))
    }
}
