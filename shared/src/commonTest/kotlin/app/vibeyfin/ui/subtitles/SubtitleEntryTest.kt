package app.vibeyfin.ui.subtitles

import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import app.vibeyfin.ui.item.ItemViewModel
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Who sees "Search for subtitles" on a title's page, and what the page does once one is added. */
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleEntryTest {
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

    private fun page() = viewModels.track(ItemViewModel("m1", app.library, app.connection, app.watch, app.watchChanges, app.downloadRepository, app.playback, app.seerrRepository, app.seerr, app.subtitles, app.profileSettings))

    private suspend fun signedIn(admin: Boolean, allowed: Boolean, type: String = "Movie"): FakeServer {
        val server = app.signedIn()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex","Policy":{"IsAdministrator":$admin,"EnableSubtitleManagement":$allowed}}"""))
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("m1", "Film", type = type, isFolder = type == "Series"))))
        return server
    }

    private suspend fun ItemViewModel.offered(): Boolean {
        uiState.first { !it.loading }
        return withTimeoutOrNull(1.seconds) { uiState.first { it.offersSubtitleSearch } } != null
    }

    @Test
    fun theEntryAppearsOnceAFailedPermissionCheckPasses() = runTest {
        val server = signedIn(admin = false, allowed = true)
        var failing = true
        server.on("GET", "/Users/Me") {
            if (failing) Reply.status(HttpStatusCode.ServiceUnavailable) else Reply.json("""{"Id":"u1","Name":"Alex","Policy":{"EnableSubtitleManagement":true}}""")
        }
        val page = page()

        assertFalse(page.offered())
        failing = false
        assertNotNull(withTimeoutOrNull(1.minutes) { page.uiState.first { it.offersSubtitleSearch } })
    }

    @Test
    fun aRefusedPermissionCheckIsNotAskedAgain() = runTest {
        val server = signedIn(admin = false, allowed = false)
        var reads = 0
        server.on("GET", "/Users/Me") {
            reads++
            Reply.json("""{"Id":"u1","Name":"Alex","Policy":{"EnableSubtitleManagement":false}}""")
        }
        val page = page()

        assertFalse(page.offered())
        val settled = reads
        assertNull(withTimeoutOrNull(1.minutes) { page.uiState.first { it.offersSubtitleSearch } })
        assertEquals(settled, reads)
    }

    @Test
    fun theEntrySitsInTheTechnicalInfoOnceOffered() = runTest {
        signedIn(admin = false, allowed = true)
        val state = page().uiState.first { it.offersSubtitleSearch }

        assertEquals(listOf("Subtitles"), state.technical.filter { it.searchesSubtitles }.map { it.label })
    }

    @Test
    fun theEntryOutlivesThePageLoadingAgain() = runTest {
        signedIn(admin = false, allowed = true)
        val page = page()

        assertTrue(page.offered())
        page.onRetry()
        page.uiState.first { !it.loading && it.item != null }
        assertTrue(page.offered())
    }

    @Test
    fun aNonAdminWithThePermissionSeesTheEntry() = runTest {
        signedIn(admin = false, allowed = true)
        assertTrue(page().offered())
    }

    @Test
    fun anAdminWithoutThePermissionDoesNot() = runTest {
        signedIn(admin = true, allowed = false)
        assertFalse(page().offered())
    }

    @Test
    fun anEpisodeHasTheEntry() = runTest {
        signedIn(admin = false, allowed = true, type = "Episode")
        assertTrue(page().offered())
    }

    @Test
    fun aShowHasNoEntry() = runTest {
        signedIn(admin = false, allowed = true, type = "Series")
        assertFalse(page().offered())
    }

    @Test
    fun aKidsProfileHasNoEntry() = runTest {
        signedIn(admin = false, allowed = true)
        app.profileSettings.setKidsProfile(true)
        assertFalse(page().offered())
    }

    @Test
    fun offlineThereIsNoEntry() = runTest {
        signedIn(admin = false, allowed = true)
        app.offline.setManual(true)
        assertFalse(page().offered())
    }

    @Test
    fun addedSubtitlesReadTheItemAgainAndSaySo() = runTest {
        val server = signedIn(admin = false, allowed = true)
        val viewModel = page()
        viewModel.uiState.first { !it.loading }
        server.on(
            "GET",
            "/Items",
            Reply.json("""{"Items":[{"Id":"m1","Name":"Film","Type":"Movie","MediaStreams":[{"Index":3,"Type":"Subtitle","DisplayTitle":"English - External"}]}],"TotalRecordCount":1}"""),
        )

        viewModel.onSubtitleAdded(SubtitleAdded("m1", 3, "English"))
        val state = viewModel.uiState.first { it.item?.subtitleTracks?.isNotEmpty() == true }

        assertEquals("English subtitles added", state.notice)
    }
}
