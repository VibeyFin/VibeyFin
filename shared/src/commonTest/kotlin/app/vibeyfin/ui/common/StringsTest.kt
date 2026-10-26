package app.vibeyfin.ui.common

import app.vibeyfin.domain.PlayMethod
import app.vibeyfin.domain.SegmentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun statsMethodNamesEachWayOfPlaying() {
        assertEquals("Direct play", Strings.statsMethod(PlayMethod.DirectPlay))
        assertEquals("Direct stream", Strings.statsMethod(PlayMethod.DirectStream))
        assertEquals("Transcode", Strings.statsMethod(PlayMethod.Transcode))
    }

    @Test
    fun statsShowADashForAMissingValue() {
        assertEquals("–", Strings.statsUnavailable)
    }

    @Test
    fun statsValuesCarryTheirUnits() {
        assertEquals("1920 × 1080", Strings.statsResolution(1920, 1080))
        assertEquals("24 fps", Strings.statsFrameRate(24f))
        assertEquals("23.98 fps", Strings.statsFrameRate(23.976f))
        assertEquals("850 kbps", Strings.statsBitrate(850))
        assertEquals("4.5 Mbps", Strings.statsBitrate(4500))
        assertEquals("0.0 s", Strings.statsBuffer(-5))
        assertEquals("12.3 s", Strings.statsBuffer(12_345))
    }

    @Test
    fun skipNamesWhatIsSkipped() {
        assertEquals("Skip intro", Strings.skip(SegmentType.Intro))
        assertEquals("Skip credits", Strings.skip(SegmentType.Outro))
        assertEquals("Skip recap", Strings.skip(SegmentType.Recap))
        assertEquals("Skip preview", Strings.skip(SegmentType.Preview))
        assertEquals("Skip ad", Strings.skip(SegmentType.Commercial))
    }

    @Test
    fun codecNamesAreShortFromAnAndroidMimeType() {
        assertEquals("h264", Strings.codecName("video/avc"))
        assertEquals("hevc", Strings.codecName("video/hevc"))
        assertEquals("aac", Strings.codecName("audio/mp4a-latm"))
        assertEquals("eac3 atmos", Strings.codecName("audio/eac3-joc"))
        assertEquals("dolby vision", Strings.codecName("video/dolby-vision"))
    }

    @Test
    fun codecNamesAreTheSameFromAnAppleFourCC() {
        assertEquals("h264", Strings.codecName("avc1"))
        assertEquals("hevc", Strings.codecName("hvc1"))
        assertEquals("hevc", Strings.codecName("hev1"))
        assertEquals("dolby vision", Strings.codecName("dvh1"))
        assertEquals("aac", Strings.codecName("mp4a"))
        assertEquals("eac3", Strings.codecName("ec-3"))
        assertEquals("mp3", Strings.codecName(".mp3"))
    }

    @Test
    fun anUnknownCodecShowsWhatThePlayerSaid() {
        assertEquals("whatever", Strings.codecName("audio/x-whatever"))
        assertEquals("abcd", Strings.codecName("ABCD "))
    }

    @Test
    fun noCodecIsNoName() {
        assertNull(Strings.codecName(null))
        assertNull(Strings.codecName("  "))
    }

    @Test
    fun aLabelAndItsValueAreOneSpokenPhrase() {
        assertEquals("Bitrate, 4.5 Mbps", Strings.labelled("Bitrate", "4.5 Mbps"))
    }

    @Test
    fun skipLabelsNameTheSeconds() {
        assertEquals("10 seconds", Strings.skipSeconds(10))
        assertEquals("Back 10 seconds", Strings.skipBackLabel(10))
        assertEquals("Forward 30 seconds", Strings.skipForwardLabel(30))
        assertEquals("30s", Strings.seekFlash(30))
        assertEquals("Skip back", Strings.skipBack)
        assertEquals("Skip forward", Strings.skipForward)
    }

    @Test
    fun theMetaLineJoinsWhatIsThere() {
        assertEquals("Series  ·  S1:E2  ·  4K", Strings.metaLine("Series  ·  S1:E2", "4K"))
        assertEquals("Series  ·  S1:E2", Strings.metaLine("Series  ·  S1:E2", null))
        assertEquals("Series  ·  S1:E2", Strings.metaLine("Series  ·  S1:E2", ""))
        assertEquals("4K", Strings.metaLine(null, "4K"))
        assertNull(Strings.metaLine(null, null))
        assertNull(Strings.metaLine("", " "))
    }

    @Test
    fun anEpisodeNotificationNamesItsShowWhenTheServerSaysWhichOne() {
        assertEquals("New episode of Saga Show", Strings.addedEpisodeNotificationTitle("Saga Show"))
        assertEquals("New episode", Strings.addedEpisodeNotificationTitle(null))
        assertEquals("New episode", Strings.addedEpisodeNotificationTitle(" "))
        assertEquals("New movie", Strings.addedMovieNotificationTitle)
    }

    @Test
    fun aNotificationBodyNamesTheProfileOnlyWhenGiven() {
        assertEquals("Pilot", Strings.addedTitleNotificationBody("Pilot", null))
        assertEquals("Pilot, for Anna", Strings.addedTitleNotificationBody("Pilot", "Anna"))
    }
}
