package app.vibeyfin.ui.banner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ServerReachability
import app.vibeyfin.domain.OfflineMode
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * Says what the app is doing about the connection. Going offline is told once, as a toast ([toast]): "Offline mode is on…" when it
 * was switched on, "You're offline…" for want of a network; the app then works from what is on the device, and Settings switches
 * Offline mode off. A network but a server that does not answer is the one [banner]: "Can't reach the server" with **Use offline**,
 * which switches Offline mode on (the user's own decision, not the app's, so a server that blinks does not flip every screen). It
 * stays until the server answers or the button is pressed. Nothing when all is well.
 */
class AppBannerViewModel(
    private val offline: OfflineMode,
    reachability: ServerReachability,
) : ViewModel() {
    val banner: StateFlow<Banner?> = combine(offline.isOffline, reachability.unreachable) { isOffline, unreachable ->
        if (unreachable && !isOffline) Banner(Strings.serverUnreachableBannerTitle, Strings.serverUnreachableBannerHelp, BannerAction.UseOffline) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _toast = MutableStateFlow<String?>(null)

    /** What the toast says, once, as the app goes offline; [onToastShown] once it is up. */
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        viewModelScope.launch {
            combine(offline.isOffline, offline.manual) { isOffline, manual ->
                when {
                    manual -> Strings.offlineModeToast
                    isOffline -> Strings.offlineToast
                    else -> null
                }
            }.distinctUntilChanged().collect { message -> if (message != null) _toast.value = message }
        }
    }

    fun onToastShown() {
        _toast.value = null
    }

    fun onAction() {
        val action = banner.value?.action ?: return
        viewModelScope.launch { offline.setManual(action == BannerAction.UseOffline) }
    }
}
