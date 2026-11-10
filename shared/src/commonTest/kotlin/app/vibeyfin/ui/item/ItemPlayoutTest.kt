package app.vibeyfin.ui.item

import app.vibeyfin.domain.PlayMethod
import app.vibeyfin.testing.HLS_PATH
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import app.vibeyfin.testing.mediaSourceJson
import app.vibeyfin.testing.playbackInfoJson
import app.vibeyfin.testing.sagaServer
import app.vibeyfin.ui.downloads.DownloadBadge
import app.vibeyfin.ui.downloads.badge
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ItemPlayoutTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        Dispatchers.resetMain()
        app.close()
    }

    private suspend fun page(playbackInfo: Reply) = app.signedIn().also {
        it.on("GET", "/Items", Reply.json(itemsJson(itemJson("m1", "Caminandes"))))
        it.on("POST", "/Items/m1/PlaybackInfo", playbackInfo)
    }.let { viewModels.track(ItemViewModel("m1", app.library, app.connection, app.watch, app.watchChanges, app.downloadRepository, app.playback, app.seerrRepository, app.seerr, app.subtitles, app.profileSettings)) }

    @Test
    fun aFileTheDeviceCanPlayIsShownAsDirectPlay() = runTest {
        val viewModel = page(Reply.json(playbackInfoJson(mediaSourceJson(container = "mp4"))))

        val state = viewModel.uiState.first { it.playout != null }

        assertEquals(Playout(PlayMethod.DirectPlay), state.playout?.video)
        assertEquals("Direct play", state.technical.first().value)
    }

    @Test
    fun aTranscodeSaysWhyInPlainWords() = runTest {
        val source = mediaSourceJson(container = "mkv", directPlay = false, directStream = false, transcodingUrl = "$HLS_PATH&TranscodeReasons=AudioCodecNotSupported")
        val viewModel = page(Reply.json(playbackInfoJson(source)))

        val playout = viewModel.uiState.first { it.playout != null }.playout

        assertNull(playout?.video, "the video isn't what is converted")
        assertEquals(PlayMethod.Transcode, playout?.audio?.method)
        assertEquals("The audio format isn't supported", playout?.audio?.reason)
    }

    @Test
    fun aVideoProblemTagsTheVideoAndAnAudioProblemTagsTheAudioToo() = runTest {
        val source = mediaSourceJson(container = "mkv", directPlay = false, directStream = false, transcodingUrl = "$HLS_PATH&TranscodeReasons=VideoCodecNotSupported,AudioCodecNotSupported")
        val viewModel = page(Reply.json(playbackInfoJson(source)))

        val playout = viewModel.uiState.first { it.playout != null }.playout

        assertEquals(Playout(PlayMethod.Transcode, "The video format isn't supported"), playout?.video)
        assertEquals(Playout(PlayMethod.Transcode, "The audio format isn't supported"), playout?.audio)
    }

    @Test
    fun aDirectStreamHasNothingToExplain() = runTest {
        val viewModel = page(Reply.json(playbackInfoJson(mediaSourceJson(container = "mkv", directPlay = false, directStream = true, transcodingUrl = "$HLS_PATH&TranscodeReasons=ContainerNotSupported"))))

        val playout = viewModel.uiState.first { it.playout != null }.playout

        assertEquals(PlayoutTags(video = Playout(PlayMethod.DirectStream)), playout)
    }

    @Test
    fun noAnswerLeavesTheLineOut() = runTest {
        val viewModel = page(Reply.status(HttpStatusCode.InternalServerError))

        val state = viewModel.uiState.first { !it.loading }

        assertNull(state.playout)
    }

    @Test
    fun aDownloadIsNotAskedAboutBecauseItPlaysFromTheDevice() = runTest {
        val server = app.sagaServer()
        server.on("POST", "/Items/m1/PlaybackInfo", Reply.json(playbackInfoJson(mediaSourceJson(container = "mp4"))))

        fun open() = viewModels.track(ItemViewModel("m1", app.library, app.connection, app.watch, app.watchChanges, app.downloadRepository, app.playback, app.seerrRepository, app.seerr, app.subtitles, app.profileSettings))
        val first = open()
        first.uiState.first { !it.loading }
        first.onDownload()
        first.uiState.first { it.download != null }
        app.downloads.complete("srv-1", "u1", "m1", 50, "srv-1/u1/m1/video.mp4", 5)
        first.uiState.first { it.downloads["m1"]?.badge == DownloadBadge.Done }
        app.jellyfin.requests.clear()

        val second = open()
        val state = second.uiState.first { !it.loading }

        assertNull(state.playout)
        assertTrue(app.jellyfin.requests.none { it.path.endsWith("/PlaybackInfo") }, "a download is not negotiated")
    }
}
