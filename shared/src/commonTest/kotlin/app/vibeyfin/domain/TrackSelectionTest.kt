package app.vibeyfin.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackSelectionTest {
    private val audio = listOf(
        PlaybackTrack(1, "English", false, language = "eng", default = true),
        PlaybackTrack(2, "Deutsch", false, language = "deu"),
    )
    private val subtitles = listOf(
        PlaybackTrack(3, "English", false, language = "eng"),
        PlaybackTrack(4, "Deutsch", false, language = "ger"),
        PlaybackTrack(5, "English forced", false, language = "eng", forced = true),
    )

    private fun choose(prefs: LanguagePrefs) = chooseTracks(audio, subtitles, prefs)

    @Test
    fun withoutAnAudioLanguageTheDefaultTrackPlays() {
        assertEquals(1, choose(LanguagePrefs()).audioIndex)
    }

    @Test
    fun anAudioLanguageWinsOverTheDefaultTrackByEitherCode() {
        assertEquals(2, choose(LanguagePrefs(audioLanguage = "ger")).audioIndex)
        assertEquals(2, choose(LanguagePrefs(audioLanguage = "deu")).audioIndex)
    }

    @Test
    fun anAudioLanguageTheVideoDoesNotHaveFallsBackToTheDefaultTrack() {
        assertEquals(1, choose(LanguagePrefs(audioLanguage = "fre")).audioIndex)
    }

    @Test
    fun alwaysShowsTheFullSubtitleInThePreferredLanguage() {
        assertEquals(3, choose(LanguagePrefs(audioLanguage = "ger", subtitleLanguage = "eng", subtitleMode = SubtitleMode.Always)).subtitleIndex)
        assertEquals(4, choose(LanguagePrefs(subtitleLanguage = "deu", subtitleMode = SubtitleMode.Always)).subtitleIndex)
    }

    @Test
    fun alwaysWithoutASubtitleLanguageShowsAnyLanguage() {
        assertEquals(3, choose(LanguagePrefs(audioLanguage = "ger", subtitleMode = SubtitleMode.Always)).subtitleIndex)
    }

    @Test
    fun noneShowsNothingNotEvenForcedSubtitles() {
        assertEquals(null, choose(LanguagePrefs(subtitleLanguage = "eng", subtitleMode = SubtitleMode.None)).subtitleIndex)
    }

    @Test
    fun onlyForcedShowsTheForcedSubtitleInThePreferredLanguage() {
        assertEquals(5, choose(LanguagePrefs(subtitleLanguage = "eng", subtitleMode = SubtitleMode.OnlyForced)).subtitleIndex)
        assertEquals(null, choose(LanguagePrefs(subtitleLanguage = "ger", subtitleMode = SubtitleMode.OnlyForced)).subtitleIndex)
    }

    @Test
    fun smartShowsSubtitlesOnlyWhenTheAudioIsNotInTheirLanguage() {
        assertEquals(3, choose(LanguagePrefs(audioLanguage = "ger", subtitleLanguage = "eng", subtitleMode = SubtitleMode.Smart)).subtitleIndex)
        assertEquals(5, choose(LanguagePrefs(audioLanguage = "eng", subtitleLanguage = "eng", subtitleMode = SubtitleMode.Smart)).subtitleIndex, "only the forced one")
    }

    @Test
    fun defaultShowsTheDownloadedSubtitleInThePreferredLanguage() {
        assertEquals(4, choose(LanguagePrefs(subtitleLanguage = "ger")).subtitleIndex)
    }

    @Test
    fun aVideoWithoutTracksPicksNothing() {
        assertEquals(TrackChoice(null, null), chooseTracks(emptyList(), emptyList(), LanguagePrefs(audioLanguage = "eng", subtitleMode = SubtitleMode.Always)))
    }

    @Test
    fun languageCodesMatchAcrossTheirTwoForms() {
        assertTrue(sameLanguage("GER", "deu"))
        assertTrue(sameLanguage("fra", "fre"))
        assertFalse(sameLanguage("eng", "ger"))
        assertFalse(sameLanguage(null, "eng"))
    }
}
