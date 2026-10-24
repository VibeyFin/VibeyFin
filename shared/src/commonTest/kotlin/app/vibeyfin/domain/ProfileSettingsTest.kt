package app.vibeyfin.domain

import app.vibeyfin.testing.TestApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ProfileSettingsTest {
    private val app = TestApp()

    @AfterTest
    fun tearDown() = runTest { app.close() }

    @Test
    fun `the skip settings are null with nobody signed in`() = runTest {
        assertNull(app.profileSettings.skipBackSeconds.first())
        assertNull(app.profileSettings.skipForwardSeconds.first())
        assertNull(app.profileSettings.autoSkipSegments.first())
    }

    @Test
    fun `a new profile skips 10 seconds each way and does not auto-skip`() = runTest {
        app.signedIn()

        assertEquals(10, app.profileSettings.skipBackSeconds.first())
        assertEquals(10, app.profileSettings.skipForwardSeconds.first())
        assertEquals(false, app.profileSettings.autoSkipSegments.first())
    }

    @Test
    fun `a skip length from the choices is saved`() = runTest {
        app.signedIn()

        app.profileSettings.setSkipBackSeconds(15)
        app.profileSettings.setSkipForwardSeconds(45)
        app.profileSettings.setAutoSkipSegments(true)

        assertEquals(15, app.profileSettings.skipBackSeconds.first())
        assertEquals(45, app.profileSettings.skipForwardSeconds.first())
        assertEquals(true, app.profileSettings.autoSkipSegments.first())
        app.profileSettings.setAutoSkipSegments(false)
        assertFalse(app.profileSettings.autoSkipSegments.first()!!)
    }

    @Test
    fun `a skip length outside the choices changes nothing`() = runTest {
        app.signedIn()

        app.profileSettings.setSkipBackSeconds(7)
        app.profileSettings.setSkipForwardSeconds(5)

        assertEquals(10, app.profileSettings.skipBackSeconds.first())
        assertEquals(10, app.profileSettings.skipForwardSeconds.first())
    }

    @Test
    fun `a stored skip length outside the choices reads as the default`() = runTest {
        app.signedIn()

        app.database.accountDao().setSkipBackSeconds("srv-1", "u1", 7)
        app.database.accountDao().setSkipForwardSeconds("srv-1", "u1", 5)

        assertEquals(ProfileSettings.DEFAULT_SKIP_BACK_SECONDS, app.profileSettings.skipBackSeconds.first())
        assertEquals(ProfileSettings.DEFAULT_SKIP_FORWARD_SECONDS, app.profileSettings.skipForwardSeconds.first())
    }

    @Test
    fun `buffer ahead is null with nobody signed in and 20 seconds for a new profile`() = runTest {
        assertNull(app.profileSettings.bufferAheadSeconds.first())
        app.signedIn()
        assertEquals(20, app.profileSettings.bufferAheadSeconds.first())
    }

    @Test
    fun `a buffer length from the choices is saved and another is ignored`() = runTest {
        app.signedIn()

        app.profileSettings.setBufferAheadSeconds(40)
        assertEquals(40, app.profileSettings.bufferAheadSeconds.first())
        app.profileSettings.setBufferAheadSeconds(0)
        assertEquals(0, app.profileSettings.bufferAheadSeconds.first())
        app.profileSettings.setBufferAheadSeconds(25)
        assertEquals(0, app.profileSettings.bufferAheadSeconds.first())
    }

    @Test
    fun `quality is Maximum for a new profile and a value outside the choices is ignored`() = runTest {
        app.signedIn()

        assertEquals(0, app.profileSettings.maxStreamingBitrate.first())
        app.profileSettings.setMaxStreamingBitrate(123)
        assertEquals(0, app.profileSettings.maxStreamingBitrate.first())
        app.profileSettings.setMaxStreamingBitrate(10_000_000)
        assertEquals(10_000_000, app.profileSettings.maxStreamingBitrate.first())
    }

    @Test
    fun `subtitle style defaults to the system style with medium white text over a shadow`() = runTest {
        assertNull(app.profileSettings.captionsFollowSystem.first())
        app.signedIn()

        assertEquals(true, app.profileSettings.captionsFollowSystem.first())
        assertEquals(1, app.profileSettings.subtitleSize.first())
        assertEquals(0, app.profileSettings.subtitleTextColor.first())
        assertEquals(1, app.profileSettings.subtitleBackground.first())
    }

    @Test
    fun `resetting the subtitle style puts every option back`() = runTest {
        app.signedIn()
        app.profileSettings.setCaptionsFollowSystem(false)
        app.profileSettings.setSubtitleSize(3)
        app.profileSettings.setSubtitleTextColor(2)
        app.profileSettings.setSubtitleBackground(2)
        app.profileSettings.setSubtitleSize(9)
        assertEquals(3, app.profileSettings.subtitleSize.first())

        app.profileSettings.resetSubtitleStyle()

        assertEquals(true, app.profileSettings.captionsFollowSystem.first())
        assertEquals(1, app.profileSettings.subtitleSize.first())
        assertEquals(0, app.profileSettings.subtitleTextColor.first())
        assertEquals(1, app.profileSettings.subtitleBackground.first())
    }

    @Test
    fun `out of range subtitle colour and background are ignored when written`() = runTest {
        app.signedIn()
        app.profileSettings.setSubtitleTextColor(2)
        app.profileSettings.setSubtitleBackground(2)

        app.profileSettings.setSubtitleTextColor(3)
        app.profileSettings.setSubtitleTextColor(-1)
        app.profileSettings.setSubtitleBackground(3)
        app.profileSettings.setSubtitleBackground(-1)

        assertEquals(2, app.profileSettings.subtitleTextColor.first())
        assertEquals(2, app.profileSettings.subtitleBackground.first())
    }

    @Test
    fun `out of range stored subtitle values read as the defaults`() = runTest {
        app.signedIn()
        val dao = app.database.accountDao()
        dao.setSubtitleSize("srv-1", "u1", 9)
        dao.setSubtitleTextColor("srv-1", "u1", 9)
        dao.setSubtitleBackground("srv-1", "u1", 9)

        assertEquals(ProfileSettings.DEFAULT_SUBTITLE_SIZE, app.profileSettings.subtitleSize.first())
        assertEquals(ProfileSettings.DEFAULT_SUBTITLE_TEXT_COLOR, app.profileSettings.subtitleTextColor.first())
        assertEquals(ProfileSettings.DEFAULT_SUBTITLE_BACKGROUND, app.profileSettings.subtitleBackground.first())
    }
}
