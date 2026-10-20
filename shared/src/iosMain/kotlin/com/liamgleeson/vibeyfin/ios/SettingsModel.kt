package com.liamgleeson.vibeyfin.ios

import com.liamgleeson.vibeyfin.ui.settings.SettingsUiState
import com.liamgleeson.vibeyfin.ui.settings.SettingsViewModel

/** The settings screen's [SettingsViewModel] as SwiftUI needs it. */
class SettingsModel {
    private val host = ViewModelHost(SettingsViewModel::class) { it.get<SettingsViewModel>() }

    fun observe(onChange: (SettingsUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onAlwaysAskChanged(value: Boolean) = host.viewModel.onAlwaysAskChanged(value)

    fun onAllowTranscodingChanged(value: Boolean) = host.viewModel.onAllowTranscodingChanged(value)

    fun close() = host.close()
}
