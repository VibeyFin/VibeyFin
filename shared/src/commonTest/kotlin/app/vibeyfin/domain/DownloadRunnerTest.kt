package app.vibeyfin.domain

import app.vibeyfin.data.db.DOWNLOAD_DONE
import app.vibeyfin.data.db.DOWNLOAD_FAILED
import app.vibeyfin.data.db.DOWNLOAD_PAUSED
import app.vibeyfin.data.db.DOWNLOAD_QUEUED
import app.vibeyfin.data.db.DOWNLOAD_RUNNING
import app.vibeyfin.data.db.DOWNLOAD_WAITING_FOR_NETWORK
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.downloadItemJson
import app.vibeyfin.testing.itemsPage
import app.vibeyfin.testing.parentJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DownloadRunnerTest {
    private val app = TestApp()
    private val alex = ProfileRef("srv-1", "u1")
    private val folder = "srv-1/u1/m1"
    private val body = "0123456789"

    @AfterTest
    fun tearDown() = runTest { app.close() }

    // Alex is signed in; the server has movie `m1` of [body] with a poster.
    private suspend fun movieOnServer(
        download: (app.vibeyfin.testing.RecordedRequest) -> Reply = { Reply.ranged(body, it) },
        subtitle: Boolean = false,
        cast: Boolean = false,
        size: Long? = body.length.toLong(),
    ): FakeServer {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsPage(downloadItemJson("m1", "Heat", size = size, subtitle = subtitle, cast = cast))))
        server.on("GET", "/Items/m1/Download", download)
        server.on("GET", "/Items/m1/Images/Primary", Reply.file("poster-bytes"))
        server.on("GET", "/Items/pe1/Images/Primary", Reply.file("ada-bytes"))
        server.on("GET", "/Videos/m1/ms-m1/Subtitles/2/0/Stream.srt", Reply.file("1\n00:00:01,000 --> 00:00:02,000\nHi\n"))
        return server
    }

    private suspend fun queue(target: DownloadTarget = DownloadTarget.Item("m1")) {
        val plan = assertIs<DownloadAttempt.Success<DownloadPlan>>(app.downloadRepository.plan(alex, target)).value
        assertIs<DownloadAttempt.Success<Int>>(app.downloadRepository.enqueue(alex, plan))
    }

    private suspend fun row(id: String = "m1") = assertNotNull(app.downloads.find("srv-1", "u1", id))

    @Test
    fun aMovieIsDownloadedWithItsPictureCastAndSubtitle() = runTest {
        movieOnServer(subtitle = true, cast = true)
        queue()

        val next = app.runner.run()

        assertEquals(DownloadRunner.Next.Done, next)
        val done = row()
        assertEquals(DOWNLOAD_DONE, done.state)
        assertEquals(body.length.toLong(), done.bytesDone)
        assertEquals("$folder/video.mkv", done.filePath)
        assertEquals(body, app.files.text("$folder/video.mkv"))
        assertNull(app.files.text("$folder/video.part"), "the part file became the video")
        val snapshot = OfflineSnapshot.decode(app.downloads.findItem("srv-1", "u1", "m1")!!.snapshotJson)
        assertTrue(snapshot.assetsDone)
        assertEquals("poster-bytes", app.files.text(snapshot.images.getValue("poster")))
        assertEquals("ada-bytes", app.files.text(snapshot.images.getValue("cast:pe1")))
        assertTrue(app.files.text(snapshot.subtitles.single().file)!!.contains("Hi"))
        val item = snapshot.mediaItem(app.files)
        assertEquals("file:///fake/$folder/images/poster.img", item.posterUrl)
        assertNull(item.backdropUrl, "a picture the item never had is not made up")
    }

    @Test
    fun theScrubPreviewTilesAreDownloadedWithTheVideoAndKeptBesideIt() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsPage(downloadItemJson("m1", "Heat", size = body.length.toLong(), trickplayThumbnails = 250))))
        server.on("GET", "/Items/m1/Download") { Reply.ranged(body, it) }
        server.on("GET", "/Items/m1/Images/Primary", Reply.file("poster-bytes"))
        (0..2).forEach { server.on("GET", "/Videos/m1/Trickplay/320/$it.jpg", Reply.file("tile$it")) }
        queue()

        app.runner.run()

        val done = row()
        assertEquals(DOWNLOAD_DONE, done.state)
        assertEquals(body.length.toLong(), done.bytesDone, "the tiles are not counted in the video's progress")
        assertEquals(body.length.toLong(), done.bytesTotal)
        assertEquals(listOf("tile0", "tile1", "tile2"), (0..2).map { app.files.text("$folder/trickplay/$it.jpg") })
        val snapshot = OfflineSnapshot.decode(app.downloads.findItem("srv-1", "u1", "m1")!!.snapshotJson)
        assertEquals(3, assertNotNull(snapshot.trickplay).tileCount)
        assertEquals("ms-m1", app.jellyfin.requestsTo("GET", "/Videos/m1/Trickplay/320/1.jpg").single().query["mediaSourceId"])
    }

    @Test
    fun aTileTheServerNoLongerHasLeavesTheDownloadWithoutTilesButDone() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsPage(downloadItemJson("m1", "Heat", size = body.length.toLong(), trickplayThumbnails = 250))))
        server.on("GET", "/Items/m1/Download") { Reply.ranged(body, it) }
        server.on("GET", "/Items/m1/Images/Primary", Reply.file("poster-bytes"))
        server.on("GET", "/Videos/m1/Trickplay/320/0.jpg", Reply.file("tile0"))
        server.on("GET", "/Videos/m1/Trickplay/320/1.jpg", Reply.status(HttpStatusCode.NotFound))
        queue()

        app.runner.run()

        assertEquals(DOWNLOAD_DONE, row().state)
        val snapshot = OfflineSnapshot.decode(app.downloads.findItem("srv-1", "u1", "m1")!!.snapshotJson)
        assertNull(snapshot.trickplay)
        assertTrue(snapshot.assetsDone)
        assertNull(app.files.text("$folder/trickplay/0.jpg"), "a partial set is not kept")
    }

    @Test
    fun removingADownloadDeletesItsTiles() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsPage(downloadItemJson("m1", "Heat", size = body.length.toLong(), trickplayThumbnails = 50))))
        server.on("GET", "/Items/m1/Download") { Reply.ranged(body, it) }
        server.on("GET", "/Items/m1/Images/Primary", Reply.file("poster-bytes"))
        server.on("GET", "/Videos/m1/Trickplay/320/0.jpg", Reply.file("tile0"))
        queue()
        app.runner.run()
        assertEquals("tile0", app.files.text("$folder/trickplay/0.jpg"))

        app.downloadRepository.remove(alex, "m1")

        assertNull(app.files.text("$folder/trickplay/0.jpg"))
    }

    @Test
    fun theRequestAsksForTheFileAsTheDownloadsProfile() = runTest {
        val server = movieOnServer()
        queue()

        app.runner.run()

        val request = app.jellyfin.requestsTo("GET", "/Items/m1/Download").single()
        assertTrue(request.authorization!!.contains("""Token="token-1""""))
        assertNull(request.range)
        assertEquals(server.address, request.address)
    }

    @Test
    fun anEpisodeKeepsItsShowAndSeasonSoItCanBeGroupedOffline() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items") { request ->
            when (request.query["ids"]) {
                "e1" -> Reply.json(itemsPage(downloadItemJson("e1", "Pilot", "Episode", seriesId = "s1", seriesName = "Saga", seasonId = "se1", seasonNumber = 1, episodeNumber = 1)))
                else -> Reply.json(itemsPage(parentJson("s1", "Saga", "Series"), parentJson("se1", "Season 1", "Season", number = 1, seriesId = "s1")))
            }
        }
        server.on("GET", "/Items/e1/Download") { Reply.ranged(body, it) }
        server.on("GET", "/Items/e1/Images/Primary", Reply.file("still"))
        server.on("GET", "/Items/s1/Images/Primary", Reply.file("show-poster"))
        server.on("GET", "/Items/se1/Images/Primary", Reply.file("season-poster"))
        queue(DownloadTarget.Item("e1"))

        app.runner.run()

        assertEquals(DOWNLOAD_DONE, row("e1").state)
        assertEquals("s1", row("e1").seriesId)
        assertEquals("se1", row("e1").seasonId)
        val show = OfflineSnapshot.decode(app.downloads.findItem("srv-1", "u1", "s1")!!.snapshotJson)
        assertEquals("show-poster", app.files.text(show.images.getValue("poster")))
        assertEquals("Saga", show.item.title)
        assertNotNull(app.downloads.findItem("srv-1", "u1", "se1"))
        assertEquals("Saga", app.downloads.findItem("srv-1", "u1", "e1")!!.seriesName)
    }

    @Test
    fun aLostConnectionKeepsTheBytesAndTheNextRunAsksForTheRest() = runTest {
        var calls = 0
        val server = movieOnServer(download = { if (++calls == 1) Reply.rangedThenDrop(body, it, dropAfter = 4) else Reply.ranged(body, it) })
        queue()

        val first = app.runner.run()

        assertEquals(DownloadRunner.Next.Retry, first)
        assertEquals(DOWNLOAD_WAITING_FOR_NETWORK, row().state)
        assertEquals("0123", app.files.text("$folder/video.part"))
        assertEquals(4L, row().bytesDone)

        val second = app.runner.run()

        assertEquals(DownloadRunner.Next.Done, second)
        assertEquals(body, app.files.text("$folder/video.mkv"))
        val ranges = app.jellyfin.requestsTo("GET", "/Items/m1/Download").map { it.range }
        assertEquals(listOf(null, "bytes=4-"), ranges)
        assertEquals(server.address, "http://nas:8096")
    }

    @Test
    fun aServerThatIgnoresTheRangeStartsTheFileOver() = runTest {
        var calls = 0
        movieOnServer(download = { if (++calls == 1) Reply.rangedThenDrop(body, it, dropAfter = 4) else Reply.file(body) })
        queue()
        app.runner.run()

        app.runner.run()

        assertEquals(body, app.files.text("$folder/video.mkv"))
        assertEquals(DOWNLOAD_DONE, row().state)
    }

    @Test
    fun aFileThatChangedOnTheServerIsStartedOverNotJoinedToTheOldBytes() = runTest {
        var calls = 0
        movieOnServer(
            download = {
                when (++calls) {
                    1 -> Reply.rangedThenDrop(body, it, dropAfter = 4, lastModified = "old")
                    2 -> Reply.ranged("ABCDEFGHIJ", it, lastModified = "new")
                    else -> Reply.ranged("ABCDEFGHIJ", it, lastModified = "new")
                }
            },
            size = 10,
        )
        queue()
        app.runner.run()

        app.runner.run()

        assertEquals("ABCDEFGHIJ", app.files.text("$folder/video.mkv"))
        assertEquals(listOf(null, "bytes=4-", null), app.jellyfin.requestsTo("GET", "/Items/m1/Download").map { it.range })
    }

    @Test
    fun withoutPermissionTheDownloadFailsAndSaysWhy() = runTest {
        movieOnServer(download = { Reply.status(HttpStatusCode.Forbidden) })
        queue()

        val next = app.runner.run()

        assertEquals(DownloadRunner.Next.Done, next)
        assertEquals(DOWNLOAD_FAILED, row().state)
        assertEquals(DownloadFailure.Forbidden.name, row().error)
    }

    @Test
    fun aRejectedTokenFailsTheDownloadAndLocksTheProfile() = runTest {
        movieOnServer(download = { Reply.status(HttpStatusCode.Unauthorized) })
        queue()

        app.runner.run()

        assertEquals(DownloadFailure.SignIn.name, row().error)
        assertNull(app.accounts.sessionToken("srv-1", "u1"), "the profile is locked like on any other 401")
    }

    @Test
    fun anItemThatIsGoneFromTheServerFails() = runTest {
        movieOnServer(download = { Reply.status(HttpStatusCode.NotFound) })
        queue()

        app.runner.run()

        assertEquals(DownloadFailure.NotOnServer.name, row().error)
    }

    @Test
    fun aServerErrorIsTriedAgainLater() = runTest {
        movieOnServer(download = { Reply.status(HttpStatusCode.ServiceUnavailable) })
        queue()

        val next = app.runner.run()

        assertEquals(DownloadRunner.Next.Retry, next)
        assertEquals(DOWNLOAD_QUEUED, row().state)
    }

    @Test
    fun withNoNetworkNothingIsAskedAndTheDownloadsWait() = runTest {
        movieOnServer()
        queue()
        app.network.set(NetworkState.None)

        val next = app.runner.run()

        assertEquals(DownloadRunner.Next.WhenConnected, next)
        assertEquals(DOWNLOAD_WAITING_FOR_NETWORK, row().state)
        assertEquals(0, app.jellyfin.requestsTo("GET", "/Items/m1/Download").size)
    }

    @Test
    fun onMobileDataDownloadsWaitForWifiUnlessTheProfileAllowsIt() = runTest {
        movieOnServer()
        queue()
        app.network.set(NetworkState.Metered)

        val waiting = app.runner.run()

        assertEquals(DownloadRunner.Next.WhenUnmetered, waiting)
        assertEquals(DOWNLOAD_WAITING_FOR_NETWORK, row().state)
        assertEquals(0, app.jellyfin.requestsTo("GET", "/Items/m1/Download").size)

        app.profileSettings.setDownloadOverMobile(true)
        val allowed = app.runner.run()

        assertEquals(DownloadRunner.Next.Done, allowed)
        assertEquals(DOWNLOAD_DONE, row().state)
    }

    @Test
    fun aDownloadTheProcessDiedInTheMiddleOfGoesOnFromItsBytes() = runTest {
        movieOnServer()
        queue()
        // What an earlier process left: running, with part of the file.
        app.downloads.setState("srv-1", "u1", "m1", DOWNLOAD_RUNNING, null, 1)
        app.files.open("$folder/video.part", append = false).apply { write("0123".encodeToByteArray(), 4) }

        app.runner.run()

        assertEquals(body, app.files.text("$folder/video.mkv"))
        assertEquals("bytes=4-", app.jellyfin.requestsTo("GET", "/Items/m1/Download").single().range)
    }

    @Test
    fun theUserPausingStopsTheTransferAndKeepsWhatWasFetched() = runTest {
        val server = movieOnServer()
        queue()
        val gate = server.hold("GET", "/Items/m1/Download")

        val run = async { app.runner.run() }
        runCurrent()
        app.downloadRepository.pause(alex, "m1")
        // The runner sees the change a moment later, on another thread.
        withContext(Dispatchers.Default) { delay(200) }
        gate.open()
        run.await()

        assertEquals(DOWNLOAD_PAUSED, row().state)
        assertTrue((app.files.text("$folder/video.mkv")) == null)
    }

    @Test
    fun downloadsGoOneAtATimeOldestFirst() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items") { request ->
            Reply.json(itemsPage(downloadItemJson(request.query["ids"]!!, size = 3, primaryTag = null)))
        }
        server.on("GET", "/Items/a/Download") { Reply.ranged("aaa", it) }
        server.on("GET", "/Items/b/Download") { Reply.ranged("bbb", it) }
        queue(DownloadTarget.Item("a"))
        app.clock.advance(kotlin.time.Duration.parse("1s"))
        queue(DownloadTarget.Item("b"))

        app.runner.run()

        assertEquals(listOf("GET /Items/a/Download", "GET /Items/b/Download"), app.jellyfin.callLog().filter { it.endsWith("/Download") })
        assertEquals("aaa", app.files.text("srv-1/u1/a/video.mkv"))
        assertEquals("bbb", app.files.text("srv-1/u1/b/video.mkv"))
    }

    @Test
    fun episodesQueuedTogetherGoBySeasonThenEpisodeNumber() = runTest {
        val server = app.signedIn()

        fun episode(id: String, season: Int, number: Int) = downloadItemJson(
            id, "Episode $number", "Episode", size = 3, seriesId = "s1", seriesName = "Saga", seasonId = "se$season",
            seasonNumber = season, episodeNumber = number, primaryTag = null,
        )
        val all = mapOf("c" to episode("c", 2, 1), "a" to episode("a", 1, 3), "d" to episode("d", 1, 1), "b" to episode("b", 1, 2))
        server.on("GET", "/Items") { request -> Reply.json(itemsPage(*request.query["ids"]!!.split(",").mapNotNull { all[it] }.toTypedArray())) }
        server.on("GET", "/Shows/s1/Episodes") { Reply.json(itemsPage(*all.values.toTypedArray())) }
        all.keys.forEach { id -> server.on("GET", "/Items/$id/Download") { Reply.ranged("aaa", it) } }
        queue(DownloadTarget.Series("s1"))

        app.runner.run()

        assertEquals(
            listOf("d", "b", "a", "c").map { "GET /Items/$it/Download" },
            app.jellyfin.callLog().filter { it.endsWith("/Download") },
        )
    }

    @Test
    fun anEpisodeThatFailsIsSkippedAndTheRestOfTheQueueGoesOn() = runTest {
        val server = app.signedIn()

        fun episode(id: String, number: Int) = downloadItemJson(
            id, "Episode $number", "Episode", size = 3, seriesId = "s1", seriesName = "Saga", seasonId = "se1",
            seasonNumber = 1, episodeNumber = number, primaryTag = null,
        )
        val all = mapOf("a" to episode("a", 1), "b" to episode("b", 2), "c" to episode("c", 3))
        server.on("GET", "/Items") { request -> Reply.json(itemsPage(*request.query["ids"]!!.split(",").mapNotNull { all[it] }.toTypedArray())) }
        server.on("GET", "/Shows/s1/Episodes") { Reply.json(itemsPage(*all.values.toTypedArray())) }
        server.on("GET", "/Items/a/Download") { Reply.ranged("aaa", it) }
        server.on("GET", "/Items/b/Download") { Reply.status(HttpStatusCode.Forbidden) }
        server.on("GET", "/Items/c/Download") { Reply.ranged("ccc", it) }
        queue(DownloadTarget.Series("s1"))

        app.runner.run()

        assertEquals(DOWNLOAD_DONE, row("a").state)
        assertEquals(DOWNLOAD_FAILED, row("b").state)
        assertEquals(DOWNLOAD_DONE, row("c").state)
    }

    @Test
    fun aFileThatWouldGoOverTheStorageLimitIsNotStarted() = runTest {
        movieOnServer(size = 2_000_000)
        queue()
        app.profileSettings.setStorageLimitMb(1)

        app.runner.run()

        assertEquals(DownloadFailure.StorageLimit.name, row().error)
        assertEquals(0, app.jellyfin.requestsTo("GET", "/Items/m1/Download").size)
    }

    @Test
    fun aFileThatDoesNotFitOnTheDeviceIsNotStarted() = runTest {
        movieOnServer(size = 2_000_000)
        queue()
        app.files.free = 1_000_000

        app.runner.run()

        assertEquals(DownloadFailure.NoSpace.name, row().error)
        assertEquals(0, app.jellyfin.requestsTo("GET", "/Items/m1/Download").size)
    }

    @Test
    fun aDiskThatFillsUpWhileWritingEndsInNoSpace() = runTest {
        movieOnServer()
        queue()
        app.files.failWrites = Exception("No space left on device")
        app.files.free = 0

        app.runner.run()

        assertEquals(DownloadFailure.NoSpace.name, row().error)
    }

    @Test
    fun aFailedDownloadComesBackWhenTheUserTriesAgain() = runTest {
        var allow = false
        movieOnServer(download = { if (allow) Reply.ranged(body, it) else Reply.status(HttpStatusCode.Forbidden) })
        queue()
        app.runner.run()

        allow = true
        app.downloadRepository.resume(alex, "m1")
        app.runner.run()

        assertEquals(DOWNLOAD_DONE, row().state)
    }

    @Test
    fun theMediaItemHasNoServerAddressesLeft() = runTest {
        movieOnServer()
        queue()
        app.runner.run()

        val item = OfflineSnapshot.decode(app.downloads.findItem("srv-1", "u1", "m1")!!.snapshotJson).mediaItem(app.files)

        assertTrue(listOfNotNull(item.posterUrl, item.backdropUrl, item.landscapeUrl, item.logoUrl).all { it.startsWith("file:///fake/") })
    }

    // Waiting inside the run, so the foreground service stays up.

    @Test
    fun withPatienceTheRunWaitsForTheNetworkAndThenDownloads() = runTest {
        movieOnServer()
        queue()
        app.network.set(NetworkState.None)

        val run = async { app.runner.run(patience = 1.hours) }
        runCurrent()
        assertEquals(DOWNLOAD_WAITING_FOR_NETWORK, row().state)
        app.network.set(NetworkState.Unmetered)

        assertEquals(DownloadRunner.Next.Done, run.await())
        assertEquals(DOWNLOAD_DONE, row().state)
    }

    @Test
    fun theRunSaysWhatItIsWaitingFor() = runTest {
        movieOnServer()
        queue()
        app.network.set(NetworkState.None)
        val seen = mutableListOf<DownloadRunner.Waiting?>()

        val run = async { app.runner.run(patience = 1.hours) { seen += it.waiting } }
        runCurrent()
        app.network.set(NetworkState.Unmetered)
        run.await()

        assertEquals(listOf(DownloadRunner.Waiting.Network), seen.filterNotNull().distinct())
    }

    private class RecordingAlerts : DownloadAlerts {
        val finished = mutableListOf<Pair<ProfileRef, List<CompletedDownload>>>()
        val failed = mutableListOf<Triple<ProfileRef, String, DownloadFailure>>()

        override fun finished(profile: ProfileRef, downloads: List<CompletedDownload>) {
            finished += profile to downloads
        }

        override fun failed(profile: ProfileRef, title: String, failure: DownloadFailure) {
            failed += Triple(profile, title, failure)
        }
    }

    private fun runnerAlerting(alerts: DownloadAlerts) = DownloadRunner(
        app.downloads, app.database.accountDao(), app.connection, app.files, app.network, app.wake, app.clock, speeds = app.speeds, alerts = alerts,
    )

    @Test
    fun aQueueThatIsDoneIsReportedOnceForTheProfile() = runTest {
        movieOnServer()
        queue()
        val alerts = RecordingAlerts()

        runnerAlerting(alerts).run()

        assertEquals(listOf(alex to listOf(CompletedDownload("Heat", null))), alerts.finished)
        assertTrue(alerts.failed.isEmpty())
    }

    @Test
    fun aFailedDownloadIsReportedWithItsTitleAndReasonAndIsNotCountedAsFinished() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsPage(downloadItemJson("m1", "Heat", size = body.length.toLong(), videoCodec = "av1"))))
        queue()
        val alerts = RecordingAlerts()
        val runner = DownloadRunner(
            app.downloads, app.database.accountDao(), app.connection, app.files, app.network, app.wake, app.clock, speeds = app.speeds,
            playable = object : PlayableVideo {
                override fun canPlay(codec: String) = false
            },
            alerts = alerts,
        )

        runner.run()

        assertEquals(listOf(Triple(alex, "Heat", DownloadFailure.Unplayable)), alerts.failed)
        assertTrue(alerts.finished.isEmpty())
    }

    @Test
    fun severalDownloadsAreOneReportWithTheirCount() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items") { request -> Reply.json(itemsPage(downloadItemJson(request.query["ids"]!!, size = 3, primaryTag = null))) }
        server.on("GET", "/Items/a/Download") { Reply.ranged("aaa", it) }
        server.on("GET", "/Items/b/Download") { Reply.ranged("bbb", it) }
        queue(DownloadTarget.Item("a"))
        app.clock.advance(1.seconds)
        queue(DownloadTarget.Item("b"))
        val alerts = RecordingAlerts()

        runnerAlerting(alerts).run()

        assertEquals(listOf(alex to listOf("a", "b")), alerts.finished.map { (profile, downloads) -> profile to downloads.map { it.title } })
    }

    @Test
    fun anEpisodeIsReportedWithItsShow() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items") { request ->
            when (request.query["ids"]) {
                "e1" -> Reply.json(itemsPage(downloadItemJson("e1", "Pilot", "Episode", seriesId = "s1", seriesName = "Saga", seasonId = "se1", seasonNumber = 1, episodeNumber = 1)))
                else -> Reply.json(itemsPage(parentJson("s1", "Saga", "Series"), parentJson("se1", "Season 1", "Season", number = 1, seriesId = "s1")))
            }
        }
        server.on("GET", "/Items/e1/Download") { Reply.ranged(body, it) }
        server.on("GET", "/Items/e1/Images/Primary", Reply.file("still"))
        server.on("GET", "/Items/s1/Images/Primary", Reply.file("show-poster"))
        server.on("GET", "/Items/se1/Images/Primary", Reply.file("season-poster"))
        queue(DownloadTarget.Item("e1"))
        val alerts = RecordingAlerts()

        runnerAlerting(alerts).run()

        assertEquals(listOf(alex to listOf(CompletedDownload("Pilot", "Saga"))), alerts.finished)
    }

    @Test
    fun aPauseByTheUserReportsNothing() = runTest {
        val server = movieOnServer()
        queue()
        val gate = server.hold("GET", "/Items/m1/Download")
        val alerts = RecordingAlerts()

        val run = async { runnerAlerting(alerts).run() }
        runCurrent()
        app.downloadRepository.pause(alex, "m1")
        withContext(Dispatchers.Default) { delay(200) }
        gate.open()
        run.await()

        assertTrue(alerts.finished.isEmpty())
        assertTrue(alerts.failed.isEmpty())
    }

    @Test
    fun aProfileWithDownloadNotificationsOffGetsNoReports() = runTest {
        movieOnServer()
        queue()
        app.profileSettings.setDownloadNotifications(false)
        val alerts = RecordingAlerts()

        runnerAlerting(alerts).run()

        assertTrue(alerts.finished.isEmpty())
        assertTrue(alerts.failed.isEmpty())
    }

    @Test
    fun aQueueSplitAcrossRunsIsReportedOnceWhenItIsDone() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items") { request -> Reply.json(itemsPage(downloadItemJson(request.query["ids"]!!, size = 3, primaryTag = null))) }
        server.on("GET", "/Items/a/Download") { Reply.ranged("aaa", it) }
        var secondCalls = 0
        server.on("GET", "/Items/b/Download") { if (++secondCalls == 1) Reply.status(HttpStatusCode.ServiceUnavailable) else Reply.ranged("bbb", it) }
        queue(DownloadTarget.Item("a"))
        app.clock.advance(1.seconds)
        queue(DownloadTarget.Item("b"))
        val alerts = RecordingAlerts()
        val runner = runnerAlerting(alerts)

        val first = runner.run()
        val none = alerts.finished.toList()
        val second = runner.run()

        assertEquals(DownloadRunner.Next.Retry, first)
        assertTrue(none.isEmpty())
        assertEquals(DownloadRunner.Next.Done, second)
        assertEquals(listOf(alex to listOf("a", "b")), alerts.finished.map { (profile, downloads) -> profile to downloads.map { it.title } })
    }

    @Test
    fun pausingTheRestOfAQueueReportsNothingForTheOnesThatFinished() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items") { request -> Reply.json(itemsPage(downloadItemJson(request.query["ids"]!!, size = 3, primaryTag = null))) }
        server.on("GET", "/Items/a/Download") { Reply.ranged("aaa", it) }
        server.on("GET", "/Items/b/Download") { Reply.ranged("bbb", it) }
        queue(DownloadTarget.Item("a"))
        app.clock.advance(1.seconds)
        queue(DownloadTarget.Item("b"))
        val gate = server.hold("GET", "/Items/b/Download")
        val alerts = RecordingAlerts()

        val run = async { runnerAlerting(alerts).run() }
        runCurrent()
        app.downloadRepository.pause(alex, "b")
        withContext(Dispatchers.Default) { delay(200) }
        gate.open()
        run.await()

        assertTrue(alerts.finished.isEmpty())
    }

    private fun runnerRefusing(refused: String) = DownloadRunner(
        app.downloads, app.database.accountDao(), app.connection, app.files, app.network, app.wake, app.clock, speeds = app.speeds,
        playable = object : PlayableVideo {
            override fun canPlay(codec: String) = codec != refused
        },
    )

    @Test
    fun aFinishedVideoIsKeptAsTheServerSentIt() = runTest {
        movieOnServer()
        queue()

        app.runner.run()

        val done = row()
        assertEquals(DOWNLOAD_DONE, done.state)
        assertEquals("$folder/video.mkv", done.filePath)
        assertEquals(body, app.files.text("$folder/video.mkv"))
        assertNull(app.files.text("$folder/video.part"))
    }

    @Test
    fun aCodecTheDeviceCannotPlayIsRefusedBeforeAnyBytesAreFetched() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsPage(downloadItemJson("m1", "Heat", size = body.length.toLong(), videoCodec = "av1"))))
        server.on("GET", "/Items/m1/Download") { Reply.ranged(body, it) }
        queue()

        runnerRefusing("av1").run()

        assertEquals(DownloadFailure.Unplayable.name, row().error)
        assertTrue(app.jellyfin.requestsTo("GET", "/Items/m1/Download").isEmpty(), "the video was never asked for")
    }

    /** A platform transfer that reports [steps] chunks of [step] bytes, moving [clockStep] on the test clock before each, and leaves a small file at the part path. */
    private inner class FakeExternalTransfer(
        val steps: Int,
        val step: Long,
        val clockStep: kotlin.time.Duration = kotlin.time.Duration.ZERO,
        val total: Long? = steps * step,
    ) : ExternalVideoTransfer {
        override suspend fun transfer(
            request: ExternalTransferRequest,
            stop: kotlinx.coroutines.flow.StateFlow<Boolean>,
            onProgress: suspend (done: Long, total: Long?) -> Unit,
        ): ExternalTransferResult {
            app.files.open(request.partPath, append = false).also { it.write("x".encodeToByteArray(), 1) }.close()
            for (i in 1..steps) {
                app.clock.advance(clockStep)
                onProgress(i * step, total)
            }
            return ExternalTransferResult.Finished
        }
    }

    private fun externalRunner(transfer: ExternalVideoTransfer) = DownloadRunner(app.downloads, app.database.accountDao(), app.connection, app.files, app.network, app.wake, app.clock, speeds = app.speeds, external = transfer)

    @Test
    fun aPlatformTransferReportsOncePerStepOfBytesNotOncePerChunk() = runTest {
        movieOnServer()
        queue()
        val seen = mutableListOf<DownloadRunner.Progress>()

        // 1,000 chunks of 10 KB: 10 MB, which is five 2 MB steps.
        externalRunner(FakeExternalTransfer(steps = 1_000, step = 10_000)).run { seen += it }

        val transferring = seen.filter { it.bytesDone > 0 }
        assertTrue(transferring.size < 20, "reported ${transferring.size} times")
        assertEquals(10_000_000L, transferring.last().bytesDone, "the last report is the full count")
    }

    @Test
    fun aPlatformTransferIsReportedBeforeItsFirstBytesSoTheSystemCanShowItAtOnce() = runTest {
        movieOnServer()
        queue()
        val seen = mutableListOf<DownloadRunner.Progress>()
        var seenWhenTheTransferStarted: List<DownloadRunner.Progress> = emptyList()
        val transfer = object : ExternalVideoTransfer {
            override suspend fun transfer(
                request: ExternalTransferRequest,
                stop: kotlinx.coroutines.flow.StateFlow<Boolean>,
                onProgress: suspend (done: Long, total: Long?) -> Unit,
            ): ExternalTransferResult {
                seenWhenTheTransferStarted = seen.toList()
                app.files.open(request.partPath, append = false).also { it.write("x".encodeToByteArray(), 1) }.close()
                return ExternalTransferResult.Finished
            }
        }

        externalRunner(transfer).run { seen += it }

        assertEquals("Heat", seenWhenTheTransferStarted.single().title)
        assertEquals(0L, seenWhenTheTransferStarted.single().bytesDone)
    }

    @Test
    fun aPlatformTransferAlsoReportsWhenHalfASecondHasPassed() = runTest {
        movieOnServer()
        queue()
        val seen = mutableListOf<DownloadRunner.Progress>()

        // 20 chunks of 1 KB, 600 ms apart: far below the byte step, so only time makes them report.
        externalRunner(FakeExternalTransfer(steps = 20, step = 1_000, clockStep = 600.milliseconds)).run { seen += it }

        assertEquals(20, seen.count { it.bytesDone > 0 })
    }

    @Test
    fun theLastBytesOfAPlatformTransferAreReportedEvenBetweenSteps() = runTest {
        movieOnServer()
        queue()
        val seen = mutableListOf<DownloadRunner.Progress>()

        // 30 KB in 3 chunks, with no total known: nothing crosses a step, yet the end must be saved.
        externalRunner(FakeExternalTransfer(steps = 3, step = 10_000, total = null)).run { seen += it }

        assertEquals(30_000L, seen.last { it.bytesDone > 0 }.bytesDone)
    }

    @Test
    fun whenThePatienceRunsOutTheRunEndsAndSaysWhenToCome() = runTest {
        movieOnServer()
        queue()
        app.network.set(NetworkState.None)

        // The wait is in virtual time, so the run measures its patience with the test's clock too.
        val runner = DownloadRunner(app.downloads, app.database.accountDao(), app.connection, app.files, app.network, app.wake, app.clock, testTimeSource, app.speeds)

        val next = runner.run(patience = 10.minutes)

        assertEquals(DownloadRunner.Next.WhenConnected, next)
        assertEquals(DOWNLOAD_WAITING_FOR_NETWORK, row().state)
    }

    @Test
    fun aServerThatFailsOnceIsTriedAgainWithoutEndingTheRun() = runTest {
        var calls = 0
        movieOnServer(download = { if (++calls == 1) Reply.status(HttpStatusCode.ServiceUnavailable) else Reply.ranged(body, it) })
        queue()

        val next = app.runner.run(patience = 10.minutes)

        assertEquals(DownloadRunner.Next.Done, next)
        assertEquals(DOWNLOAD_DONE, row().state)
        assertEquals(2, app.jellyfin.requestsTo("GET", "/Items/m1/Download").size)
    }

    @Test
    fun aLostConnectionInTheMiddleIsResumedInTheSameRun() = runTest {
        var calls = 0
        movieOnServer(download = { if (++calls == 1) Reply.rangedThenDrop(body, it, dropAfter = 4) else Reply.ranged(body, it) })
        queue()

        val next = app.runner.run(patience = 10.minutes)

        assertEquals(DownloadRunner.Next.Done, next)
        assertEquals(body, app.files.text("$folder/video.mkv"))
        assertEquals(listOf(null, "bytes=4-"), app.jellyfin.requestsTo("GET", "/Items/m1/Download").map { it.range })
    }

    @Test
    fun onMobileDataTheRunWaitsForWifiAndAChangeOfSettingWakesIt() = runTest {
        movieOnServer()
        queue()
        app.network.set(NetworkState.Metered)

        val run = async { app.runner.run(patience = 1.hours) }
        runCurrent()
        assertEquals(DOWNLOAD_WAITING_FOR_NETWORK, row().state)
        app.profileSettings.setDownloadOverMobile(true)
        app.wake.wake()

        assertEquals(DownloadRunner.Next.Done, run.await())
        assertEquals(DOWNLOAD_DONE, row().state)
    }

    @Test
    fun aDownloadAddedWhileTheRunWaitsIsStartedByTheSameRun() = runTest {
        val server = movieOnServer()
        app.network.set(NetworkState.None)
        queue()

        val run = async { app.runner.run(patience = 1.hours) }
        runCurrent()
        app.network.set(NetworkState.Unmetered)
        server.on("GET", "/Items/m1/Download") { Reply.ranged(body, it) }

        assertEquals(DownloadRunner.Next.Done, run.await())
        assertEquals(DOWNLOAD_DONE, row().state)
    }

    // How fast, and how long is left.

    @Test
    fun theRunReportsHowFastTheBytesComeIn() = runTest {
        movieOnServer()
        queue()
        // Ten bytes arrive over two seconds.
        app.files.onWrite = { app.clock.advance(2.seconds) }
        val speeds = mutableListOf<Long?>()

        app.runner.run { speeds += it.bytesPerSecond }

        assertTrue(speeds.contains(5L), "10 bytes in 2 s is 5 B/s: $speeds")
        assertTrue(app.speeds.speeds.value.isEmpty(), "a finished download has no speed")
    }

    @Test
    fun theTimeLeftIsWhatIsLeftOverTheSpeed() {
        assertEquals(20.seconds, DownloadRunner.Progress("m1", "Heat", bytesDone = 100, bytesTotal = 300, remaining = 1, bytesPerSecond = 10).timeLeft)
        assertNull(DownloadRunner.Progress("m1", "Heat", bytesDone = 100, bytesTotal = 300, remaining = 1).timeLeft, "no speed yet")
        assertNull(DownloadRunner.Progress("m1", "Heat", bytesDone = 100, bytesTotal = null, remaining = 1, bytesPerSecond = 10).timeLeft, "no size yet")
    }
}
