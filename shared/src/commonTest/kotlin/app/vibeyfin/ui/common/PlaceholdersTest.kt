package app.vibeyfin.ui.common

import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaAspect
import app.vibeyfin.domain.model.SearchKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceholdersTest {
    @Test
    fun aPosterTileHasATitleLongEnoughToFillItsLines() {
        val tile = Placeholders.posterTile

        assertTrue(tile.title.length >= 60)
        assertEquals(MediaAspect.POSTER, tile.posterAspectRatio)
        assertEquals(null, tile.posterUrl)
    }

    @Test
    fun tilesInARowAreDistinctSoALazyListCanKeyThem() {
        val ids: List<String> = Placeholders.posterTiles(6).map { it.id }

        assertEquals(6, ids.distinct().size)
    }

    @Test
    fun anEpisodeIsWideAndNamesItsShowAndNumber() {
        val episode = Placeholders.episode

        assertEquals(ItemKind.Episode, episode.kind)
        assertEquals(MediaAspect.WIDE, episode.posterAspectRatio)
        assertTrue(episode.overview.orEmpty().length >= 300)
        assertTrue(episode.seasonNumber != null && episode.episodeNumber != null)
    }

    @Test
    fun theItemFillsEveryBlockOfItsPage() {
        val item = Placeholders.item

        assertTrue(item.overview.orEmpty().length >= 400)
        assertTrue(item.genres.size >= 3)
        assertTrue(item.cast.size >= 6)
        assertTrue(item.cast.all { it.name.length >= 20 && it.role.orEmpty().length >= 20 })
        assertTrue(item.year != null && item.runtimeMinutes != null && item.ageRating != null && item.rating != null)
    }

    @Test
    fun aContinueCardHasATitleASubtitleAndProgress() {
        val card = Placeholders.continueCard

        assertTrue(card.title.length >= 40)
        assertTrue(card.subtitle.orEmpty().length >= 30)
        assertTrue(card.progress != null)
    }

    @Test
    fun aRowIsLoadedWithPlaceholderTiles() {
        val row = Placeholders.mediaRow(count = 4)

        assertEquals(4, row.items.size)
        assertEquals(false, row.loading)
        assertTrue(row.title.length >= 30)
    }

    @Test
    fun aSeerrTitleAndItsPageAreFull() {
        val details = Placeholders.seerrDetails

        assertTrue(details.title.title.length >= 60)
        assertTrue(details.title.overview.orEmpty().length >= 400)
        assertTrue(details.cast.size >= 6)
        assertTrue(details.seasons.isNotEmpty())
        assertTrue(Placeholders.seerrEpisode.overview.orEmpty().length >= 300)
    }

    @Test
    fun aPersonHasABiography() {
        val person = Placeholders.person

        assertTrue(person.name.length >= 30)
        assertTrue(person.overview.orEmpty().length >= 400)
    }

    @Test
    fun everySearchKindHasAResultWithItsOwnShape() {
        val shapes: Map<SearchKind, Float> = SearchKind.entries.associateWith { Placeholders.searchResult(it).aspectRatio }

        assertEquals(MediaAspect.POSTER, shapes.getValue(SearchKind.Movie))
        assertEquals(MediaAspect.WIDE, shapes.getValue(SearchKind.Episode))
        assertEquals(MediaAspect.POSTER, shapes.getValue(SearchKind.Person))
        assertTrue(Placeholders.searchResult(SearchKind.Movie).title.length >= 60)
    }

    @Test
    fun aProfileTileHasALongName() {
        assertTrue(Placeholders.profile.name.length >= 24)
    }

    @Test
    fun aServerRowHasALongNameAndMeta() {
        assertTrue(Placeholders.serverRow.server.name.length >= 40)
    }
}
