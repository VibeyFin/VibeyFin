package app.vibeyfin.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The filter set a custom row saves: it must read back as it was written, and survive filters added later. */
class FilterSetTest {
    @Test
    fun aCombinationReadsBackAsItWasSaved() {
        val saved = FilterSet(
            sort = PageSort.ReleaseDate,
            order = SortOrder.Ascending,
            type = TypeFilter.Movies,
            watched = WatchedFilter.Unwatched,
            genres = listOf(NamedValue("g1", "Drama"), NamedValue("g2", "Thriller")),
            genreMatch = GenreMatch.All,
            years = YearRange(1990, 1999),
            minCommunityRating = 7,
            studios = listOf(NamedValue("s1", "A24")),
            tags = listOf("dance"),
            runtime = RuntimeLimit(RuntimeLimit.Bound.ShorterThan, 90),
            addedWithin = AddedWithin.Month,
            quality = VideoQuality.Hdr,
        )

        assertEquals(saved, FilterSet.decode(saved.encode()))
    }

    @Test
    fun aSavedSetCarriesItsVersion() {
        assertEquals(true, FilterSet().encode().contains("\"version\":${FilterSet.VERSION}"))
    }

    @Test
    fun aSetSavedByANewerAppKeepsTheFiltersThisOneKnows() {
        val newer = """{"version":99,"watched":"Unwatched","moodFilter":"cosy","quality":"Hologram","minCommunityRating":8}"""

        val read = FilterSet.decode(newer)

        assertEquals(WatchedFilter.Unwatched, read?.watched)
        assertEquals(8, read?.minCommunityRating)
        assertNull(read?.quality)
    }

    @Test
    fun somethingThatIsNotAFilterSetReadsAsNothing() {
        assertNull(FilterSet.decode("not json"))
    }
}
