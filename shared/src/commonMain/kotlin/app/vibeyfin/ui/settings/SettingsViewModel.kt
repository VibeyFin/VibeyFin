package app.vibeyfin.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.DevicePreferences
import app.vibeyfin.domain.DownloadRepository
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
import app.vibeyfin.domain.model.Account
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
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
     * Settings waits for the profile's PIN, or has not yet found out whether it needs one: draw no pages while true, only a
     * progress indicator (and the PIN sheet once it is asked). It starts true so a gated profile never sees its settings flash by.
     */
    val locked: Boolean = true,
    /** The PIN to open Settings was dismissed: the native screen goes back. */
    val leave: Boolean = false,
    /** The connection is Disconnected (switch profile, sign out), not merely expired: the native screen leaves. */
    val disconnected: Boolean = false,
    /**
     * One-shot: notifications were just switched on, so the native screen asks the OS for notification permission and
     * then calls [SettingsViewModel.onNotificationPermissionRequested]. If the OS says no, it calls
     * [SettingsViewModel.onNotificationPermissionDenied].
     */
    val requestNotificationPermission: Boolean = false,
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
    private val downloads: DownloadRepository,
    private val applicationScope: CoroutineScope,
    private val appVersion: String = "",
    private val subtitleStyleApplies: Boolean = true,
) : ViewModel() {
    private val confirmSignOut = MutableStateFlow(false)
    private val pin = PinGate(pins, auth, viewModelScope)
    /**
     * The profile whose PIN to open Settings is known not to be needed or has been entered. Only the gate collector below and a passed
     * PIN set it; the state is locked for every other profile, so the lock comes from the data and not from which collector ran first.
     */
    private val unlockedFor = MutableStateFlow<ProfileKey?>(null)
    private val leave = MutableStateFlow(false)
    private val requestNotificationPermission = MutableStateFlow(false)
    private val notificationsBlocked = MutableStateFlow(false)
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
                    // A profile met again (after Disconnected) is decided again.
                    unlockedFor.value = null
                    if (account == null) return@collect
                    val key = account.key()
                    if (pins.gates(account.serverId, account.userId).openSettings) {
                        pin.ask(PinReason.OpenSettings, account.serverId, account.userId, account.name, onDismiss = { leave.value = true }) {
                            unlockedFor.value = key
                        }
                    } else {
                        unlockedFor.value = key
                    }
                }
        }
        // A dialog left open while the connection drops (a revoked token, say) would otherwise wait for a session that is gone.
        viewModelScope.launch {
            connection.state.collect { if (it !is ConnectionState.Connected) confirmSignOut.value = false }
        }
    }

    /**
     * Each source with the value it starts from. A Room or DataStore read starts from null ("not read yet"), so the page is drawn from
     * the defaults at once and no source that is slow on a cold start, or right after a sign-in, holds the others back.
     */
    private val sources: List<Pair<Flow<Any?>, Any?>> = listOf(
        unread(preferences.alwaysAskWhoIsWatching),
        perProfile(profileSettings.showPlaybackStats),
        perProfile(profileSettings.bingeThreshold),
        perProfile(profileSettings.homeGenreRows),
        perProfile(profileSettings.showHomeHero),
        perProfile(profileSettings.skipBackSeconds),
        perProfile(profileSettings.skipForwardSeconds),
        perProfile(profileSettings.autoSkipSegments),
        perProfile(profileSettings.bufferAheadSeconds),
        perProfile(profileSettings.maxStreamingBitrate),
        perProfile(profileSettings.captionsFollowSystem),
        perProfile(profileSettings.subtitleSize),
        perProfile(profileSettings.subtitleTextColor),
        perProfile(profileSettings.subtitleBackground),
        live(connection.state),
        servers.servers.onStart { emit(emptyList()) } to emptyList<Server>(),
        live(confirmSignOut),
        perProfile(profileSettings.kidsProfile),
        live(pin.prompt),
        live(unlockedFor),
        live(leave),
        live(policyState),
        perProfile(profileSettings.pinGates),
        perProfile(profileSettings.notificationsEnabled),
        perProfile(profileSettings.notificationScope),
        live(requestNotificationPermission),
        live(notificationsBlocked),
        perProfile(profileSettings.pictureInPicture),
        unread(preferences.offlineMode),
        perProfile(profileSettings.downloadOverMobile),
        perProfile(profileSettings.storageLimitMb),
    )

    /** Eager, so the reads start with the ViewModel and not when the screen first draws. */
    val uiState: StateFlow<SettingsUiState> = combine(sources.map { it.first }) { state(it.asList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, state(sources.map { it.second }))

    /**
     * A profile setting tagged with the profile it was read for, starting from "not read yet" (null) each time the profile changes, so
     * one profile's values never show under another, whatever order the sources arrive in.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> perProfile(flow: Flow<T?>): Pair<Flow<Any?>, Any?> = connection.state
        .map { (it as? ConnectionState.Connected)?.account?.key() }
        .distinctUntilChanged()
        .flatMapLatest { key -> flow.map { ProfileRead(key, it) }.onStart { emit(ProfileRead(key, null)) } } to ProfileRead(null, null)

    private fun state(v: List<Any?>): SettingsUiState {
        val defaults = SettingsValues()
        val connected = v[CONNECTION] as? ConnectionState.Connected
        val profile = connected?.account?.key()

        /** The read at [index] when it is for the profile shown now; null (the default) when not read yet or for another profile. */
        fun <T> read(index: Int): T? {
            val read = v[index] as ProfileRead
            @Suppress("UNCHECKED_CAST")
            return if (profile != null && read.profile == profile) read.value as T? else null
        }

        // The saved server, not the one the connection started with: addresses can be added later.
        val server = (v[SERVERS] as List<*>).filterIsInstance<Server>().firstOrNull { it.id == connected?.server?.id } ?: connected?.server
        val kids = read<Boolean>(KIDS)
        val values = SettingsValues(
            alwaysAskWhoIsWatching = v[ALWAYS_ASK] as Boolean? ?: defaults.alwaysAskWhoIsWatching,
            appVersion = appVersion,
            subtitleStyleApplies = subtitleStyleApplies,
            hasProfile = connected != null,
            // Until the flag is read a signed-in profile counts as a kids one, so rows a kids profile must not see never flash by.
            kidsProfile = kids ?: (connected != null),
            pinGates = read(PIN_GATES) ?: PinGates(),
            policy = v[POLICY] as PolicyState,
            notificationsEnabled = read(NOTIFICATIONS) ?: defaults.notificationsEnabled,
            notificationScope = read(NOTIFICATION_SCOPE) ?: defaults.notificationScope,
            notificationsBlocked = v[NOTIFICATIONS_BLOCKED] as Boolean,
            showPlaybackStats = read(SHOW_STATS) ?: defaults.showPlaybackStats,
            bingeThreshold = read(BINGE) ?: defaults.bingeThreshold,
            homeGenreRows = read(GENRE_ROWS) ?: defaults.homeGenreRows,
            showHomeHero = read(HERO) ?: defaults.showHomeHero,
            skipBackSeconds = read(SKIP_BACK) ?: defaults.skipBackSeconds,
            skipForwardSeconds = read(SKIP_FORWARD) ?: defaults.skipForwardSeconds,
            autoSkipSegments = read(AUTO_SKIP) ?: defaults.autoSkipSegments,
            bufferAheadSeconds = read(BUFFER_AHEAD) ?: defaults.bufferAheadSeconds,
            pictureInPicture = read(PICTURE_IN_PICTURE) ?: defaults.pictureInPicture,
            offlineMode = v[OFFLINE_MODE] as Boolean? ?: defaults.offlineMode,
            downloadOverMobile = read(DOWNLOAD_OVER_MOBILE) ?: defaults.downloadOverMobile,
            storageLimitGb = read<Long>(STORAGE_LIMIT)?.let { (it / MB_PER_GB).toInt() } ?: defaults.storageLimitGb,
            maxStreamingBitrate = read(QUALITY) ?: defaults.maxStreamingBitrate,
            captionsFollowSystem = read(CAPTIONS_FOLLOW_SYSTEM) ?: defaults.captionsFollowSystem,
            subtitleSize = read(SUBTITLE_SIZE) ?: defaults.subtitleSize,
            subtitleTextColor = read(SUBTITLE_COLOR) ?: defaults.subtitleTextColor,
            subtitleBackground = read(SUBTITLE_BACKGROUND) ?: defaults.subtitleBackground,
            profileName = connected?.account?.name.orEmpty(),
            serverName = server?.name.orEmpty(),
            serverAddresses = server?.addresses.orEmpty(),
        )
        return SettingsUiState(
            pages = settingsPages(values),
            hasProfile = values.hasProfile,
            confirmSignOut = v[CONFIRM_SIGN_OUT] as Boolean,
            serverId = connected?.server?.id.orEmpty(),
            kidsProfile = kids == true,
            pinPrompt = v[PIN_PROMPT] as PinPrompt?,
            // Locked for a signed-in profile until its gate is known to be off or its PIN was entered; never by default unlocked.
            locked = profile != null && v[UNLOCKED_FOR] != profile,
            leave = v[LEAVE] as Boolean,
            disconnected = v[CONNECTION] is ConnectionState.Disconnected,
            requestNotificationPermission = v[REQUEST_PERMISSION] as Boolean,
        )
    }

    /** A [ToggleItem] was switched. Keys that are not toggles are ignored. */
    fun onToggle(key: SettingKey, value: Boolean) {
        viewModelScope.launch {
            when (key) {
                SettingKey.AlwaysAsk -> preferences.setAlwaysAskWhoIsWatching(value)
                SettingKey.ShowStats -> profileSettings.setShowPlaybackStats(value)
                SettingKey.ShowHero -> profileSettings.setShowHomeHero(value)
                SettingKey.AutoSkip -> profileSettings.setAutoSkipSegments(value)
                SettingKey.PictureInPicture -> profileSettings.setPictureInPicture(value)
                SettingKey.OfflineMode -> preferences.setOfflineMode(value)
                // A download that waits for Wi-Fi is looked at again at once.
                SettingKey.DownloadOverMobile -> {
                    profileSettings.setDownloadOverMobile(value)
                    downloads.schedule()
                }
                SettingKey.CaptionsFollowSystem -> profileSettings.setCaptionsFollowSystem(value)
                SettingKey.KidsProfile -> if (value) {
                    profileSettings.setKidsProfile(true)
                } else {
                    // Turning kids mode on needs nothing; turning it off asks for the PIN whenever there is one.
                    guarded(PinReason.KidsOff, { it.hasPin }) { profileSettings.setKidsProfile(false) }
                }
                SettingKey.Notifications -> {
                    // A kids profile has no Notifications page; this keeps a stale toggle from reaching it.
                    if (profileSettings.kidsProfile.first() == true) return@launch
                    profileSettings.setNotificationsEnabled(value)
                    notificationsBlocked.value = false
                    requestNotificationPermission.value = value
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
                SettingKey.NotificationScope -> profileSettings.setNotificationScope(value)
                // In gigabytes; 0 is no limit.
                SettingKey.StorageLimit -> {
                    profileSettings.setStorageLimitMb(value * MB_PER_GB)
                    downloads.schedule()
                }
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

    /** The native screen showed the OS permission prompt (or found it needless): the one-shot is spent. */
    fun onNotificationPermissionRequested() {
        requestNotificationPermission.value = false
    }

    /** The OS refused notification permission: the setting goes back off and the page says where to allow it. */
    fun onNotificationPermissionDenied() {
        requestNotificationPermission.value = false
        notificationsBlocked.value = true
        viewModelScope.launch { profileSettings.setNotificationsEnabled(false) }
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

private fun <T> unread(flow: Flow<T?>): Pair<Flow<Any?>, Any?> = flow.onStart { emit(null) } to null

private fun <T> live(flow: StateFlow<T>): Pair<Flow<Any?>, Any?> = flow to flow.value

/** A profile by server and user id. */
private typealias ProfileKey = Pair<String, String>

private fun Account.key(): ProfileKey = serverId to userId

/** A profile setting and the profile it was read for; [value] null is "not read yet". */
private data class ProfileRead(val profile: ProfileKey?, val value: Any?)

// The positions of the sources in [SettingsViewModel.sources].
private const val ALWAYS_ASK = 0
private const val SHOW_STATS = 1
private const val BINGE = 2
private const val GENRE_ROWS = 3
private const val HERO = 4
private const val SKIP_BACK = 5
private const val SKIP_FORWARD = 6
private const val AUTO_SKIP = 7
private const val BUFFER_AHEAD = 8
private const val QUALITY = 9
private const val CAPTIONS_FOLLOW_SYSTEM = 10
private const val SUBTITLE_SIZE = 11
private const val SUBTITLE_COLOR = 12
private const val SUBTITLE_BACKGROUND = 13
private const val CONNECTION = 14
private const val SERVERS = 15
private const val CONFIRM_SIGN_OUT = 16
private const val KIDS = 17
private const val PIN_PROMPT = 18
private const val UNLOCKED_FOR = 19
private const val LEAVE = 20
private const val POLICY = 21
private const val PIN_GATES = 22
private const val NOTIFICATIONS = 23
private const val NOTIFICATION_SCOPE = 24
private const val REQUEST_PERMISSION = 25
private const val NOTIFICATIONS_BLOCKED = 26
private const val PICTURE_IN_PICTURE = 27
private const val OFFLINE_MODE = 28
private const val DOWNLOAD_OVER_MOBILE = 29
private const val STORAGE_LIMIT = 30

private const val MB_PER_GB = 1_000L
