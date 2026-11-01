package app.vibeyfin.data.prefs

import app.vibeyfin.domain.PlaybackEngineChoice // PoC: remove with poc/playback-engine
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevicePreferencesTest {
    private val dataStore = InMemoryPreferencesDataStore()
    private var generated = 0
    private val preferences = DataStoreDevicePreferences(dataStore, newDeviceId = { "device-${++generated}" })

    @Test
    fun deviceIdIsGeneratedOnceAndKept() = runTest {
        val first = preferences.deviceId()
        val second = preferences.deviceId()
        // A second instance over the same storage is a later app start.
        val afterRestart = DataStoreDevicePreferences(dataStore, newDeviceId = { "other" }).deviceId()

        assertEquals("device-1", first)
        assertEquals(first, second)
        assertEquals(first, afterRestart)
    }

    @Test
    fun deviceIdIsGeneratedOnceEvenWhenAskedConcurrently() = runTest {
        val ids = (1..8).map { async { preferences.deviceId() } }.awaitAll()

        assertEquals(setOf("device-1"), ids.toSet())
        assertEquals(1, generated)
    }

    @Test
    fun alwaysAskIsOffByDefault() = runTest {
        assertFalse(preferences.alwaysAskWhoIsWatching.first())

        preferences.setAlwaysAskWhoIsWatching(true)

        assertTrue(preferences.alwaysAskWhoIsWatching.first())
    }

    @Test
    fun offlineModeIsOffByDefaultAndIsKept() = runTest {
        assertFalse(preferences.offlineMode.first())

        preferences.setOfflineMode(true)

        assertTrue(preferences.offlineMode.first())
        assertTrue(DataStoreDevicePreferences(dataStore).offlineMode.first(), "after a restart")
    }

    // PoC: remove with poc/playback-engine
    @Test
    fun playbackEngineIsAutomaticByDefaultAndIsKept() = runTest {
        assertEquals(PlaybackEngineChoice.Auto, preferences.playbackEngine.first())

        preferences.setPlaybackEngine(PlaybackEngineChoice.Aether)

        assertEquals(PlaybackEngineChoice.Aether, preferences.playbackEngine.first())
        assertEquals(PlaybackEngineChoice.Aether, DataStoreDevicePreferences(dataStore).playbackEngine.first(), "after a restart")
    }

    // PoC: remove with poc/playback-engine
    @Test
    fun playbackEngineKeysFallBackToAutomatic() {
        assertEquals(PlaybackEngineChoice.Aether, PlaybackEngineChoice.fromKey("aether"))
        assertEquals(PlaybackEngineChoice.Auto, PlaybackEngineChoice.fromKey("unknown"))
        assertEquals(PlaybackEngineChoice.Auto, PlaybackEngineChoice.fromKey(null))
    }

    @Test
    fun nothingIsRememberedOnAFreshInstall() = runTest {
        assertNull(preferences.lastServerId.first())
        assertNull(preferences.lastProfile.first())
    }

    @Test
    fun theLastProfileAlsoSetsTheLastServer() = runTest {
        preferences.setLastProfile(ProfileRef("srv", "u1"))

        assertEquals("srv", preferences.lastServerId.first())
        assertEquals(ProfileRef("srv", "u1"), preferences.lastProfile.first())
    }

    @Test
    fun choosingTheSameServerKeepsTheLastProfile() = runTest {
        preferences.setLastProfile(ProfileRef("srv", "u1"))

        preferences.setLastServer("srv")

        assertEquals(ProfileRef("srv", "u1"), preferences.lastProfile.first())
    }

    @Test
    fun choosingAnotherServerForgetsTheLastProfile() = runTest {
        preferences.setLastProfile(ProfileRef("srv", "u1"))

        preferences.setLastServer("other")

        assertEquals("other", preferences.lastServerId.first())
        assertNull(preferences.lastProfile.first())
    }

    @Test
    fun clearingTheLastServerClearsTheProfileToo() = runTest {
        preferences.setLastProfile(ProfileRef("srv", "u1"))

        preferences.setLastServer(null)

        assertNull(preferences.lastServerId.first())
        assertNull(preferences.lastProfile.first())
    }
}
