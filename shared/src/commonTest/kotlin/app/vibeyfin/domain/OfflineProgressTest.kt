package app.vibeyfin.domain

import app.vibeyfin.data.db.DOWNLOAD_DONE
import app.vibeyfin.data.db.DOWNLOAD_QUEUED
import app.vibeyfin.data.db.DOWNLOAD_RUNNING
import app.vibeyfin.data.db.DownloadEntity
import app.vibeyfin.data.db.DownloadItemEntity
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.count
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** What the profile watched without a connection, kept in `pending_reports`, and the downloads tables beside it. */
class OfflineProgressTest {
    private val app = TestApp()
    private val alex = ProfileRef("srv-1", "u1")

    @AfterTest
    fun tearDown() = runTest { app.close() }

    private suspend fun savedAlex() {
        app.servers.save("srv-1", "Home", "10.10.7", "http://nas:8096")
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
    }

    private fun progress(item: String = "e1", ticks: Long = 600_000_000, played: Boolean = false, at: Long = 1_000) =
        PendingProgress(alex, item, ticks, played, at)

    @Test
    fun aNewerRecordOfAnItemReplacesTheOlderOne() = runTest {
        savedAlex()
        app.pending.saveProgress(progress(ticks = 600_000_000, at = 1_000))
        app.pending.saveProgress(progress(ticks = 900_000_000, played = true, at = 2_000))

        val saved = app.pending.progress().single()

        assertEquals(900_000_000, saved.positionTicks)
        assertEquals(true, saved.played)
        assertEquals(2_000, saved.watchedAt)
    }

    @Test
    fun eachItemKeepsItsOwnRecord() = runTest {
        savedAlex()
        app.pending.saveProgress(progress("e1"))
        app.pending.saveProgress(progress("e2"))

        assertEquals(setOf("e1", "e2"), app.pending.progress().map { it.itemId }.toSet())
        assertEquals("e2", app.pending.progress(alex, "e2")!!.itemId)
        assertNull(app.pending.progress(alex, "e3"))
    }

    @Test
    fun aRecordIsGoneOnceRemoved() = runTest {
        savedAlex()
        val record = progress()
        app.pending.saveProgress(record)

        app.pending.remove(record)

        assertEquals(emptyList(), app.pending.progress())
    }

    @Test
    fun offlineProgressAndStopReportsDoNotMix() = runTest {
        savedAlex()
        app.pending.save(PendingStop(alex, "e1", "ms-1", "ps-1", 100, failed = false))
        app.pending.saveProgress(progress("e1"))

        assertEquals(1, app.pending.stops().size)
        assertEquals(1, app.pending.progress().size)
    }

    @Test
    fun offlineProgressSurvivesTheCrashRecoveryAgeLimit() = runTest {
        savedAlex()
        app.pending.saveProgress(progress())
        app.clock.advance(48.hours)

        app.pending.discardOlderThan(24.hours)

        assertEquals(1, app.pending.progress().size)
    }

    private fun download(item: String, state: String, done: Long = 0, total: Long = 1_000, at: Long = 10) = DownloadEntity(
        serverId = "srv-1", userId = "u1", itemId = item, kind = "Episode", seriesId = "s1", seasonId = "se1",
        state = state, bytesDone = done, bytesTotal = total, mediaSourceId = "ms-$item", container = "mkv", etag = null,
        filePath = null, error = null, addedAt = at, updatedAt = at, completedAt = null,
    )

    private fun snapshot(item: String, title: String, search: String) = DownloadItemEntity(
        serverId = "srv-1", userId = "u1", itemId = item, kind = "Episode", title = title, seriesId = "s1", seriesName = "Saga",
        seasonId = "se1", seasonNumber = 1, episodeNumber = 1, searchText = search, positionTicks = 0, played = false,
        userDataAt = 0, snapshotJson = "{}",
    )

