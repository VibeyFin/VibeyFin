package app.vibeyfin.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the app runs from the downloads only. It does when the device has no network, or when
 * the user switched "Offline mode" on in Settings; the switch wins, so a network that comes back
 * does not undo it. A missing network never touches a session: only a 401 expires one.
 *
 * Whether the server answers is a different question (the network can be up while the server is
 * not) and is not decided here.
 */
class OfflineMode(
    private val network: NetworkMonitor,
    private val preferences: DevicePreferences,
    scope: CoroutineScope,
) {
    /** The "Offline mode" switch in Settings. */
    val manual: Flow<Boolean> = preferences.offlineMode

    /** True while the app should use only what is downloaded. Starts false until the stored switch has been read. */
    val isOffline: StateFlow<Boolean> = combine(network.state, preferences.offlineMode) { state, manual ->
        manual || !state.isConnected
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialValue = !network.state.value.isConnected)

    /** What the network can carry, for downloads that wait for Wi-Fi. */
    val networkState: StateFlow<NetworkState> get() = network.state

    suspend fun setManual(value: Boolean) = preferences.setOfflineMode(value)
}
