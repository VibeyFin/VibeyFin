package app.vibeyfin.ui.home

import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeHeroTest {
    private fun item(id: String, backdrop: Boolean = true, logo: Boolean = true, year: Int? = 2024, genres: List<String> = emptyList(), played: Boolean = false) = MediaItem(
        id = id, title = id, kind = ItemKind.Movie, isFolder = false, year = year, runtimeMinutes = null, genres = genres, overview = null,
        rating = null, ageRating = null, seriesName = null, seasonNumber = null, episodeNumber = null, posterAspectRatio = 2f / 3f,
        played = played, posterUrl = null, backdropUrl = if (backdrop) "b-$id" else null, logoUrl = if (logo) "l-$id" else null,
    )

    @Test
    fun onlyAnItemWithABackdropAndALogoCanBeFeatured() {
        val picked = pickHero(listOf(item("no-logo", logo = false), item("no-backdrop", backdrop = false), item("ok")), Random(1))

        assertEquals("ok", picked?.id)
    }

    @Test
    fun aWatchedItemIsNeverFeatured() {
        val picked = pickHero(listOf(item("seen", played = true), item("fresh")), Random(1))

        assertEquals("fresh", picked?.id)
        assertNull(pickHero(listOf(item("seen", played = true)), Random(1)))
    }

    @Test
    fun nothingQualifyingMeansNoHero() {
        assertNull(pickHero(listOf(item("a", logo = false)), Random(1)))
        assertNull(pickHero(emptyList(), Random(1)))
    }

    @Test
    fun theSameItemInBothListsIsOneCandidateAndEveryQualifyingItemCanBePicked() {
        val items = listOf(item("a"), item("a"), item("b"), item("c"))

        val seen = (0 until 60).mapNotNull { pickHero(items, Random(it))?.id }.toSet()

        assertEquals(setOf("a", "b", "c"), seen)
    }

    @Test
    fun theFactsAreWhatItIsAndAtMostFourGenres() {
        assertEquals(listOf("Movie", "Drama", "Thriller", "Action", "Comedy"), item("x", genres = listOf("Drama", "Thriller", "Action", "Comedy", "Horror")).heroFacts)
        assertEquals(listOf("Movie"), item("y", year = null).heroFacts)
        assertTrue(item("z").copy(kind = ItemKind.Series).heroFacts.first() == "Show")
    }
}
