package app.vibeyfin.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.DevicePreferences
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.domain.ServerRepository
import app.vibeyfin.domain.model.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    /** The Settings tree, one page per id; draw a page's [SettingsPage.visibleOnly]. */
    val pages: Map<SettingsPageId, SettingsPage> = settingsPages(SettingsValues()),
    /** Whether there is a signed-in profile whose settings are shown below the device ones. */
    val hasProfile: Boolean = false,
    /** Sign out was tapped and waits for the confirmation. */
    val confirmSignOut: Boolean = false,
    /** The connected server's id, for the native app to open "Who's watching?" there after Switch profile or Sign out. Empty when disconnected. */
    val serverId: String = "",
    // The flat fields below are removed with the native renderers in Tasks 7 and 8.
    val alwaysAskWhoIsWatching: Boolean = false,
    val showPlaybackStats: Boolean = false,
    val bingeThreshold: Int = ProfileSettings.DEFAULT_BINGE_THRESHOLD,
    val homeGenreRows: Int = ProfileSettings.HOME_GENRES_ALL,
    val showHomeHero: Boolean = true,
    val skipBackSeconds: Int = ProfileSettings.DEFAULT_SKIP_BACK_SECONDS,
    val skipForwardSeconds: Int = ProfileSettings.DEFAULT_SKIP_FORWARD_SECONDS,
    val autoSkipSegments: Boolean = false,
    val bufferAheadSeconds: Int = ProfileSettings.DEFAULT_BUFFER_AHEAD_SECONDS,
)

