package app.vibeyfin.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeTextTest {
    @Test
    fun anEpisodeTagNamesSeasonAndEpisodeOrJustTheEpisode() {
        assertEquals("S1 E4", Strings.episodeTag(1, 4))
        assertEquals("E4", Strings.episodeTag(null, 4))
        assertNull(Strings.episodeTag(1, null))
    }

    @Test
    fun aReleaseDateIsWrittenOutAndAnythingElseIsNothing() {
        assertEquals("22 September 2015", Strings.releaseDate("2015-09-22"))
        assertEquals("1 January 1999", Strings.releaseDate("1999-01-01"))
        assertNull(Strings.releaseDate(null))
        assertNull(Strings.releaseDate("2015"))
        assertNull(Strings.releaseDate("2015-13-01"))
    }

    @Test
    fun aRatingTagHasOneDecimal() {
        assertEquals("★ 8.2", Strings.ratingTag(8.2))
        assertEquals("★ 7.0", Strings.ratingTag(7.0))
        assertEquals("★ 7.9", Strings.ratingTag(7.94))
    }
}
