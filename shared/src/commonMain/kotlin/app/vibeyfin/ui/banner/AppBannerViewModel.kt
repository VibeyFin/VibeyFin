package app.vibeyfin.ui.banner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ServerReachability
import app.vibeyfin.domain.OfflineMode
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the banner's button does. */
enum class BannerAction { GoOnline, UseOffline }

/** The line across the top of the app that says the app is offline, or that the server cannot be reached, and the way out. */
data class Banner(val title: String, val help: String, val action: BannerAction?) {
    /** The button's words; null when there is no button (offline for want of a network, which only a network ends). */
    val actionLabel: String?
        get() = when (action) {
            BannerAction.GoOnline -> Strings.goOnline
            BannerAction.UseOffline -> Strings.useOffline
            null -> null
        }
}

/**
 * Says what the app is doing about the connection. Offline mode switched on in Settings: "Offline mode" with **Go online**. No
 * network: "You're offline", with nothing to press. A network but a server that does not answer: "Can't reach the server" with
 * **Use offline**, which switches Offline mode on (the user's own decision, not the app's, so a server that blinks does not flip
 * every screen). Nothing when all is well.
 */
class AppBannerViewModel(
    private val offline: OfflineMode,
    reachability: ServerReachability,
) : ViewModel() {
    val banner: StateFlow<Banner?> = combine(offline.isOffline, offline.manual, reachability.unreachable) { isOffline, manual, unreachable ->
        when {
            manual -> Banner(Strings.offlineModeTitle, Strings.offlineBannerHelp, BannerAction.GoOnline)
            isOffline -> Banner(Strings.offlineBannerTitle, Strings.offlineBannerHelp, action = null)
            unreachable -> Banner(Strings.serverUnreachableBannerTitle, Strings.serverUnreachableBannerHelp, BannerAction.UseOffline)
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onAction() {
        val action = banner.value?.action ?: return
        viewModelScope.launch { offline.setManual(action == BannerAction.UseOffline) }
    }
}