    @Test
    fun progressOnlyMovesWhileTheDownloadRuns() = runTest {
        savedAlex()
        app.downloads.upsert(download("e1", DOWNLOAD_QUEUED))

        app.downloads.setProgress("srv-1", "u1", "e1", 400, 1_000, now = 20)
        assertEquals(0, app.downloads.find("srv-1", "u1", "e1")!!.bytesDone, "queued: not moved")

        app.downloads.setState("srv-1", "u1", "e1", DOWNLOAD_RUNNING, null, now = 30)
        app.downloads.setProgress("srv-1", "u1", "e1", 400, 1_000, now = 40)
        assertEquals(400, app.downloads.find("srv-1", "u1", "e1")!!.bytesDone)
    }

    @Test
    fun completingMarksItDoneWithItsFile() = runTest {
        savedAlex()
        app.downloads.upsert(download("e1", DOWNLOAD_RUNNING, done = 500))

        app.downloads.complete("srv-1", "u1", "e1", bytes = 1_000, filePath = "u1/e1.mkv", now = 99)

        val done = app.downloads.find("srv-1", "u1", "e1")!!
        assertEquals(DOWNLOAD_DONE, done.state)
        assertEquals(1_000, done.bytesDone)
        assertEquals("u1/e1.mkv", done.filePath)
        assertEquals(99L, done.completedAt)
    }

    @Test
    fun searchFindsOnlyFinishedDownloadsByTitleShowOrCast() = runTest {
        savedAlex()
        app.downloads.upsert(download("e1", DOWNLOAD_DONE))
        app.downloads.upsert(download("e2", DOWNLOAD_RUNNING))
        app.downloads.upsertItems(
            listOf(
                snapshot("e1", "Pilot", "pilot saga jane roe"),
                snapshot("e2", "Second", "second saga jane roe"),
            ),
        )

        assertEquals(listOf("e1"), app.downloads.searchDone("srv-1", "u1", "%saga%").map { it.itemId })
        assertEquals(listOf("e1"), app.downloads.searchDone("srv-1", "u1", "%jane%").map { it.itemId })
        assertEquals(emptyList(), app.downloads.searchDone("srv-1", "u1", "%nothing%").map { it.itemId })
    }

    @Test
    fun theBytesInUseAddUpTheProfilesDownloads() = runTest {
        savedAlex()
        app.downloads.upsert(download("e1", DOWNLOAD_DONE, done = 700))
        app.downloads.upsert(download("e2", DOWNLOAD_RUNNING, done = 300))

        assertEquals(1_000, app.downloads.observeBytesUsed("srv-1", "u1").first())
    }

    @Test
    fun removingTheServerRemovesItsProfilesDownloadsAndRecords() = runTest {
        savedAlex()
        app.downloads.upsert(download("e1", DOWNLOAD_DONE))
        app.downloads.upsertItems(listOf(snapshot("e1", "Pilot", "pilot")))
        app.pending.saveProgress(progress())

        app.servers.delete("srv-1")

        assertEquals(0, app.database.count("downloads"))
        assertEquals(0, app.database.count("download_items"))
        assertEquals(0, app.database.count("pending_reports"))
        assertTrue(app.pending.progress().isEmpty())
    }

    @Test
    fun theSettingsForDownloadsStartOffAndAreKeptPerProfile() = runTest {
        app.signedIn()

        assertEquals(false, app.profileSettings.downloadOverMobile.first())
        assertEquals(false, app.profileSettings.autoCleanWatched.first())
        assertEquals(ProfileSettings.NO_STORAGE_LIMIT, app.profileSettings.storageLimitMb.first())

        app.profileSettings.setDownloadOverMobile(true)
        app.profileSettings.setAutoCleanWatched(true)
        app.profileSettings.setStorageLimitMb(20_000)
        app.profileSettings.setStorageLimitMb(-5)

        assertEquals(true, app.profileSettings.downloadOverMobile.first())
        assertEquals(true, app.profileSettings.autoCleanWatched.first())
        assertEquals(20_000L, app.profileSettings.storageLimitMb.first())
    }
}
