package app.vibeyfin.ui.downloads

import app.vibeyfin.data.db.DOWNLOAD_PAUSED
import app.vibeyfin.data.db.DOWNLOAD_QUEUED
import app.vibeyfin.domain.Download
import app.vibeyfin.domain.DownloadAttempt
import app.vibeyfin.domain.DownloadState
import app.vibeyfin.domain.DownloadTarget
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.sagaServer
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadFlowTest {
    private val app = TestApp()
    private val alex = ProfileRef("srv-1", "u1")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        Dispatchers.resetMain()
        app.close()
    }

    private suspend fun queuedShow(): List<Download> {
        val plan = (app.downloadRepository.plan(alex, DownloadTarget.Series("s1")) as DownloadAttempt.Success).value
        app.downloadRepository.enqueue(alex, plan)
        return app.downloadRepository.observeAll(alex).first { it.size == 2 }
    }

    @Test
    fun `asking to remove opens a prompt that counts the downloads`() = runTest {
        app.sagaServer()
        val flow = DownloadFlow(backgroundScope, app.downloadRepository) { alex }
        val items = queuedShow()

        flow.askRemove(items, "Saga")

        val prompt = assertNotNull(flow.removal.value)
        assertEquals("Saga", prompt.title)
        assertEquals(2, prompt.count)
    }

    @Test
    fun `confirming a removal deletes the downloads and closes the prompt`() = runTest {
        app.sagaServer()
        val flow = DownloadFlow(backgroundScope, app.downloadRepository) { alex }
        val items = queuedShow()
        flow.askRemove(items, "Saga")

        flow.confirmRemove()

        assertNull(flow.removal.value)
        app.downloadRepository.observeAll(alex).first { it.isEmpty() }
    }

    @Test
    fun `dismissing a removal keeps the downloads`() = runTest {
        app.sagaServer()
        val flow = DownloadFlow(backgroundScope, app.downloadRepository) { alex }
        val items = queuedShow()
        flow.askRemove(items, "Saga")

        flow.dismissRemoval()

        assertNull(flow.removal.value)
        assertEquals(DOWNLOAD_QUEUED, app.downloads.find("srv-1", "u1", "e1")!!.state)
    }

    @Test
    fun `nothing signed in means no permission is read`() = runTest {
        val flow = DownloadFlow(backgroundScope, app.downloadRepository) { null }

        assertEquals(false, flow.refreshCanDownload())
        assertEquals(true, flow.canDownload.value)
    }

    @Test
    fun `pause then resume then remove act on one download at once`() = runTest {
        app.sagaServer()
        val flow = DownloadFlow(backgroundScope, app.downloadRepository) { alex }
        val first = queuedShow().first { it.itemId == "e1" }

        flow.act(first, DownloadAction.Pause)
        app.downloadRepository.observeAll(alex).first { list -> list.any { it.itemId == "e1" && it.state == DownloadState.Paused } }
        assertEquals(DOWNLOAD_PAUSED, app.downloads.find("srv-1", "u1", "e1")!!.state)

        flow.act(first, DownloadAction.Resume)
        app.downloadRepository.observeAll(alex).first { list -> list.any { it.itemId == "e1" && it.state != DownloadState.Paused } }

        flow.act(first, DownloadAction.Remove)
        app.downloadRepository.observeAll(alex).first { list -> list.none { it.itemId == "e1" } }
        assertNull(flow.removal.value)
    }

    @Test
    fun `a user the server does not let download is read as such`() = runTest {
        val server = app.sagaServer()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex","Policy":{"EnableContentDownloading":false}}"""))
        val flow = DownloadFlow(backgroundScope, app.downloadRepository) { alex }

        assertEquals(true, flow.refreshCanDownload())

        assertEquals(false, flow.canDownload.value)
    }
}
