package app.vibeyfin.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.domain.DevicePreferences
import app.vibeyfin.domain.ProfileSettings
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
    /** The player shows its stats overlay. */
    val showPlaybackStats: Boolean = false,
    /** Episodes in a row before "Still watching?"; 0 is off. */
    val bingeThreshold: Int = ProfileSettings.DEFAULT_BINGE_THRESHOLD,
    /** How many genre rows Home shows: 0 is every genre, -1 none. */
    val homeGenreRows: Int = ProfileSettings.HOME_GENRES_ALL,
    /** Home features one item at the top. */
    val showHomeHero: Boolean = true,
)

/** Device-wide settings from [DevicePreferences] and the signed-in profile's from the database, one column per option. */
class SettingsViewModel(
    private val preferences: DevicePreferences,
    private val profileSettings: ProfileSettings,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.alwaysAskWhoIsWatching,
        profileSettings.showPlaybackStats,
        profileSettings.bingeThreshold,
        profileSettings.homeGenreRows,
        profileSettings.showHomeHero,
    ) { alwaysAsk, showStats, threshold, genreRows, hero ->
        SettingsUiState(
            alwaysAskWhoIsWatching = alwaysAsk,
            hasProfile = showStats != null || threshold != null,
            showPlaybackStats = showStats ?: false,
            bingeThreshold = threshold ?: ProfileSettings.DEFAULT_BINGE_THRESHOLD,
            homeGenreRows = genreRows ?: ProfileSettings.HOME_GENRES_ALL,
            showHomeHero = hero ?: true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onAlwaysAskChanged(value: Boolean) {
        viewModelScope.launch { preferences.setAlwaysAskWhoIsWatching(value) }
    }

    fun onShowPlaybackStatsChanged(value: Boolean) {
        viewModelScope.launch { profileSettings.setShowPlaybackStats(value) }
    }

    fun onBingeThresholdChanged(value: Int) {
        viewModelScope.launch { profileSettings.setBingeThreshold(value) }
    }

    fun onShowHomeHeroChanged(value: Boolean) {
        viewModelScope.launch { profileSettings.setShowHomeHero(value) }
    }

    fun onHomeGenreRowsChanged(value: Int) {
        viewModelScope.launch { profileSettings.setHomeGenreRows(value) }
    }
}
