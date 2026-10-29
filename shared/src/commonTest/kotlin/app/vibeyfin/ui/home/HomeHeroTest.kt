package app.vibeyfin.ui.home

import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeHeroTest {
    private fun item(id: String, backdrop: Boolean = true, logo: Boolean = true, year: Int? = 2024, genres: List<String> = emptyList(), played: Boolean = false) = MediaItem(
        id = id, title = id, kind = ItemKind.Movie, isFolder = false, year = year, runtimeMinutes = null, genres = genres, overview = null,
        rating = null, ageRating = null, seriesName = null, seasonNumber = null, episodeNumber = null, posterAspectRatio = 2f / 3f,
        played = played, posterUrl = null, backdropUrl = if (backdrop) "b-$id" else null, logoUrl = if (logo) "l-$id" else null,
    )

    @Test
    fun onlyAnItemWithABackdropAndALogoCanBeFeatured() {
        val picked = pickHeroes(listOf(item("no-logo", logo = false), item("no-backdrop", backdrop = false), item("ok")), 5, Random(1))

        assertEquals(listOf("ok"), picked.map { it.id })
    }

    @Test
    fun aWatchedItemIsNeverFeatured() {
        val picked = pickHeroes(listOf(item("seen", played = true), item("fresh")), 2, Random(1))

        assertEquals(listOf("fresh"), picked.map { it.id })
        assertTrue(pickHeroes(listOf(item("seen", played = true)), 1, Random(1)).isEmpty())
    }

    @Test
    fun nothingQualifyingMeansNoHero() {
        assertTrue(pickHeroes(listOf(item("a", logo = false)), 3, Random(1)).isEmpty())
        assertTrue(pickHeroes(emptyList(), 3, Random(1)).isEmpty())
    }

    @Test
    fun theSameItemInBothListsIsOneCandidateAndEveryQualifyingItemCanBePicked() {
        val items = listOf(item("a"), item("a"), item("b"), item("c"))

        val seen = (0 until 60).flatMap { seed -> pickHeroes(items, 1, Random(seed)).map { it.id } }.toSet()

        assertEquals(setOf("a", "b", "c"), seen)
    }

    @Test
    fun severalArePickedAtMostTheNumberAskedAndNeverOneTwice() {
        val items = listOf(item("a"), item("b"), item("a"), item("c"), item("d"), item("e"), item("f"))

        for (seed in 0 until 20) {
            val picked = pickHeroes(items, 5, Random(seed)).map { it.id }
            assertEquals(5, picked.size)
            assertEquals(picked.size, picked.toSet().size)
        }
        assertEquals(3, pickHeroes(listOf(item("a"), item("b"), item("c")), 5, Random(1)).size)
        assertTrue(pickHeroes(items, 0, Random(1)).isEmpty())
    }

    @Test
    fun theFactsAreWhatItIsAndAtMostFourGenres() {
        assertEquals(listOf("Movie", "Drama", "Thriller", "Action", "Comedy"), item("x", genres = listOf("Drama", "Thriller", "Action", "Comedy", "Horror")).heroFacts)
        assertEquals(listOf("Movie"), item("y", year = null).heroFacts)
        assertTrue(item("z").copy(kind = ItemKind.Series).heroFacts.first() == "Show")
    }
}
