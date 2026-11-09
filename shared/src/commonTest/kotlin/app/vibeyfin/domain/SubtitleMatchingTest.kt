package app.vibeyfin.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubtitleMatchingTest {
    private fun result(name: String) = RemoteSubtitle("id", name, "OpenSubtitles", "srt", null, isHashMatch = false)

    private fun track(title: String?, displayTitle: String? = null) = StreamTrack(displayTitle = displayTitle, title = title, language = "eng", external = true)

    @Test
    fun aTrackWhoseTitleHoldsTheReleaseNameCountsAsTheResult() {
        assertTrue(result("Film.2020.1080p.BluRay.x264-GRP.srt").namedBy(track("film 2020 1080p bluray x264 grp")))
    }

    @Test
    fun theDisplayTitleCountsToo() {
        assertTrue(result("Film 2020 BluRay").namedBy(track(title = null, displayTitle = "Film.2020.BluRay - English - SRT - External")))
    }

    @Test
    fun aTrackWithAnotherReleaseNameIsNotTheResult() {
        assertFalse(result("Film.2020.1080p.BluRay.x264-GRP.srt").namedBy(track("Film 2020 WEB-DL")))
    }

    @Test
    fun aGenericTrackTitleMatchesNothing() {
        assertFalse(result("Film.2020.1080p.BluRay.srt").namedBy(track(title = null, displayTitle = "English - SRT - External")))
    }

    @Test
    fun aNameOfOneWordIsTooLittleToGoOn() {
        assertFalse(result("Film").namedBy(track("Film")))
    }
}
