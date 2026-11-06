package app.vibeyfin.ui.downloads

import app.vibeyfin.domain.CompletedDownload
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadedTitleTest {
    @Test
    fun oneDownloadIsNamed() {
        assertEquals("Downloaded Heat", downloadedTitle(listOf(CompletedDownload("Heat", null))))
        assertEquals("Downloaded Pilot", downloadedTitle(listOf(CompletedDownload("Pilot", "Saga Show"))))
    }

    @Test
    fun severalEpisodesOfOneShowAreCountedUnderTheShow() {
        val episodes = listOf(CompletedDownload("Pilot", "Saga Show"), CompletedDownload("The Ferry", "Saga Show"), CompletedDownload("Tides", "Saga Show"))

        assertEquals("Downloaded 3 episodes for Saga Show", downloadedTitle(episodes))
    }

    @Test
    fun anythingElseIsCountedAsVideos() {
        val mixed = listOf(CompletedDownload("Heat", null), CompletedDownload("Ronin", null))
        val twoShows = listOf(CompletedDownload("Pilot", "Saga Show"), CompletedDownload("Pilot", "Solo Season"))

        assertEquals("Downloaded 2 videos", downloadedTitle(mixed))
        assertEquals("Downloaded 2 videos", downloadedTitle(twoShows))
    }
}
