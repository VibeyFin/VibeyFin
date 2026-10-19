package com.liamgleeson.vibeyfin.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liamgleeson.vibeyfin.domain.DevicePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    /** Show "Who's watching?" on every start instead of reopening the last profile. */
    val alwaysAskWhoIsWatching: Boolean = false,
)

/** Device-wide settings. Per-profile settings will live in the database, one column per option. */
class SettingsViewModel(private val preferences: DevicePreferences) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = preferences.alwaysAskWhoIsWatching
        .map { SettingsUiState(alwaysAskWhoIsWatching = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onAlwaysAskChanged(value: Boolean) {
        viewModelScope.launch { preferences.setAlwaysAskWhoIsWatching(value) }
    }
}
