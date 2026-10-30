package app.vibeyfin.ui.item

import app.vibeyfin.domain.DynamicRange
import app.vibeyfin.domain.VideoFormat
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemFactsTest {
    private fun item(
        kind: ItemKind = ItemKind.Movie,
        year: Int? = null,
        runtimeMinutes: Int? = null,
        rating: Double? = null,
        ageRating: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        videoFormat: VideoFormat? = null,
    ) = MediaItem(
        id = "i", title = "T", kind = kind, isFolder = false, year = year, runtimeMinutes = runtimeMinutes,
        genres = emptyList(), overview = null, rating = rating, ageRating = ageRating, seriesName = null,
        seasonNumber = seasonNumber, episodeNumber = episodeNumber, posterAspectRatio = 1f, posterUrl = null,
        backdropUrl = null, videoFormat = videoFormat,
    )

    private fun video(range: DynamicRange = DynamicRange.Sdr, width: Int = 1920, height: Int = 1080) = VideoFormat(codec = "hevc", width = width, height = height, dynamicRange = range, frameRate = 24f, bitrateKbps = null)

    @Test
    fun `Dolby Vision wins over resolution`() {
        assertEquals("Dolby Vision", item(videoFormat = video(DynamicRange.DolbyVision, 3840, 2160)).formatBadge())
    }

    @Test
    fun `HDR10 and HLG are named`() {
        assertEquals("HDR10", item(videoFormat = video(DynamicRange.Hdr10)).formatBadge())
        assertEquals("HLG", item(videoFormat = video(DynamicRange.Hlg, 3840, 2160)).formatBadge())
    }

    @Test
    fun `4K SDR shows 4K`() {
        assertEquals("4K", item(videoFormat = video(width = 3840, height = 2160)).formatBadge())
        assertEquals("4K", item(videoFormat = video(width = 3840, height = 1600)).formatBadge())
    }

    @Test
    fun `1080p and 720p show HD and smaller shows nothing`() {
        assertEquals("HD", item(videoFormat = video()).formatBadge())
        assertEquals("HD", item(videoFormat = video(width = 1280, height = 720)).formatBadge())
        assertNull(item(videoFormat = video(width = 720, height = 480)).formatBadge())
    }

    @Test
    fun `no video stream shows no badge`() {
        assertNull(item(videoFormat = null).formatBadge())
    }

    @Test
    fun `only one badge`() {
        // HDR 4K is a single string, not "4K HDR10".
        assertEquals("HDR10", item(videoFormat = video(DynamicRange.Hdr10, 3840, 2160)).formatBadge())
    }

    @Test
    fun aMovieShowsYearAgeRatingRuntimeAndScoreInThatOrder() {
        val facts = item(year = 2013, ageRating = "PG", runtimeMinutes = 84, rating = 7.3).facts()

        assertEquals(listOf("2013", "PG", "1 h 24 min", "★ 7.3"), facts)
    }

    @Test
    fun anEpisodeStartsWithItsSeasonAndNumber() {
        assertEquals(listOf("S2 E5", "2020"), item(ItemKind.Episode, year = 2020, seasonNumber = 2, episodeNumber = 5).facts())
        assertEquals(listOf("E5"), item(ItemKind.Episode, episodeNumber = 5).facts())
    }

    @Test
    fun onlyAnEpisodeHasAnEpisodeLabel() {
        assertEquals(emptyList(), item(ItemKind.Movie, seasonNumber = 1, episodeNumber = 2).facts())
    }

    @Test
    fun anItemWithNoFactsHasNone() {
        assertEquals(emptyList(), item().facts())
    }

    @Test
    fun runtimesReadLikeSpeech() {
        assertEquals("1 min", runtimeText(1))
        assertEquals("59 min", runtimeText(59))
        assertEquals("1 h", runtimeText(60))
        assertEquals("1 h 1 min", runtimeText(61))
        assertEquals("2 h 15 min", runtimeText(135))
    }

    @Test
    fun scoresShowOneDecimal() {
        assertEquals(listOf("★ 7.0"), item(rating = 7.0).facts())
        assertEquals(listOf("★ 7.3"), item(rating = 7.26).facts())
        assertEquals(listOf("★ 10.0"), item(rating = 10.0).facts())
        assertEquals(listOf("★ 0.5"), item(rating = 0.5).facts())
    }
}
