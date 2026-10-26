package app.vibeyfin.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.PinLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Where a tapped new-title notification takes the app. */
sealed interface NotificationRoute {
    data class Item(val itemId: String) : NotificationRoute

    /** The notification belongs to a profile that is not signed in: it is picked on "Who's watching?", with its PIN if it asks. */
    data class WhoIsWatching(val serverId: String) : NotificationRoute
}

/**
 * Opens what a tapped new-title notification is about. It never signs a profile in by itself, so a profile's PIN and a
 * locked profile's password are asked the usual way:
 * - the notification's profile is signed in: its item opens;
 * - another profile is signed in: the app leaves it for "Who's watching?" (unless that profile's PIN guards switching,
 *   then the tap is dropped and the app stays where it is), and the item opens once the notification's profile is picked;
 * - nobody is signed in yet (a cold start): the usual startup runs, then the same rules apply to whoever it reaches.
 * Picking a different profile on "Who's watching?" drops the tap.
 */
class NotificationOpenViewModel(
    private val connection: ConnectionManager,
    private val pins: PinLock,
) : ViewModel() {
    private data class Target(val serverId: String, val userId: String, val itemId: String, val leftProfile: Boolean = false)

    private val target = MutableStateFlow<Target?>(null)
    private val inApp = MutableStateFlow(false)
    private val _route = MutableStateFlow<NotificationRoute?>(null)
    val route: StateFlow<NotificationRoute?> = _route.asStateFlow()

    init {
        viewModelScope.launch {
            combine(target, connection.state, inApp) { t, state, inApp -> if (inApp) t?.let { it to state } else null }
                .collect { pair -> pair?.let { (t, state) -> resolve(t, state) } }
        }
    }

    fun onNotificationTapped(serverId: String, userId: String, itemId: String) {
        target.value = Target(serverId, userId, itemId)
    }

    /** Whether the app is past startup and "Who's watching?", inside a signed-in profile; nothing is routed before that. */
    fun onInApp(inApp: Boolean) {
        this.inApp.value = inApp
    }

    fun onRouted() {
        _route.value = null
    }

    private suspend fun resolve(t: Target, state: ConnectionState) {
        val account = (state as? ConnectionState.Connected)?.account ?: return
        if (target.value != t) return
        if (account.serverId == t.serverId && account.userId == t.userId) {
            target.value = null
            _route.value = NotificationRoute.Item(t.itemId)
            return
        }
        if (t.leftProfile || pins.gates(account.serverId, account.userId).switchProfile) {
            target.compareAndSet(t, null)
            return
        }
        // Leaves the profile before marking the target, so no update ever pairs the marked target with the old profile.
        connection.switchProfile()
        target.compareAndSet(t, t.copy(leftProfile = true))
        _route.value = NotificationRoute.WhoIsWatching(t.serverId)
    }
}
