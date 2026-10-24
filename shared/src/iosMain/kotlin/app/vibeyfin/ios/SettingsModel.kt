package app.vibeyfin.ios

import app.vibeyfin.ui.settings.SettingKey
import app.vibeyfin.ui.settings.SettingsUiState
import app.vibeyfin.ui.settings.SettingsViewModel

/** The settings screen's [SettingsViewModel] as SwiftUI needs it. */
class SettingsModel {
    private val host = ViewModelHost(SettingsViewModel::class) { it.get<SettingsViewModel>() }

    fun observe(onChange: (SettingsUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onToggle(key: SettingKey, value: Boolean) = host.viewModel.onToggle(key, value)

    fun onChoose(key: SettingKey, value: Int) = host.viewModel.onChoose(key, value)

    fun onAction(key: SettingKey) = host.viewModel.onAction(key)

    fun onConfirmSignOut() = host.viewModel.onConfirmSignOut()

    fun onDismissSignOut() = host.viewModel.onDismissSignOut()

    // The handlers below are removed with the native renderers in Tasks 7 and 8.

    fun onAlwaysAskChanged(value: Boolean) = host.viewModel.onAlwaysAskChanged(value)

    fun onShowPlaybackStatsChanged(value: Boolean) = host.viewModel.onShowPlaybackStatsChanged(value)

    fun onBingeThresholdChanged(value: Int) = host.viewModel.onBingeThresholdChanged(value)

    fun onShowHomeHeroChanged(value: Boolean) = host.viewModel.onShowHomeHeroChanged(value)

    fun onSkipBackChanged(value: Int) = host.viewModel.onSkipBackChanged(value)

    fun onSkipForwardChanged(value: Int) = host.viewModel.onSkipForwardChanged(value)

    fun onAutoSkipChanged(value: Boolean) = host.viewModel.onAutoSkipChanged(value)

    fun onHomeGenreRowsChanged(value: Int) = host.viewModel.onHomeGenreRowsChanged(value)

    fun onBufferAheadChanged(value: Int) = host.viewModel.onBufferAheadChanged(value)

    fun close() = host.close()
}
