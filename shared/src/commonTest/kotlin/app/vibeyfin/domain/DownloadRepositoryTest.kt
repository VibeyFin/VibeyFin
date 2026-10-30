package app.vibeyfin.domain

import app.vibeyfin.data.db.DOWNLOAD_DONE
import app.vibeyfin.data.db.DOWNLOAD_FAILED
import app.vibeyfin.data.db.DOWNLOAD_PAUSED
import app.vibeyfin.data.db.DOWNLOAD_QUEUED
import app.vibeyfin.data.db.DOWNLOAD_RUNNING
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.FakeDownloadScheduler
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.count
import app.vibeyfin.testing.downloadItemJson
import app.vibeyfin.testing.itemsPage
import app.vibeyfin.testing.parentJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadRepositoryTest {
    private val app = TestApp()
    private val repository = app.downloadRepository
    private val alex = ProfileRef("srv-1", "u1")

    @AfterTest
    fun tearDown() = runTest { app.close() }

    /** Saga: season 1 has episodes e1 (watched, 100 bytes) and e2 (200 bytes); movie m1 is 50 bytes. */
    private suspend fun saga(): FakeServer {
        val server = app.signedIn()
        fun episode(id: String, size: Long, played: Boolean = false, number: Int) = downloadItemJson(
            id, "Episode $number", "Episode", size = size, seriesId = "s1", seriesName = "Saga", seasonId = "se1",
            seasonNumber = 1, episodeNumber = number, played = played,
        )
        server.on("GET", "/Items") { request ->
            when (request.query["ids"]) {
                "m1" -> Reply.json(itemsPage(downloadItemJson("m1", "Heat", size = 50)))
                "e1" -> Reply.json(itemsPage(episode("e1", 100, played = true, number = 1)))
                "s1,se1" -> Reply.json(itemsPage(parentJson("s1", "Saga", "Series"), parentJson("se1", "Season 1", "Season", number = 1, seriesId = "s1")))
                else -> Reply.json(itemsPage())
            }
        }
        server.on("GET", "/Shows/s1/Episodes") { request ->
            val all = listOf(episode("e1", 100, played = true, number = 1), episode("e2", 200, number = 2))
            Reply.json(itemsPage(*all.toTypedArray()))
        }
        return server
    }

    private suspend fun plan(target: DownloadTarget) = assertIs<DownloadAttempt.Success<DownloadPlan>>(repository.plan(alex, target)).value

    @Test
    fun aSeriesComesToEveryEpisodeWithItsSizeAndWatchedState() = runTest {
        saga()

        val plan = plan(DownloadTarget.Series("s1"))

        assertEquals(listOf("e1", "e2"), plan.candidates.map { it.itemId })
        assertEquals(300L, plan.bytes())
        assertEquals(listOf("e2"), plan.unwatched.map { it.itemId })
        assertEquals(200L, plan.bytes(plan.unwatched))
        assertTrue(plan.candidates.all { it.kind == ItemKind.Episode })
    }

    @Test
    fun aSeasonAsksForItsOwnEpisodes() = runTest {
        val server = saga()

        plan(DownloadTarget.Season("s1", "se1"))

        assertEquals("se1", app.jellyfin.requestsTo("GET", "/Shows/s1/Episodes").single().query["seasonId"])
        assertTrue(server.address.isNotEmpty())
    }

    @Test
    fun aFolderOrAnythingWithoutAFileIsLeftOut() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsPage(parentJson("s1", "Saga", "Series"), downloadItemJson("m1", size = 5))))

        val plan = plan(DownloadTarget.Item("m1"))

        assertEquals(listOf("m1"), plan.candidates.map { it.itemId })
    }

    @Test
    fun enqueuingSavesTheCopyAndTheShowAndSeasonAndAsksForTheBackgroundWork() = runTest {
        saga()

        val queued = repository.enqueue(alex, plan(DownloadTarget.Item("e1")))

        assertEquals(DownloadAttempt.Success(1), queued)
        assertEquals(DOWNLOAD_QUEUED, app.downloads.find("srv-1", "u1", "e1")!!.state)
        assertEquals(100L, app.downloads.find("srv-1", "u1", "e1")!!.bytesTotal)
        assertEquals(setOf("e1", "s1", "se1"), app.downloads.observeItems("srv-1", "u1").first().map { it.itemId }.toSet())
        assertEquals(listOf(DownloadNetwork.None), app.scheduler.requests, "the run waits for Wi-Fi itself")
    }

    @Test
    fun theWorkIsAskedForWithoutANetworkSoARunKeepsGoingThroughALostOne() = runTest {
        saga()
        app.profileSettings.setDownloadOverMobile(true)

        repository.enqueue(alex, plan(DownloadTarget.Item("m1")))

        assertEquals(listOf(DownloadNetwork.None), app.scheduler.requests)
    }

    @Test
    fun aRunThatIsWaitingIsToldAboutNewWork() = runTest {
        saga()
        var wakes = 0
        val job = launch(Dispatchers.Unconfined) { app.wake.signal.collect { wakes++ } }

        repository.enqueue(alex, plan(DownloadTarget.Item("m1")))

        assertEquals(1, wakes)
        job.cancel()
    }

    @Test
    fun askingTwiceDoesNotQueueItTwice() = runTest {
        saga()
        repository.enqueue(alex, plan(DownloadTarget.Item("m1")))

        val again = plan(DownloadTarget.Item("m1"))
        assertTrue(again.candidates.single().alreadyHave)
        assertTrue(again.missing.isEmpty())
        assertEquals(DownloadAttempt.Success(0), repository.enqueue(alex, again))
        assertEquals(1, app.database.count("downloads"))
    }

    @Test
    fun onlyTheChosenEpisodesAreQueued() = runTest {
        saga()
        val plan = plan(DownloadTarget.Series("s1"))

        repository.enqueue(alex, plan, plan.unwatched)

        assertEquals(listOf("e2"), app.downloads.observeAll("srv-1", "u1").first().map { it.itemId })
    }

    @Test
    fun aSavedCopyThatHasItsPicturesIsNotReplacedWhenRequeued() = runTest {
        saga()
        repository.enqueue(alex, plan(DownloadTarget.Item("m1")))
        val snapshot = OfflineSnapshot.decode(app.downloads.findItem("srv-1", "u1", "m1")!!.snapshotJson)
        app.downloads.setSnapshot("srv-1", "u1", "m1", snapshot.copy(assetsDone = true, images = mapOf("poster" to "x")).encode())
        app.downloads.setState("srv-1", "u1", "m1", DOWNLOAD_FAILED, "Unknown", 1)

        repository.resume(alex, "m1")

        assertEquals(DOWNLOAD_QUEUED, app.downloads.find("srv-1", "u1", "m1")!!.state)
        assertEquals("x", OfflineSnapshot.decode(app.downloads.findItem("srv-1", "u1", "m1")!!.snapshotJson).images["poster"])
    }

    @Test
    fun pausingAndResumingMoveTheStateAndResumingStartsTheWork() = runTest {
        saga()
        repository.enqueue(alex, plan(DownloadTarget.Item("m1")))
        app.scheduler.requests.clear()

        repository.pause(alex, "m1")
        assertEquals(DOWNLOAD_PAUSED, app.downloads.find("srv-1", "u1", "m1")!!.state)

        repository.resume(alex, "m1")
        assertEquals(DOWNLOAD_QUEUED, app.downloads.find("srv-1", "u1", "m1")!!.state)
        assertEquals(1, app.scheduler.requests.size)
    }

    @Test
    fun aFinishedDownloadCannotBePausedOrResumed() = runTest {
        saga()
        repository.enqueue(alex, plan(DownloadTarget.Item("m1")))
        app.downloads.complete("srv-1", "u1", "m1", 50, "srv-1/u1/m1/video.mkv", 5)

        repository.pause(alex, "m1")
        repository.resume(alex, "m1")

        assertEquals(DOWNLOAD_DONE, app.downloads.find("srv-1", "u1", "m1")!!.state)
    }

    @Test
    fun removingDeletesTheFilesAndTheShowOnceNoEpisodeIsLeft() = runTest {
        saga()
        val all = plan(DownloadTarget.Series("s1"))
        repository.enqueue(alex, all)
        app.files.open("srv-1/u1/e1/video.mkv", append = false).write("x".encodeToByteArray(), 1)
        app.files.open("srv-1/u1/s1/images/poster.img", append = false).write("x".encodeToByteArray(), 1)

        repository.remove(alex, "e1")
        assertNull(app.files.text("srv-1/u1/e1/video.mkv"))
        assertTrue(app.downloads.findItem("srv-1", "u1", "s1") != null, "e2 still sits under the show")

        repository.remove(alex, "e2")
        assertNull(app.downloads.findItem("srv-1", "u1", "s1"))
        assertNull(app.downloads.findItem("srv-1", "u1", "se1"))
        assertNull(app.files.text("srv-1/u1/s1/images/poster.img"))
        assertEquals(0, app.database.count("downloads"))
        assertEquals(0, app.database.count("download_items"))
    }

    @Test
    fun removingAllClearsTheProfilesDownloads() = runTest {
        saga()
        repository.enqueue(alex, plan(DownloadTarget.Series("s1")))
        repository.enqueue(alex, plan(DownloadTarget.Item("m1")))

        repository.removeAll(alex)

        assertEquals(0, app.database.count("downloads"))
        assertEquals(0, app.database.count("download_items"))
    }

    @Test
    fun theListJoinsEachDownloadWithItsSavedCopy() = runTest {
        saga()
        repository.enqueue(alex, plan(DownloadTarget.Item("m1")))
        app.downloads.complete("srv-1", "u1", "m1", 50, "srv-1/u1/m1/video.mkv", 5)

        val download = repository.observeAll(alex).first().single()

        assertEquals("Heat", download.item.title)
        assertEquals(DownloadState.Done, download.state)
        assertEquals(1f, download.progress)
        assertEquals(50L, download.bytesTotal)
        assertEquals(alex, download.profile)
        assertEquals(50L, repository.observeBytesUsed(alex).first())
    }

    @Test
    fun theStatesChangeWhenADownloadDoesButNotForItsByteCount() = runTest {
        saga()
        repository.enqueue(alex, plan(DownloadTarget.Item("m1")))
        app.downloads.setState("srv-1", "u1", "m1", DOWNLOAD_RUNNING, null, 1)
        // Collected on another thread, so the list sits in a flow instead of a plain MutableList.
        val seen = MutableStateFlow(emptyList<List<Download>>())
        backgroundScope.launch(Dispatchers.Default) { repository.observeStates(alex).collect { states -> seen.update { it + listOf(states) } } }
        settle()

        repeat(50) { app.downloads.setProgress("srv-1", "u1", "m1", it * 1_000L, 100_000, 2) }
        settle()
        assertEquals(listOf(DownloadState.Running), seen.value.map { it.single().state }, "progress alone does not emit")

        app.downloads.setState("srv-1", "u1", "m1", DOWNLOAD_PAUSED, null, 3)
        settle()
        assertEquals(listOf(DownloadState.Running, DownloadState.Paused), seen.value.map { it.single().state })
    }

    /** Lets the database's flows, which run off the test's clock, deliver what they have. */
    private suspend fun settle() = kotlinx.coroutines.withContext(Dispatchers.Default) { kotlinx.coroutines.delay(300) }

    @Test
    fun purgingDeletesTheProfilesWholeFolderEvenFilesNoRowKnows() = runTest {
        saga()
        repository.enqueue(alex, plan(DownloadTarget.Item("m1")))
        listOf("srv-1/u1/stray/images/poster.img", "srv-1/u2/m9/video.mkv").forEach { app.files.open(it, append = false).also { out -> out.write("x".encodeToByteArray(), 1) }.close() }

        repository.purge(alex)

        assertNull(app.downloads.find("srv-1", "u1", "m1"))
        assertTrue(app.files.contents.keys.none { it.startsWith("srv-1/u1/") }, "nothing of the profile is left")
        assertTrue("srv-1/u2/m9/video.mkv" in app.files.contents, "another profile keeps its files")
    }

    @Test
    fun aPlatformWithoutDownloadsQueuesNothingToRun() = runTest {
        saga()
        val noScheduler = FakeDownloadScheduler(isSupported = false)
        val repository = DownloadRepository(app.downloads, app.connection, app.files, noScheduler, app.wake, app.languages, app.clock)

        repository.enqueue(alex, repository.plan(alex, DownloadTarget.Item("m1")).let { (it as DownloadAttempt.Success).value })

        assertTrue(noScheduler.requests.isEmpty())
    }

    @Test
    fun noPermissionNoServerAndNoSessionAreToldApart() = runTest {
        val server = saga()
        server.on("GET", "/Items", Reply.status(HttpStatusCode.Forbidden))
        assertEquals(DownloadAttempt.Failure(DownloadFailure.Forbidden), repository.plan(alex, DownloadTarget.Item("m1")))

        server.refuseConnections()
        assertEquals(DownloadAttempt.Failure(null, unreachable = true), repository.plan(alex, DownloadTarget.Item("m1")))

        assertEquals(DownloadAttempt.Failure(DownloadFailure.SignIn), repository.plan(ProfileRef("srv-1", "nobody"), DownloadTarget.Item("m1")))
    }

    @Test
    fun theServersPolicySaysWhetherDownloadingIsAllowed() = runTest {
        val server = saga()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Policy":{"EnableContentDownloading":false}}"""))
        assertEquals(false, repository.canDownload(alex))

        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Policy":{"EnableContentDownloading":true}}"""))
        assertEquals(true, repository.canDownload(alex))

        server.refuseConnections()
        assertNull(repository.canDownload(alex))
    }

    @Test
    fun resumingAfterStartAsksForTheWorkOnlyWhenThereIsSome() = runTest {
        saga()
        repository.resumePending()
        assertTrue(app.scheduler.requests.isEmpty())

        repository.enqueue(alex, plan(DownloadTarget.Item("m1")))
        app.scheduler.requests.clear()
        repository.resumePending()

        assertEquals(1, app.scheduler.requests.size)
    }
}
