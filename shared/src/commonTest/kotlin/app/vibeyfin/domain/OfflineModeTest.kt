package app.vibeyfin.domain

import app.vibeyfin.data.prefs.DataStoreDevicePreferences
import app.vibeyfin.testing.FakeNetworkMonitor
import app.vibeyfin.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineModeTest {
    private val network = FakeNetworkMonitor()
    private val preferences = DataStoreDevicePreferences(InMemoryPreferencesDataStore())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val offline = OfflineMode(network, preferences, scope)

    @AfterTest
    fun tearDown() = scope.cancel()

    @Test
    fun withANetworkTheAppIsOnline() {
        assertFalse(offline.isOffline.value)
    }

    @Test
    fun losingTheNetworkGoesOfflineAndGettingItBackGoesOnline() {
        network.set(NetworkState.None)
        assertTrue(offline.isOffline.value)

        network.set(NetworkState.Metered)
        assertFalse(offline.isOffline.value)
    }

    @Test
    fun aMeteredNetworkIsStillANetwork() {
        network.set(NetworkState.Metered)

        assertFalse(offline.isOffline.value)
    }

    @Test
    fun theSwitchInSettingsGoesOfflineEvenWithANetwork() = runTest {
        offline.setManual(true)

        assertTrue(offline.isOffline.value)
        assertTrue(offline.manual.first())
    }

    @Test
    fun aNetworkThatComesBackDoesNotUndoTheSwitch() = runTest {
        offline.setManual(true)
        network.set(NetworkState.None)
        network.set(NetworkState.Unmetered)

        assertTrue(offline.isOffline.value)
    }

    @Test
    fun turningTheSwitchOffFollowsTheNetworkAgain() = runTest {
        offline.setManual(true)
        network.set(NetworkState.None)
        offline.setManual(false)

        assertTrue(offline.isOffline.value, "still no network")

        network.set(NetworkState.Unmetered)
        assertFalse(offline.isOffline.value)
    }

    @Test
    fun theAppStartsOfflineWhenThereIsNoNetworkYet() {
        val noNetwork = FakeNetworkMonitor(NetworkState.None)

        val started = OfflineMode(noNetwork, DataStoreDevicePreferences(InMemoryPreferencesDataStore()), scope)

        assertEquals(true, started.isOffline.value)
    }
}
