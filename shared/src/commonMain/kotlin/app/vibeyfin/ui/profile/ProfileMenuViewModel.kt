package app.vibeyfin.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.DownloadRepository
import app.vibeyfin.domain.PinLock
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.domain.SearchHistory
import app.vibeyfin.ui.common.PinPrompt
import app.vibeyfin.ui.common.ProfileActions
import app.vibeyfin.ui.home.HomeExit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProfileMenuUiState(
    /** The signed-in profile; null while nobody is. */
    val userName: String? = null,
    /** A kids profile: the menu has no Sign out. */
    val kidsProfile: Boolean = false,
    /** The PIN sheet, while switching profile or server waits for the profile's PIN. */
    val pinPrompt: PinPrompt? = null,
    /** Where to go once the profile has been left. */
    val exit: HomeExit? = null,
    /** One-shot: open Settings › Quick Connect, then call [ProfileMenuViewModel.onQuickConnectOpened]. */
    val openQuickConnect: Boolean = false,
)

/** The profile button at the top of every tab: who is signed in, and the menu's switch profile, switch server, Quick Connect and sign out. */
class ProfileMenuViewModel(
    connection: ConnectionManager,
    profileSettings: ProfileSettings,
    searchHistory: SearchHistory,
    pins: PinLock,
    auth: AuthRepository,
    downloads: DownloadRepository,
) : ViewModel() {
    private val profile = ProfileActions(connection, profileSettings, searchHistory, pins, auth, downloads, viewModelScope)

    val uiState: StateFlow<ProfileMenuUiState> = combine(
        connection.state,
        profileSettings.kidsProfile,
        profile.pinPrompt,
        profile.exit,
        profile.openQuickConnect,
    ) { state, kids, prompt, exit, quickConnect ->
        val connected = state as? ConnectionState.Connected
        ProfileMenuUiState(
            userName = connected?.account?.name,
            kidsProfile = kids == true,
            pinPrompt = prompt,
            exit = exit,
            openQuickConnect = quickConnect,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileMenuUiState())

    fun onSwitchProfile() = profile.switchProfile()

    fun onSwitchServer() = profile.switchServer()

    fun onSignOut() = profile.signOut()

    /** Quick Connect in the menu: approve another device's code, after the PIN to open Settings when the profile has one. */
    fun onQuickConnect() = profile.quickConnect()

    fun onQuickConnectOpened() = profile.quickConnectOpened()

    fun onSubmitPin(pin: String) = profile.submitPin(pin)

    fun onForgotPin() = profile.forgotPin()

    fun onSubmitPassword(password: String) = profile.submitPassword(password)

    fun onDismissPin() = profile.dismissPin()
}
