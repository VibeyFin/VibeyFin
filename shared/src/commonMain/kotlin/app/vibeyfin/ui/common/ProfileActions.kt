package app.vibeyfin.ui.common

import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.DownloadRepository
import app.vibeyfin.domain.PinLock
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.domain.SearchHistory
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.ui.home.HomeExit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What the profile menu does: switch profile, switch server and sign out, behind the profile's PIN when it has one. Home and the
 * Android tabs' profile button both run it, so the rules live here once. [exit] is where to go once the profile has been left.
 */
class ProfileActions(
    private val connection: ConnectionManager,
    private val profileSettings: ProfileSettings,
    private val searchHistory: SearchHistory,
    private val pins: PinLock,
    auth: AuthRepository,
    private val downloads: DownloadRepository,
    private val scope: CoroutineScope,
) {
    private val pin = PinGate(pins, auth, scope)
    private val _exit = MutableStateFlow<HomeExit?>(null)
    val exit: StateFlow<HomeExit?> = _exit.asStateFlow()

    /** The PIN sheet, while switching profile or server waits for the profile's PIN. */
    val pinPrompt: StateFlow<PinPrompt?> = pin.prompt

    private val _openQuickConnect = MutableStateFlow(false)

    /** One-shot: the app opens Settings › Quick Connect, then calls [quickConnectOpened]. */
    val openQuickConnect: StateFlow<Boolean> = _openQuickConnect.asStateFlow()

    /**
     * Settings › Quick Connect straight from the menu, behind the PIN to open Settings as Settings itself is: signing another device in
     * as this profile is no less than changing its settings. Never on a kids profile.
     */
    fun quickConnect() {
        connection.asAccount(Unit) { account ->
            scope.launch {
                if (profileSettings.kidsProfile.first() == true) return@launch
                val open: suspend () -> Unit = { _openQuickConnect.value = true }
                if (pins.gates(account.serverId, account.userId).openSettings) {
                    pin.ask(PinReason.OpenSettings, account.serverId, account.userId, account.name, onPass = open)
                } else {
                    open()
                }
            }
        }
    }

    fun quickConnectOpened() {
        _openQuickConnect.value = false
    }

    /** Leaves this profile signed in and goes back to "Who's watching?" on the same server, after its PIN if it asks. */
    fun switchProfile() {
        val serverId = currentServerId() ?: return
        leaveProfile(HomeExit.WhoIsWatching(serverId))
    }

    /** Leaving for the servers list is leaving the profile too, so it asks for the PIN the same way. */
    fun switchServer() {
        if (currentServerId() == null) {
            connection.switchProfile()
            _exit.value = HomeExit.Servers
            return
        }
        leaveProfile(HomeExit.Servers)
    }

    /**
     * Revokes the token on the server, locks the profile here, forgets what it searched for and deletes its downloads, and goes back
     * to "Who's watching?". (An expired session keeps both: that profile is coming back.)
     */
    fun signOut() {
        val serverId = currentServerId() ?: return
        scope.launch {
            // A kids profile has no Sign out; its menu hides it, and this keeps it that way.
            if (profileSettings.kidsProfile.first() == true) return@launch
            // While still signed in: the history is the signed-in profile's.
            searchHistory.clear()
            connection.asAccount(Unit) { downloads.purge(ProfileRef(it.serverId, it.userId)) }
            connection.signOut()
            _exit.value = HomeExit.WhoIsWatching(serverId)
        }
    }

    /** A PIN was entered in the open PIN sheet. */
    fun submitPin(pin: String) = this.pin.submitPin(pin)

    /** "Forgot PIN?": the PIN sheet asks for the profile's Jellyfin password instead. */
    fun forgotPin() = pin.forgotPin()

    /** The Jellyfin password entered after "Forgot PIN?". */
    fun submitPassword(password: String) = pin.submitPassword(password)

    fun dismissPin() = pin.dismiss()

    private fun leaveProfile(to: HomeExit) {
        connection.asAccount(Unit) { account ->
            scope.launch {
                val leave: suspend () -> Unit = {
                    connection.switchProfile()
                    _exit.value = to
                }
                if (pins.gates(account.serverId, account.userId).switchProfile) {
                    pin.ask(PinReason.SwitchProfile, account.serverId, account.userId, account.name, onPass = leave)
                } else {
                    leave()
                }
            }
        }
    }

    private fun currentServerId() = connection.asConnected<String?>(null) { it.server.id }
}
