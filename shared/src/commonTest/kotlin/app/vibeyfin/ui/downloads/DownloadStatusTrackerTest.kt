package app.vibeyfin.ui.downloads

import app.vibeyfin.domain.DownloadRunner
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadStatusTrackerTest {
    private val tracker = DownloadStatusTracker()

    private fun progress(title: String, done: Long, total: Long?, remaining: Int = 1, waiting: DownloadRunner.Waiting? = null): DownloadRunner.Progress = DownloadRunner.Progress("id-$title", title, done, total, remaining, waiting)

    @Test
    fun theTitleNamesTheDownloadAndTheSecondLineSaysHowMuchOfItIsHere() {
        val shown = tracker.update(progress("Heat", done = 1_234_000_000, total = 4_500_000_000))

        assertEquals("Downloading Heat", shown.title)
        assertEquals("1.23 GB of 4.50 GB", shown.subtitle)
        assertEquals(1_234_000_000L, shown.queueDone)
        assertEquals(4_500_000_000L, shown.queueTotal)
    }

    @Test
    fun eachSideTakesTheUnitItNeeds() {
        val shown = tracker.update(progress("Heat", done = 512_340_000, total = 2_000_000_000))

        assertEquals("512.34 MB of 2.00 GB", shown.subtitle)
    }

    @Test
    fun aSizeThatIsNotKnownYetShowsOnlyWhatIsHere() {
        val shown = tracker.update(progress("Heat", done = 300_000_000, total = null))

        assertEquals("300.00 MB", shown.subtitle)
        assertEquals(300_000_000L, shown.queueTotal)
    }

    @Test
    fun theNextDownloadStartsItsOwnCountInTheSecondLine() {
        tracker.update(progress("Heat", done = 1_000_000_000, total = 1_000_000_000, remaining = 2))

        val shown = tracker.update(progress("Ronin", done = 50_000_000, total = 500_000_000))

        assertEquals("Downloading Ronin", shown.title)
        assertEquals("50.00 MB of 500.00 MB", shown.subtitle)
    }

    @Test
    fun waitingKeepsTheTitleAndBytesAndSaysWhatItWaitsFor() {
        tracker.update(progress("Heat", done = 400_000_000, total = 1_000_000_000))

        val shown = tracker.update(progress("Heat", done = 0, total = null, remaining = 0, waiting = DownloadRunner.Waiting.Wifi))

        assertEquals("Downloading Heat", shown.title)
        assertEquals("Waiting for Wi-Fi", shown.subtitle)
        assertEquals(400_000_000L, shown.queueDone)
    }

    @Test
    fun theBarCountsTheWholeQueueSoItDoesNotEndWithTheFirstVideo() {
        tracker.update(progress("Heat", done = 1_000, total = 1_000, remaining = 2))

        val shown = tracker.update(progress("Ronin", done = 250, total = 1_000, remaining = 1))

        assertEquals(1_250L, shown.queueDone)
        assertEquals(2_000L, shown.queueTotal)
    }

    @Test
    fun downloadsStillToDoAreCountedAtTheAverageSizeSoFar() {
        val shown = tracker.update(progress("Heat", done = 500, total = 1_000, remaining = 3))

        assertEquals(500L, shown.queueDone)
        assertEquals(3_000L, shown.queueTotal)
    }

    @Test
    fun aDownloadThatStoppedShortIsLeftOutOfTheBar() {
        tracker.update(progress("Heat", done = 400, total = 1_000, remaining = 2))

        val shown = tracker.update(progress("Ronin", done = 100, total = 1_000, remaining = 1))

        assertEquals(100L, shown.queueDone)
        assertEquals(1_000L, shown.queueTotal)
    }
}
