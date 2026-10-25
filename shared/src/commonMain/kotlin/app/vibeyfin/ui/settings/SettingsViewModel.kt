package app.vibeyfin.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.DevicePreferences
import app.vibeyfin.domain.PinGates
import app.vibeyfin.domain.PinLock
import app.vibeyfin.domain.ProfilePolicy
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.domain.ServerRepository
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.model.Server
import app.vibeyfin.ui.common.PinGate
import app.vibeyfin.ui.common.PinPrompt
import app.vibeyfin.ui.common.PinReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
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
    /** A kids profile is signed in; its pages leave out Advanced, Sign out and Notifications. */
    val kidsProfile: Boolean = false,
    /** The PIN sheet, while one is open. */
    val pinPrompt: PinPrompt? = null,
    /**
     * Settings waits for the profile's PIN, or has not yet found out whether it needs one: draw no pages while true.
     * It starts true so a gated profile never sees its settings flash by.
     */
    val locked: Boolean = true,
    /** The PIN to open Settings was dismissed: the native screen goes back. */
    val leave: Boolean = false,
    /** The connection is Disconnected (switch profile, sign out), not merely expired: the native screen leaves. */
    val disconnected: Boolean = false,
)

/** Device-wide settings from [DevicePreferences] and the signed-in profile's from the database, built into pages by [settingsPages]. */
class SettingsViewModel(
    private val preferences: DevicePreferences,
    private val profileSettings: ProfileSettings,
    private val connection: ConnectionManager,
    private val servers: ServerRepository,
    private val pins: PinLock,
    private val policy: ProfilePolicy,
    auth: AuthRepository,
    private val applicationScope: CoroutineScope,
    private val appVersion: String = "",
    private val subtitleStyleApplies: Boolean = true,
) : ViewModel() {
    private val confirmSignOut = MutableStateFlow(false)
    private val pin = PinGate(pins, auth, viewModelScope)
    private val locked = MutableStateFlow(true)
    private val leave = MutableStateFlow(false)
    private val policyState = MutableStateFlow<PolicyState>(PolicyState.Loading)
    private var policyLoad: Job? = null

    init {
        // Decided per profile as the connection gets one, so Settings opened before the profile is signed in still asks.
        // The same profile coming back (Forgot PIN signs in again) is not asked twice.
        viewModelScope.launch {
            connection.state
                .map { (it as? ConnectionState.Connected)?.account }
                .distinctUntilChangedBy { it?.serverId to it?.userId }
                .collect { account ->
                    // Limits read for the previous profile must not show for this one.
                    policyLoad?.cancel()
                    policyState.value = PolicyState.Loading
                    val gates = account?.let { pins.gates(it.serverId, it.userId) } ?: PinGates()
                    if (account != null && gates.openSettings) {
                        locked.value = true
                        pin.ask(PinReason.OpenSettings, account.serverId, account.userId, account.name, onDismiss = { leave.value = true }) {
                            locked.value = false
                        }
                    } else {
                        locked.value = false
                    }
                }
        }
        // A dialog left open while the connection drops (a revoked token, say) would otherwise wait for a session that is gone.
        viewModelScope.launch {
            connection.state.collect { if (it !is ConnectionState.Connected) confirmSignOut.value = false }
        }
    }

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
            profileSettings.kidsProfile,
            pin.prompt,
            locked,
            leave,
            policyState,
            profileSettings.pinGates,
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
            kidsProfile = v[17] as Boolean? ?: false,
            pinGates = v[22] as PinGates? ?: PinGates(),
            policy = v[21] as PolicyState,
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
            kidsProfile = values.kidsProfile,
            pinPrompt = v[18] as PinPrompt?,
            locked = v[19] as Boolean,
            leave = v[20] as Boolean,
            disconnected = v[14] is ConnectionState.Disconnected,
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
                SettingKey.KidsProfile -> if (value) {
                    profileSettings.setKidsProfile(true)
                } else {
                    // Turning kids mode on needs nothing; turning it off asks for the PIN whenever there is one.
                    guarded(PinReason.KidsOff, { it.hasPin }) { profileSettings.setKidsProfile(false) }
                }
                SettingKey.PinOpenProfile -> profileSettings.setPinOpenProfile(value)
                SettingKey.PinOpenSettings -> profileSettings.setPinOpenSettings(value)
                SettingKey.PinSwitchProfile -> profileSettings.setPinSwitchProfile(value)
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
                SettingKey.SwitchProfile -> guarded(PinReason.SwitchProfile, { it.switchProfile }) { connection.switchProfile() }
                // A kids profile has no Sign out row; this keeps a stale tap from reaching it.
                SettingKey.SignOut -> if (profileSettings.kidsProfile.first() != true) confirmSignOut.value = true
                SettingKey.RemovePin -> guarded(PinReason.RemovePin, { it.hasPin }) { pins.removePin() }
                SettingKey.SetPin -> choosePin()
                // The current PIN first, then a new one typed twice.
                SettingKey.ChangePin -> guarded(PinReason.ChangePin, { it.hasPin }) { choosePin() }
                else -> Unit
            }
        }
    }

    /**
     * A page was opened. The Kids and PIN page reads the Jellyfin user's limits then, every time it opens and not
     * cached; reopening it is the retry after a failed read. Other pages need nothing.
     */
    fun onPageOpened(page: SettingsPageId) {
        if (page != SettingsPageId.KidsAndPin) return
        policyLoad?.cancel()
        policyState.value = PolicyState.Loading
        policyLoad = viewModelScope.launch {
            policyState.value = when (val result = policy.load()) {
                is ApiResult.Success -> PolicyState.Loaded(result.value)
                is ApiResult.Failure -> PolicyState.Failed(result.error)
            }
        }
    }

    private fun choosePin() {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        pin.choose(account.serverId, account.userId, account.name)
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

    /** A PIN was entered in the open PIN sheet. */
    fun onSubmitPin(pin: String) = this.pin.submitPin(pin)

    /** "Forgot PIN?": the sheet asks for the profile's Jellyfin password instead. */
    fun onForgotPin() = pin.forgotPin()

    /** The Jellyfin password entered after "Forgot PIN?". */
    fun onSubmitPassword(password: String) = pin.submitPassword(password)

    fun onDismissPin() = pin.dismiss()

    /** Runs [action] for the signed-in profile, after its PIN when [needed] says so for its current [PinGates]. */
    private suspend fun guarded(reason: PinReason, needed: (PinGates) -> Boolean, action: suspend () -> Unit) {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        if (needed(pins.gates(account.serverId, account.userId))) {
            pin.ask(reason, account.serverId, account.userId, account.name, onPass = action)
        } else {
            action()
        }
    }
}
