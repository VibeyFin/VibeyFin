package app.vibeyfin.ui.children

import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionGroupsTest {
    private fun item(id: String, kind: ItemKind, year: Int?) = MediaItem(
        id = id, title = id, kind = kind, isFolder = false, year = year, runtimeMinutes = null, genres = emptyList(), overview = null,
        rating = null, ageRating = null, seriesName = null, seasonNumber = null, episodeNumber = null, posterAspectRatio = 2f / 3f,
        played = false, posterUrl = null, backdropUrl = null, logoUrl = null,
    )

    private val collection = item("collection", ItemKind.Collection, null)

    @Test
    fun membersAreGroupedByKindAndOldestFirst() {
        val state = ChildrenUiState(
            collection = collection,
            items = listOf(
                item("late", ItemKind.Movie, 2020),
                item("show", ItemKind.Series, 2010),
                item("early", ItemKind.Movie, 1999),
                item("extra", ItemKind.Episode, 2005),
            ),
        )

        assertEquals(listOf(Strings.collectionMovies, Strings.collectionShows, Strings.collectionOther), state.collectionGroups.map { it.title })
        assertEquals(listOf("early", "late"), state.collectionGroups.first().items.map { it.id })
    }

    @Test
    fun theGroupsDoNotWaitForTheCollectionItself() {
        val state = ChildrenUiState(collection = null, items = listOf(item("early", ItemKind.Movie, 1999)))

        assertEquals(listOf("early"), state.collectionGroups.single().items.map { it.id })
    }

    @Test
    fun anEmptyGroupIsLeftOut() {
        val state = ChildrenUiState(collection = collection, items = listOf(item("only", ItemKind.Movie, 2001)))

        assertEquals(listOf(Strings.collectionMovies), state.collectionGroups.map { it.title })
    }

    @Test
    fun aMemberWithoutAYearComesLastAndTheTitleBreaksATie() {
        val state = ChildrenUiState(
            collection = collection,
            items = listOf(item("b", ItemKind.Movie, 2001), item("none", ItemKind.Movie, null), item("a", ItemKind.Movie, 2001)),
        )

        assertEquals(listOf("a", "b", "none"), state.collectionGroups.single().items.map { it.id })
    }
}
