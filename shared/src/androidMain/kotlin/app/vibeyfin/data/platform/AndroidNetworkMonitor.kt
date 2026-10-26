package app.vibeyfin.data.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.vibeyfin.domain.NetworkMonitor
import app.vibeyfin.domain.NetworkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Follows the default network. A network counts only when the system has validated that it reaches the
 * internet, so Wi-Fi without a route out, and a captive portal, are [NetworkState.None]. The callback is
 * registered once for the life of the process, which is what makes [state] cheap to read anywhere.
 */
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val current = MutableStateFlow(read(manager.activeNetwork))

    override val state: StateFlow<NetworkState> = current.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    current.value = stateOf(capabilities)
                }

                override fun onLost(network: Network) {
                    current.value = NetworkState.None
                }
            },
        )
    }

    private fun read(network: Network?): NetworkState =
        network?.let(manager::getNetworkCapabilities)?.let(::stateOf) ?: NetworkState.None

    private fun stateOf(capabilities: NetworkCapabilities): NetworkState = when {
        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkState.None
        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetworkState.None
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkState.Unmetered
        else -> NetworkState.Metered
    }
}
