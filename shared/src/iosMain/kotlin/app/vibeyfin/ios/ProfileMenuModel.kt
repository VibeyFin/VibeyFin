package app.vibeyfin.ios

import app.vibeyfin.ui.common.PinSheetControls
import app.vibeyfin.ui.profile.ProfileMenuUiState
import app.vibeyfin.ui.profile.ProfileMenuViewModel

/** The profile button at the top of every tab: [ProfileMenuViewModel] as SwiftUI needs it. */
class ProfileMenuModel : PinSheetModel {
    private val host = ViewModelHost(ProfileMenuViewModel::class) { it.get<ProfileMenuViewModel>() }

    override val pinSheet: PinSheetControls get() = host.viewModel

    fun observe(onChange: (ProfileMenuUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onSwitchProfile() = host.viewModel.onSwitchProfile()

    fun onSwitchServer() = host.viewModel.onSwitchServer()

    fun onSignOut() = host.viewModel.onSignOut()

    fun onQuickConnect() = host.viewModel.onQuickConnect()

    fun onQuickConnectOpened() = host.viewModel.onQuickConnectOpened()

    fun close() = host.close()
}
