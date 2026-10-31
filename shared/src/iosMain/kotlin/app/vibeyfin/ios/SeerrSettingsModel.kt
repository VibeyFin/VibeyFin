package app.vibeyfin.ios

import app.vibeyfin.ui.seerr.SeerrSettingsUiState
import app.vibeyfin.ui.seerr.SeerrSettingsViewModel

/** Settings › Seerr, as SwiftUI needs it. */
class SeerrSettingsModel {
    private val host = ViewModelHost(SeerrSettingsViewModel::class) {
        it.get<SeerrSettingsViewModel>()
    }

    fun observe(onChange: (SeerrSettingsUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onAddressChange(text: String) = host.viewModel.onAddressChange(text)

    fun onConnect() = host.viewModel.onConnect()

    fun onChangeAddress() = host.viewModel.onChangeAddress()

    fun onRetry() = host.viewModel.onRetry()

    fun onSignInWithPassword() = host.viewModel.onSignInWithPassword()

    fun onSubmitPassword(password: String) = host.viewModel.onSubmitPassword(password)

    fun onDismissPassword() = host.viewModel.onDismissPassword()

    fun onDisconnect() = host.viewModel.onDisconnect()

    fun onConfirmDisconnect() = host.viewModel.onConfirmDisconnect()

    fun onDismissDisconnect() = host.viewModel.onDismissDisconnect()

    fun close() = host.close()
}