/** Device-wide settings from [DevicePreferences] and the signed-in profile's from the database, built into pages by [settingsPages]. */
class SettingsViewModel(
    private val preferences: DevicePreferences,
    private val profileSettings: ProfileSettings,
    private val connection: ConnectionManager,
    private val servers: ServerRepository,
    private val applicationScope: CoroutineScope,
    private val appVersion: String = "",
    private val subtitleStyleApplies: Boolean = true,
) : ViewModel() {
    private val confirmSignOut = MutableStateFlow(false)

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
            profileSettings.maxStreamingBitrate,
            profileSettings.captionsFollowSystem,
            profileSettings.subtitleSize,
            profileSettings.subtitleTextColor,
            profileSettings.subtitleBackground,
            connection.state,
            servers.servers,
            confirmSignOut,
        ),
    ) { v ->
        val defaults = SettingsValues()
        val showStats = v[1] as Boolean?
        val threshold = v[2] as Int?
        val connected = v[14] as? ConnectionState.Connected
        // The saved server, not the one the connection started with: addresses can be added later.
        val server = (v[15] as List<*>).filterIsInstance<Server>().firstOrNull { it.id == connected?.server?.id } ?: connected?.server
        val values = SettingsValues(
            alwaysAskWhoIsWatching = v[0] as Boolean,
            appVersion = appVersion,
            subtitleStyleApplies = subtitleStyleApplies,
            hasProfile = showStats != null || threshold != null,
            showPlaybackStats = showStats ?: defaults.showPlaybackStats,
            bingeThreshold = threshold ?: defaults.bingeThreshold,
            homeGenreRows = v[3] as Int? ?: defaults.homeGenreRows,
            showHomeHero = v[4] as Boolean? ?: defaults.showHomeHero,
            skipBackSeconds = v[5] as Int? ?: defaults.skipBackSeconds,
            skipForwardSeconds = v[6] as Int? ?: defaults.skipForwardSeconds,
            autoSkipSegments = v[7] as Boolean? ?: defaults.autoSkipSegments,
            bufferAheadSeconds = v[8] as Int? ?: defaults.bufferAheadSeconds,
            maxStreamingBitrate = v[9] as Int? ?: defaults.maxStreamingBitrate,
            captionsFollowSystem = v[10] as Boolean? ?: defaults.captionsFollowSystem,
            subtitleSize = v[11] as Int? ?: defaults.subtitleSize,
            subtitleTextColor = v[12] as Int? ?: defaults.subtitleTextColor,
            subtitleBackground = v[13] as Int? ?: defaults.subtitleBackground,
            profileName = connected?.account?.name.orEmpty(),
            serverName = server?.name.orEmpty(),
            serverAddresses = server?.addresses.orEmpty(),
        )
        SettingsUiState(
            pages = settingsPages(values),
            hasProfile = values.hasProfile,
            confirmSignOut = v[16] as Boolean,
            serverId = connected?.server?.id.orEmpty(),
            alwaysAskWhoIsWatching = values.alwaysAskWhoIsWatching,
            showPlaybackStats = values.showPlaybackStats,
            bingeThreshold = values.bingeThreshold,
            homeGenreRows = values.homeGenreRows,
            showHomeHero = values.showHomeHero,
            skipBackSeconds = values.skipBackSeconds,
            skipForwardSeconds = values.skipForwardSeconds,
            autoSkipSegments = values.autoSkipSegments,
            bufferAheadSeconds = values.bufferAheadSeconds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** A [ToggleItem] was switched. Keys that are not toggles are ignored. */
    fun onToggle(key: SettingKey, value: Boolean) {
        viewModelScope.launch {
            when (key) {
                SettingKey.AlwaysAsk -> preferences.setAlwaysAskWhoIsWatching(value)
                SettingKey.ShowStats -> profileSettings.setShowPlaybackStats(value)
                SettingKey.ShowHero -> profileSettings.setShowHomeHero(value)
                SettingKey.AutoSkip -> profileSettings.setAutoSkipSegments(value)
                SettingKey.CaptionsFollowSystem -> profileSettings.setCaptionsFollowSystem(value)
                else -> Unit
            }
        }
    }

    /** A [ChoiceItem] option was picked. Keys that are not choices are ignored; the setters ignore values off their lists. */
    fun onChoose(key: SettingKey, value: Int) {
        viewModelScope.launch {
            when (key) {
                SettingKey.BingeThreshold -> profileSettings.setBingeThreshold(value)
                SettingKey.HomeGenreRows -> profileSettings.setHomeGenreRows(value)
                SettingKey.SkipBack -> profileSettings.setSkipBackSeconds(value)
                SettingKey.SkipForward -> profileSettings.setSkipForwardSeconds(value)
                SettingKey.BufferAhead -> profileSettings.setBufferAheadSeconds(value)
                SettingKey.Quality -> profileSettings.setMaxStreamingBitrate(value)
                SettingKey.SubtitleSize -> profileSettings.setSubtitleSize(value)
                SettingKey.SubtitleColor -> profileSettings.setSubtitleTextColor(value)
                SettingKey.SubtitleBackground -> profileSettings.setSubtitleBackground(value)
                else -> Unit
            }
        }
    }

    /** An [ActionItem] was tapped. Keys without an action yet are ignored. */
    fun onAction(key: SettingKey) {
        viewModelScope.launch {
            when (key) {
                SettingKey.ResetSubtitles -> profileSettings.resetSubtitleStyle()
                SettingKey.SwitchProfile -> connection.switchProfile()
                SettingKey.SignOut -> confirmSignOut.value = true
                else -> Unit
            }
        }
    }

    /** The user confirmed signing out: revokes the token and returns to "Who's watching?". */
    fun onConfirmSignOut() {
        confirmSignOut.value = false
        // The screen leaves right away and clears this view model, so the revoke and the lock must not depend on viewModelScope.
        applicationScope.launch { connection.signOut() }
    }

    fun onDismissSignOut() {
        confirmSignOut.value = false
    }

    // The handlers below are removed with the native renderers in Tasks 7 and 8.

    fun onAlwaysAskChanged(value: Boolean) = onToggle(SettingKey.AlwaysAsk, value)

    fun onShowPlaybackStatsChanged(value: Boolean) = onToggle(SettingKey.ShowStats, value)

    fun onBingeThresholdChanged(value: Int) = onChoose(SettingKey.BingeThreshold, value)

    fun onShowHomeHeroChanged(value: Boolean) = onToggle(SettingKey.ShowHero, value)

    fun onHomeGenreRowsChanged(value: Int) = onChoose(SettingKey.HomeGenreRows, value)

    fun onSkipBackChanged(value: Int) = onChoose(SettingKey.SkipBack, value)

    fun onSkipForwardChanged(value: Int) = onChoose(SettingKey.SkipForward, value)

    fun onBufferAheadChanged(value: Int) = onChoose(SettingKey.BufferAhead, value)

    fun onAutoSkipChanged(value: Boolean) = onToggle(SettingKey.AutoSkip, value)
}
