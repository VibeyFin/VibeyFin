package app.vibeyfin.ios

import app.vibeyfin.ui.settings.SettingKey
import app.vibeyfin.ui.settings.SettingsPageId
import app.vibeyfin.ui.settings.SettingsUiState
import app.vibeyfin.ui.settings.SettingsViewModel
import app.vibeyfin.ui.settings.firstSettablePage

/** The settings screen's [SettingsViewModel] as SwiftUI needs it. */
class SettingsModel {
    private val host = ViewModelHost(SettingsViewModel::class) { it.get<SettingsViewModel>() }

    fun observe(onChange: (SettingsUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onToggle(key: SettingKey, value: Boolean) = host.viewModel.onToggle(key, value)

    fun onChoose(key: SettingKey, value: Int) = host.viewModel.onChoose(key, value)

    fun onAction(key: SettingKey) = host.viewModel.onAction(key)

    /** The page the iPad's split view opens on; null when nothing has anything to set. */
    fun firstSettablePage(): SettingsPageId? = firstSettablePage(host.viewModel.uiState.value.pages)

    fun onConfirmSignOut() = host.viewModel.onConfirmSignOut()

    fun onDismissSignOut() = host.viewModel.onDismissSignOut()

    fun close() = host.close()
}
