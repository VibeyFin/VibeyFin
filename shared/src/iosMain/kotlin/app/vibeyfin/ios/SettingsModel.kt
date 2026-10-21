package app.vibeyfin.ios

import app.vibeyfin.ui.settings.SettingsUiState
import app.vibeyfin.ui.settings.SettingsViewModel

/** The settings screen's [SettingsViewModel] as SwiftUI needs it. */
class SettingsModel {
    private val host = ViewModelHost(SettingsViewModel::class) { it.get<SettingsViewModel>() }

    fun observe(onChange: (SettingsUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onAlwaysAskChanged(value: Boolean) = host.viewModel.onAlwaysAskChanged(value)

    fun onShowPlaybackStatsChanged(value: Boolean) = host.viewModel.onShowPlaybackStatsChanged(value)

    fun onBingeThresholdChanged(value: Int) = host.viewModel.onBingeThresholdChanged(value)

    fun close() = host.close()
}
