package app.vibeyfin.ios

import app.vibeyfin.ui.profile.ProfileMenuUiState
import app.vibeyfin.ui.profile.ProfileMenuViewModel

/** The profile button at the top of every tab: [ProfileMenuViewModel] as SwiftUI needs it. */
class ProfileMenuModel {
    private val host = ViewModelHost(ProfileMenuViewModel::class) { it.get<ProfileMenuViewModel>() }

    fun observe(onChange: (ProfileMenuUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onSwitchProfile() = host.viewModel.onSwitchProfile()

    fun onSwitchServer() = host.viewModel.onSwitchServer()

    fun onSignOut() = host.viewModel.onSignOut()

    /** A PIN was entered in the open PIN sheet. */
    fun onSubmitPin(pin: String) = host.viewModel.onSubmitPin(pin)

    /** "Forgot PIN?": the PIN sheet asks for the profile's Jellyfin password instead. */
    fun onForgotPin() = host.viewModel.onForgotPin()

    fun onDismissPin() = host.viewModel.onDismissPin()

    /** The Jellyfin password entered after "Forgot PIN?". */
    fun onSubmitPassword(password: String) = host.viewModel.onSubmitPassword(password)

    fun close() = host.close()
}
