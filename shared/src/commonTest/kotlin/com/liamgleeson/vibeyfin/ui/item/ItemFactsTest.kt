package com.liamgleeson.vibeyfin.ui.item

import com.liamgleeson.vibeyfin.domain.model.ItemKind
import com.liamgleeson.vibeyfin.domain.model.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemFactsTest {
    private fun item(
        kind: ItemKind = ItemKind.Movie,
        year: Int? = null,
        runtimeMinutes: Int? = null,
        rating: Double? = null,
        ageRating: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ) = MediaItem(
        id = "i", title = "T", kind = kind, isFolder = false, year = year, runtimeMinutes = runtimeMinutes,
        genres = emptyList(), overview = null, rating = rating, ageRating = ageRating, seriesName = null,
        seasonNumber = seasonNumber, episodeNumber = episodeNumber, posterAspectRatio = 1f, posterUrl = null,
        backdropUrl = null,
    )

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
