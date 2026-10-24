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
    /** Seconds the player's skip-back button and left double-tap jump. */
    val skipBackSeconds: Int = ProfileSettings.DEFAULT_SKIP_BACK_SECONDS,
    /** Seconds the player's skip-forward button and right double-tap jump. */
    val skipForwardSeconds: Int = ProfileSettings.DEFAULT_SKIP_FORWARD_SECONDS,
    /** The player jumps past marked intros and recaps without asking. */
    val autoSkipSegments: Boolean = false,
    /** Seconds the player loads ahead; 0 is Auto, the platform player's own choice. */
    val bufferAheadSeconds: Int = ProfileSettings.DEFAULT_BUFFER_AHEAD_SECONDS,
)

/** Device-wide settings from [DevicePreferences] and the signed-in profile's from the database, one column per option. */
class SettingsViewModel(
    private val preferences: DevicePreferences,
    private val profileSettings: ProfileSettings,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine<Any?, SettingsUiState>(
        listOf(
            preferences.alwaysAskWhoIsWatching,
            profileSettings.showPlaybackStats,
            profileSettings.bingeThreshold,
            profileSettings.homeGenreRows,
            profileSettings.showHomeHero,
            profileSettings.skipBackSeconds,
            profileSettings.skipForwardSeconds,
            profileSettings.autoSkipSegments,
            profileSettings.bufferAheadSeconds,
        ),
    ) { v ->
        val showStats = v[1] as Boolean?
        val threshold = v[2] as Int?
        SettingsUiState(
            alwaysAskWhoIsWatching = v[0] as Boolean,
            hasProfile = showStats != null || threshold != null,
            showPlaybackStats = showStats ?: false,
            bingeThreshold = threshold ?: ProfileSettings.DEFAULT_BINGE_THRESHOLD,
            homeGenreRows = v[3] as Int? ?: ProfileSettings.HOME_GENRES_ALL,
            showHomeHero = v[4] as Boolean? ?: true,
            skipBackSeconds = v[5] as Int? ?: ProfileSettings.DEFAULT_SKIP_BACK_SECONDS,
            skipForwardSeconds = v[6] as Int? ?: ProfileSettings.DEFAULT_SKIP_FORWARD_SECONDS,
            autoSkipSegments = v[7] as Boolean? ?: false,
            bufferAheadSeconds = v[8] as Int? ?: ProfileSettings.DEFAULT_BUFFER_AHEAD_SECONDS,
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

    fun onSkipBackChanged(value: Int) {
        viewModelScope.launch { profileSettings.setSkipBackSeconds(value) }
    }

    fun onSkipForwardChanged(value: Int) {
        viewModelScope.launch { profileSettings.setSkipForwardSeconds(value) }
    }

    fun onBufferAheadChanged(value: Int) {
        viewModelScope.launch { profileSettings.setBufferAheadSeconds(value) }
    }

    fun onAutoSkipChanged(value: Boolean) {
        viewModelScope.launch { profileSettings.setAutoSkipSegments(value) }
    }
}
