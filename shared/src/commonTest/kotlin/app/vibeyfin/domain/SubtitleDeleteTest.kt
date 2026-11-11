package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Who may delete a subtitle file from a title on the server, and what deleting one sends. */
class SubtitleDeleteTest {
    private val app = TestApp()

    @AfterTest
    fun tearDown() = runTest { app.close() }

    private suspend fun signedIn(admin: Boolean, managesSubtitles: Boolean): FakeServer {
        val server = app.signedIn()
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex","Policy":{"IsAdministrator":$admin,"EnableSubtitleManagement":$managesSubtitles}}"""))
        return server
    }

    @Test
    fun anAdminMayDeleteSubtitlesWithoutSubtitleManagement() = runTest {
        signedIn(admin = true, managesSubtitles = false)

        assertTrue(app.subtitles.deletable.first())
    }

    @Test
    fun subtitleManagementAloneIsNotEnough() = runTest {
        signedIn(admin = false, managesSubtitles = true)

        assertFalse(app.subtitles.deletable.first())
    }

    @Test
    fun deletingSendsTheTrackIndexToTheServer() = runTest {
        val server = signedIn(admin = true, managesSubtitles = false)
        server.on("DELETE", "/Videos/m1/Subtitles/3", Reply.noContent())

        val result = app.subtitles.delete("m1", 3)

        assertEquals(SubtitleDeletion.Deleted, result)
        assertEquals(1, app.jellyfin.requestsTo("DELETE", "/Videos/m1/Subtitles/3").size)
    }

    @Test
    fun aRefusedDeleteHidesDeletingForThatProfile() = runTest {
        val server = signedIn(admin = true, managesSubtitles = false)
        server.on("DELETE", "/Videos/m1/Subtitles/3", Reply.status(HttpStatusCode.Forbidden))

        val result = app.subtitles.delete("m1", 3)

        assertEquals(SubtitleDeletion.Failed(ApiError.Forbidden), result)
        assertFalse(app.subtitles.deletable.first())
    }

    @Test
    fun aTrackTheServerNoLongerHasIsAlreadyGone() = runTest {
        val server = signedIn(admin = true, managesSubtitles = false)
        server.on("DELETE", "/Videos/m1/Subtitles/7", Reply.status(HttpStatusCode.BadRequest))

        assertEquals(SubtitleDeletion.AlreadyGone, app.subtitles.delete("m1", 7))
        assertTrue(app.subtitles.deletable.first())
    }

    @Test
    fun aKidsProfileMayNotDeleteSubtitles() = runTest {
        signedIn(admin = true, managesSubtitles = false)
        app.profileSettings.setKidsProfile(true)

        assertFalse(app.subtitles.deletable.first())
    }

    @Test
    fun offlineThereIsNothingToDelete() = runTest {
        signedIn(admin = true, managesSubtitles = false)
        app.offline.setManual(true)

        assertFalse(app.subtitles.deletable.first())
    }
}
