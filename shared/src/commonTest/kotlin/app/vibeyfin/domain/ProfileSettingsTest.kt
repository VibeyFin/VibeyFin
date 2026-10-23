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
}
