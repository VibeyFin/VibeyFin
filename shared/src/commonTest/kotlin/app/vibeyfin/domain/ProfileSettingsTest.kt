package app.vibeyfin.domain

import app.vibeyfin.data.db.NotifiedItemEntity
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `picture in picture is null with nobody signed in and on for a new profile and saved when switched`() = runTest {
        assertNull(app.profileSettings.pictureInPicture.first())
        app.signedIn()
        assertEquals(true, app.profileSettings.pictureInPicture.first())

        app.profileSettings.setPictureInPicture(false)
        assertEquals(false, app.profileSettings.pictureInPicture.first())
    }

    @Test
    fun `swipe gestures are null with nobody signed in and on for a new profile and saved when switched`() = runTest {
        assertNull(app.profileSettings.swipeGestures.first())
        app.signedIn()
        assertEquals(true, app.profileSettings.swipeGestures.first())

        app.profileSettings.setSwipeGestures(false)
        assertEquals(false, app.profileSettings.swipeGestures.first())
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

    @Test
    fun `the notification settings are null with nobody signed in`() = runTest {
        assertNull(app.profileSettings.notificationsEnabled.first())
        assertNull(app.profileSettings.notificationScope.first())
    }

    @Test
    fun `a new profile has notifications off and the follow scope`() = runTest {
        app.signedIn()

        assertEquals(false, app.profileSettings.notificationsEnabled.first())
        assertEquals(0, app.profileSettings.notificationScope.first())
    }

    @Test
    fun `download notifications are null with nobody signed in and on for a new profile`() = runTest {
        assertNull(app.profileSettings.downloadNotifications.first())

        app.signedIn()

        assertEquals(true, app.profileSettings.downloadNotifications.first())
    }

    @Test
    fun `download notifications are saved`() = runTest {
        app.signedIn()

        app.profileSettings.setDownloadNotifications(false)

        assertEquals(false, app.profileSettings.downloadNotifications.first())
    }

    @Test
    fun `the notification settings are saved and a scope outside the choices changes nothing`() = runTest {
        app.signedIn()

        app.profileSettings.setNotificationsEnabled(true)
        app.profileSettings.setNotificationScope(1)
        app.profileSettings.setNotificationScope(7)

        assertEquals(true, app.profileSettings.notificationsEnabled.first())
        assertEquals(1, app.profileSettings.notificationScope.first())
    }

    @Test
    fun `a notified item is remembered per profile and pruned by age`() = runTest {
        val dao = app.database.accountDao()
        app.signedIn()
        val account = (app.connection.state.value as ConnectionState.Connected).account

        assertFalse(dao.wasNotified(account.serverId, account.userId, "i1"))
        dao.markNotified(NotifiedItemEntity(account.serverId, account.userId, "i1", 100))
        dao.markNotified(NotifiedItemEntity(account.serverId, account.userId, "i2", 500))
        assertEquals(true, dao.wasNotified(account.serverId, account.userId, "i1"))

        dao.pruneNotified(account.serverId, account.userId, 300)

        assertFalse(dao.wasNotified(account.serverId, account.userId, "i1"))
        assertEquals(true, dao.wasNotified(account.serverId, account.userId, "i2"))
    }

    @Test
    fun `values with nobody signed in have no profile and every default`() = runTest {
        val values = app.profileSettings.values.first()

        assertNull(values.profile)
        assertFalse(values.hasProfile)
        assertEquals(10, values[ProfileSettingDefinitions.SkipBackSeconds])
        assertNull(values.readOrNull(ProfileSettingDefinitions.SkipBackSeconds))
    }

    @Test
    fun `values of a signed-in profile hold what was saved and the default for the rest`() = runTest {
        app.signedIn()
        app.profileSettings.setSkipBackSeconds(15)

        val values = app.profileSettings.values.first { it.readOrNull(ProfileSettingDefinitions.SkipBackSeconds) == 15 }

        assertEquals(ProfileRef("srv-1", "u1"), values.profile)
        assertEquals(15, values[ProfileSettingDefinitions.SkipBackSeconds])
        assertEquals(true, values[ProfileSettingDefinitions.PictureInPicture])
    }

    @Test
    fun `a value outside the choices is ignored and so is one stored outside them`() = runTest {
        app.signedIn()

        app.profileSettings.setBingeThreshold(1)
        app.profileSettings.setStorageLimitMb(-5)
        assertEquals(3, app.profileSettings.bingeThreshold.first())
        assertEquals(0L, app.profileSettings.storageLimitMb.first())

        app.database.accountDao().setSkipBackSeconds("srv-1", "u1", 7)
        assertEquals(10, app.profileSettings.skipBackSeconds.first())
        assertEquals(10, app.profileSettings.values.first { it.hasProfile }[ProfileSettingDefinitions.SkipBackSeconds])
    }

    @Test
    fun `a profile uses the default theme and an unknown theme id changes nothing`() = runTest {
        assertNull(app.profileSettings.themeId.first())
        app.signedIn()

        assertEquals(ThemeIds.DEFAULT, app.profileSettings.themeId.first())

        app.profileSettings.setThemeId(ThemeIds.DEFAULT)
        assertEquals(ThemeIds.DEFAULT, app.profileSettings.themeId.first())

        app.profileSettings.setThemeId("no-such-theme")
        assertEquals(ThemeIds.DEFAULT, app.profileSettings.themeId.first())

        app.database.accountDao().setThemeId("srv-1", "u1", "no-such-theme")
        assertEquals(ThemeIds.DEFAULT, app.profileSettings.themeId.first())
    }

    @Test
    fun `shownFor hides values read for another profile`() = runTest {
        app.signedIn()
        app.profileSettings.setSkipBackSeconds(15)
        val values = app.profileSettings.values.first { it.readOrNull(ProfileSettingDefinitions.SkipBackSeconds) == 15 }

        assertEquals(15, values.shownFor(ProfileRef("srv-1", "u1"))[ProfileSettingDefinitions.SkipBackSeconds])
        val other = values.shownFor(ProfileRef("srv-1", "u2"))
        assertEquals(ProfileRef("srv-1", "u2"), other.profile)
        assertNull(other.readOrNull(ProfileSettingDefinitions.SkipBackSeconds))
        assertEquals(10, other[ProfileSettingDefinitions.SkipBackSeconds])
    }

    @Test
    fun `switching profile never shows the previous profile's value`() = runTest {
        val server = app.signedIn()
        app.profileSettings.setSkipBackSeconds(15)
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u2","Name":"Sam"}"""))
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")

        val seen = mutableListOf<ProfileValues>()
        val collecting = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { app.profileSettings.values.toList(seen) }
        app.connection.activate("srv-1", "u2")
        app.profileSettings.setSkipBackSeconds(30)
        advanceUntilIdle()
        collecting.cancel()

        val sam = seen.filter { it.profile == ProfileRef("srv-1", "u2") }
        assertTrue(sam.isNotEmpty())
        assertTrue(sam.none { it[ProfileSettingDefinitions.SkipBackSeconds] == 15 })
        assertEquals(15, app.database.accountDao().observeSkipBackSeconds("srv-1", "u1").first())
    }
}
