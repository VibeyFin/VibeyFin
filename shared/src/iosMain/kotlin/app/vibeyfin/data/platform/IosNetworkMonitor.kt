package app.vibeyfin.data.platform

import app.vibeyfin.domain.NetworkMonitor
import app.vibeyfin.domain.NetworkState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/**
 * Follows the device's network path. `satisfied` means a route to the internet exists (flight mode, no signal and a Wi-Fi with no
 * way out are not satisfied). A path that is "expensive" (mobile data, or a hotspot) or "constrained" (Low Data Mode) is
 * [NetworkState.Metered]. Started once for the life of the process, like the Android one, so [state] is cheap to read anywhere.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNetworkMonitor : NetworkMonitor {
    private val current = MutableStateFlow(NetworkState.Unmetered)

    override val state: StateFlow<NetworkState> = current.asStateFlow()

    init {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path ->
            current.value = when {
                path == null || nw_path_get_status(path) != nw_path_status_satisfied -> NetworkState.None
                nw_path_is_expensive(path) || nw_path_is_constrained(path) -> NetworkState.Metered
                else -> NetworkState.Unmetered
            }
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }
}
