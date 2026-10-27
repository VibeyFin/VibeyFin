package app.vibeyfin.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the device's connection can carry right now. */
enum class NetworkState {
    /** No network, or one that has no internet (flight mode, no signal, Wi-Fi without a route out). */
    None,

    /** A connection that is not charged by the megabyte: Wi-Fi, Ethernet. */
    Unmetered,

    /** A connection that may be charged or capped: mobile data, or a hotspot the system marks as metered. */
    Metered,
    ;

    val isConnected: Boolean get() = this != None
}

/**
 * What the platform knows about the network. Only the platform can ask, so each has its own
 * implementation, bound in `platformModule()`. [state] always has a value: it starts with what the
 * device has at that moment, and follows changes.
 */
interface NetworkMonitor {
    val state: StateFlow<NetworkState>
}

/** A monitor that says the network is always up and unmetered, for a platform that has no monitor yet. */
object AlwaysOnlineNetworkMonitor : NetworkMonitor {
    override val state: StateFlow<NetworkState> = MutableStateFlow(NetworkState.Unmetered)
}
