package app.vibeyfin.ui.subtitles

import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.item.DeletableSubtitle
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
import kotlin.time.Duration.Companion.seconds

/** Deleting a subtitle file from a title's Technical info: who sees it, which tracks it lists, and what confirming does. */
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleDeleteEntryTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()

    private val embedded = """{"Index":1,"Type":"Subtitle","DisplayTitle":"English - SUBRIP","IsExternal":false}"""
    private val french = """{"Index":2,"Type":"Subtitle","DisplayTitle":"French - SUBRIP - External","IsExternal":true}"""

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

    private fun FakeServer.film(vararg streams: String) {
        on("GET", "/Items", Reply.json("""{"Items":[{"Id":"m1","Name":"Film","Type":"Movie","MediaStreams":[${streams.joinToString(",")}]}],"TotalRecordCount":1}"""))
    }

    private suspend fun signedIn(admin: Boolean, vararg streams: String): FakeServer {
        val server = app.signedIn()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex","Policy":{"IsAdministrator":$admin}}"""))
        server.film(*streams)
        return server
    }

    private suspend fun ItemViewModel.offered(): Boolean {
        uiState.first { !it.loading }
        return withTimeoutOrNull(1.seconds) { uiState.first { it.offersSubtitleDelete } } != null
    }

    @Test
    fun anAdminIsOfferedOnlyTheExternalTracksUnderSubtitles() = runTest {
        signedIn(admin = true, embedded, french)
        val state = page().uiState.first { it.offersSubtitleDelete }

        assertEquals(listOf(DeletableSubtitle(2, "French · SUBRIP · External")), state.deletableSubtitles)
        assertEquals(listOf("Subtitles"), state.technical.filter { it.deletesSubtitles }.map { it.label })
    }

    @Test
    fun aTitleWithOnlyEmbeddedSubtitlesHasNothingToDelete() = runTest {
        signedIn(admin = true, embedded)

        assertFalse(page().offered())
    }

    @Test
    fun aUserWhoIsNotAnAdminIsNotOfferedIt() = runTest {
        signedIn(admin = false, embedded, french)

        assertFalse(page().offered())
    }

    @Test
    fun confirmingDeletesTheTrackAndReadsTheTitleAgain() = runTest {
        val server = signedIn(admin = true, embedded, french)
        server.on("DELETE", "/Videos/m1/Subtitles/2", Reply.noContent())
        val page = page()
        page.uiState.first { it.offersSubtitleDelete }

        page.onDeleteSubtitle(2)
        assertEquals(DeletableSubtitle(2, "French · SUBRIP · External"), page.uiState.value.subtitleDeletePrompt)
        server.film(embedded)
        page.onConfirmDeleteSubtitle()
        val after = page.uiState.first { it.deletableSubtitles.isEmpty() }

        assertEquals(1, app.jellyfin.requestsTo("DELETE", "/Videos/m1/Subtitles/2").size)
        assertNull(after.subtitleDeletePrompt)
        assertFalse(after.offersSubtitleDelete)
        assertEquals("French · SUBRIP · External deleted", after.notice)
    }

    @Test
    fun cancellingDeletesNothing() = runTest {
        signedIn(admin = true, embedded, french)
        val page = page()
        page.uiState.first { it.offersSubtitleDelete }

        page.onDeleteSubtitle(2)
        page.onCancelDeleteSubtitle()

        assertNull(page.uiState.value.subtitleDeletePrompt)
        assertTrue(app.jellyfin.requestsTo("DELETE", "/Videos/m1/Subtitles/2").isEmpty())
    }

    @Test
    fun aDeleteThatFailsKeepsTheTrackAndSaysSo() = runTest {
        val server = signedIn(admin = true, embedded, french)
        server.on("DELETE", "/Videos/m1/Subtitles/2", Reply.status(HttpStatusCode.ServiceUnavailable))
        val page = page()
        page.uiState.first { it.offersSubtitleDelete }

        page.onDeleteSubtitle(2)
        page.onConfirmDeleteSubtitle()
        val after = page.uiState.first { it.actionMessage != null }

        assertEquals(LoadMessages.SUBTITLE_DELETE_FAILED, after.actionMessage)
        assertEquals(listOf(2), after.deletableSubtitles.map { it.streamIndex })
    }

    @Test
    fun aRefusedDeleteSaysTheAccountMayNotAndHidesTheEntry() = runTest {
        val server = signedIn(admin = true, embedded, french)
        server.on("DELETE", "/Videos/m1/Subtitles/2", Reply.status(HttpStatusCode.Forbidden))
        val page = page()
        page.uiState.first { it.offersSubtitleDelete }

        page.onDeleteSubtitle(2)
        page.onConfirmDeleteSubtitle()
        val after = page.uiState.first { it.notice != null && !it.offersSubtitleDelete }

        assertEquals(LoadMessages.SUBTITLE_DELETE_NOT_ALLOWED, after.notice)
        assertNull(after.actionMessage)
    }

    @Test
    fun aTrackThatWasAlreadyDeletedIsShownGoneAndSaysSo() = runTest {
        val server = signedIn(admin = true, embedded, french)
        server.on("DELETE", "/Videos/m1/Subtitles/2", Reply.status(HttpStatusCode.BadRequest))
        val page = page()
        page.uiState.first { it.offersSubtitleDelete }

        page.onDeleteSubtitle(2)
        server.film(embedded)
        page.onConfirmDeleteSubtitle()
        val after = page.uiState.first { it.notice != null }

        assertEquals(LoadMessages.SUBTITLE_ALREADY_GONE, after.notice)
        assertNull(after.actionMessage)
        assertTrue(after.deletableSubtitles.isEmpty())
    }

    @Test
    fun tryAgainDeletesTheTrackAgainAndLeavesTheWatchedMarkAlone() = runTest {
        val server = signedIn(admin = true, embedded, french)
        server.on("DELETE", "/Videos/m1/Subtitles/2", Reply.status(HttpStatusCode.ServiceUnavailable))
        val page = page()
        page.uiState.first { it.offersSubtitleDelete }
        page.onDeleteSubtitle(2)
        page.onConfirmDeleteSubtitle()
        page.uiState.first { it.actionMessage != null }

        server.on("DELETE", "/Videos/m1/Subtitles/2", Reply.noContent())
        server.film(embedded)
        page.onRetryAction()
        val after = page.uiState.first { it.deletableSubtitles.isEmpty() }

        assertEquals(2, app.jellyfin.requestsTo("DELETE", "/Videos/m1/Subtitles/2").size)
        assertTrue(app.jellyfin.requestsTo("POST", "/UserPlayedItems/m1").isEmpty())
        assertNull(after.actionMessage)
    }

    @Test
    fun aRejectedTokenOnDeleteEndsTheSession() = runTest {
        val server = signedIn(admin = true, embedded, french)
        server.on("DELETE", "/Videos/m1/Subtitles/2", Reply.status(HttpStatusCode.Unauthorized))
        val page = page()
        page.uiState.first { it.offersSubtitleDelete }

        page.onDeleteSubtitle(2)
        page.onConfirmDeleteSubtitle()

        assertNotNull(page.uiState.first { it.exit != null }.exit)
    }
}
