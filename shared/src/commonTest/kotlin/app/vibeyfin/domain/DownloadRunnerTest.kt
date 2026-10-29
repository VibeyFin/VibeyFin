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
import kotlinx.coroutines.test.testTimeSource
import kotlinx.coroutines.test.runTest
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

    /** Alex is signed in; the server has movie `m1` of [body] with a poster. */
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

    /** A platform converter that answers [answer], having written [output] at the target path when it converts. */
    private inner class FakeConverter(val answer: VideoConversion, val playable: (String) -> Boolean = { true }) : VideoConverter {
        var converted: Pair<String, String>? = null

        override suspend fun convert(source: String, target: String, stop: kotlinx.coroutines.flow.StateFlow<Boolean>, onProgress: suspend (Float) -> Unit): VideoConversion {
            converted = source to target
            onProgress(1f)
            if (answer == VideoConversion.Converted) app.files.open(target, append = false).also { it.write("mp4-bytes".encodeToByteArray(), 9) }.close()
            return answer
        }

        override fun canPlayVideo(codec: String) = playable(codec)
    }

    private fun runnerWith(converter: VideoConverter) =
        DownloadRunner(app.downloads, app.database.accountDao(), app.connection, app.files, app.network, app.wake, app.clock, speeds = app.speeds, converter = converter)

    @Test
    fun aVideoThePlayerCannotOpenIsReplacedByTheConvertedFile() = runTest {
        movieOnServer()
        queue()
        val converter = FakeConverter(VideoConversion.Converted)

        runnerWith(converter).run()

        assertEquals("$folder/video.part" to "$folder/video.converting", converter.converted)
        val done = row()
        assertEquals(DOWNLOAD_DONE, done.state)
        assertEquals("$folder/video.mp4", done.filePath)
        assertEquals("mp4-bytes", app.files.text("$folder/video.mp4"))
        assertNull(app.files.text("$folder/video.part"), "the original is not kept next to the copy")
        assertNull(app.files.text("$folder/video.converting"))
        assertEquals(9L, app.files.length("$folder/video.mp4"))
    }

    @Test
    fun aVideoThePlayerCanOpenIsKeptAsItCame() = runTest {
        movieOnServer()
        queue()

        runnerWith(FakeConverter(VideoConversion.NotNeeded)).run()

        assertEquals("$folder/video.mkv", row().filePath)
        assertEquals(body, app.files.text("$folder/video.mkv"))
    }

    @Test
    fun aVideoInACodecThePlayerCannotDecodeFailsAsUnplayable() = runTest {
        movieOnServer()
        queue()

        runnerWith(FakeConverter(VideoConversion.Unsupported)).run()

        assertEquals(DOWNLOAD_FAILED, row().state)
        assertEquals(DownloadFailure.Unplayable.name, row().error)
        assertNull(app.files.text("$folder/video.part"), "what was fetched is no use")
    }

    @Test
    fun aCodecTheDeviceCannotPlayIsRefusedBeforeAnyBytesAreFetched() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsPage(downloadItemJson("m1", "Heat", size = body.length.toLong(), videoCodec = "av1"))))
        server.on("GET", "/Items/m1/Download") { Reply.ranged(body, it) }
        queue()

        runnerWith(FakeConverter(VideoConversion.NotNeeded, playable = { it != "av1" })).run()

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

    private fun externalRunner(transfer: ExternalVideoTransfer) =
        DownloadRunner(app.downloads, app.database.accountDao(), app.connection, app.files, app.network, app.wake, app.clock, speeds = app.speeds, external = transfer)

    @Test
    fun aPlatformTransferReportsOncePerStepOfBytesNotOncePerChunk() = runTest {
        movieOnServer()
        queue()
        val seen = mutableListOf<DownloadRunner.Progress>()

        // 1,000 chunks of 10 KB: 10 MB, which is five 2 MB steps.
        externalRunner(FakeExternalTransfer(steps = 1_000, step = 10_000)).run { seen += it }

        val transferring = seen.filter { !it.converting && it.bytesDone > 0 }
        assertTrue(transferring.size < 20, "reported ${transferring.size} times")
        assertEquals(10_000_000L, transferring.last().bytesDone, "the last report is the full count")
    }

    @Test
    fun aPlatformTransferAlsoReportsWhenHalfASecondHasPassed() = runTest {
        movieOnServer()
        queue()
        val seen = mutableListOf<DownloadRunner.Progress>()

        // 20 chunks of 1 KB, 600 ms apart: far below the byte step, so only time makes them report.
        externalRunner(FakeExternalTransfer(steps = 20, step = 1_000, clockStep = 600.milliseconds)).run { seen += it }

        assertEquals(20, seen.count { it.bytesDone > 0 && !it.converting })
    }

    @Test
    fun theLastBytesOfAPlatformTransferAreReportedEvenBetweenSteps() = runTest {
        movieOnServer()
        queue()
        val seen = mutableListOf<DownloadRunner.Progress>()

        // 30 KB in 3 chunks, with no total known: nothing crosses a step, yet the end must be saved.
        externalRunner(FakeExternalTransfer(steps = 3, step = 10_000, total = null)).run { seen += it }

        assertEquals(30_000L, seen.last { !it.converting && it.bytesDone > 0 }.bytesDone)
    }

    @Test
    fun aFinishedVideoIsPublishedUnderTheTitleOfTheItem() = runTest {
        movieOnServer()
        queue()

        app.runner.run()

        val name = assertNotNull(app.files.published["$folder/video.mkv"])
        assertTrue(name.startsWith("Heat") && name.endsWith(".mkv"), name)
    }

    @Test
    fun aVideoThatIsNotFinishedIsNotPublished() = runTest {
        movieOnServer(download = { Reply.rangedThenDrop(body, it, dropAfter = 4) })
        queue()
        app.network.set(NetworkState.None)

        app.runner.run()

        assertTrue(app.files.published.isEmpty())
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
