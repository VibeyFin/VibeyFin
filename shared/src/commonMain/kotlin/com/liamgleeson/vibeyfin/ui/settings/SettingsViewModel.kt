package com.liamgleeson.vibeyfin.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liamgleeson.vibeyfin.domain.DevicePreferences
import com.liamgleeson.vibeyfin.domain.ProfileSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    /** Show "Who's watching?" on every start instead of reopening the last profile. */
    val alwaysAskWhoIsWatching: Boolean = false,
    /** Whether there is a signed-in profile whose settings are shown below the device ones. */
    val hasProfile: Boolean = false,
    /** The profile lets the server transcode when the device can't play a file as it is. */
    val allowTranscoding: Boolean = false,
    /** The player shows its stats overlay. */
    val showPlaybackStats: Boolean = false,
    /** Episodes in a row before "Still watching?"; 0 is off. */
    val bingeThreshold: Int = 3,
)

/** Device-wide settings from [DevicePreferences] and the signed-in profile's from the database, one column per option. */
class SettingsViewModel(
    private val preferences: DevicePreferences,
    private val profileSettings: ProfileSettings,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.alwaysAskWhoIsWatching,
        profileSettings.allowTranscoding,
        profileSettings.showPlaybackStats,
        profileSettings.bingeThreshold,
    ) { alwaysAsk, allowTranscoding, showStats, threshold ->
        SettingsUiState(
            alwaysAskWhoIsWatching = alwaysAsk,
            hasProfile = allowTranscoding != null || showStats != null || threshold != null,
            allowTranscoding = allowTranscoding ?: false,
            showPlaybackStats = showStats ?: false,
            bingeThreshold = threshold ?: 3,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onAlwaysAskChanged(value: Boolean) {
        viewModelScope.launch { preferences.setAlwaysAskWhoIsWatching(value) }
    }

    fun onAllowTranscodingChanged(value: Boolean) {
        viewModelScope.launch { profileSettings.setAllowTranscoding(value) }
    }

    fun onShowPlaybackStatsChanged(value: Boolean) {
        viewModelScope.launch { profileSettings.setShowPlaybackStats(value) }
    }

    fun onBingeThresholdChanged(value: Int) {
        viewModelScope.launch { profileSettings.setBingeThreshold(value) }
    }
}
