package app.vibeyfin.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.DevicePreferences
import app.vibeyfin.domain.DownloadRepository
import app.vibeyfin.domain.Language
import app.vibeyfin.domain.LanguagePreferences
import app.vibeyfin.domain.LanguagePrefs
import app.vibeyfin.domain.PinGates
import app.vibeyfin.domain.PinLock
import app.vibeyfin.domain.ProfilePolicy
import app.vibeyfin.domain.ProfileSettingDefinitions
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.domain.ProfileValues
import app.vibeyfin.domain.SeerrConnection
import app.vibeyfin.domain.SeerrState
import app.vibeyfin.domain.ServerRepository
import app.vibeyfin.domain.SubtitleMode
import app.vibeyfin.domain.accountOrNull
import app.vibeyfin.domain.model.Account
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.domain.model.Server
import app.vibeyfin.i18n.AppLanguage
import app.vibeyfin.i18n.Texts
import app.vibeyfin.ui.common.KidsGuard
import app.vibeyfin.ui.common.PinGate
import app.vibeyfin.ui.common.PinPrompt
import app.vibeyfin.ui.common.PinReason
import app.vibeyfin.ui.common.PinSheetControls
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    /** "Delete downloaded files" was tapped and waits for the confirmation. */
    val confirmPurgeDownloads: Boolean = false,
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
    /**
     * One-shot: the page the native screen should close, as after picking a language from its list. The screen showing that page goes
     * back and calls [SettingsViewModel.onPageClosed]. Null otherwise.
     */
    val closePage: SettingsPageId? = null,
)

/** Device-wide settings from [DevicePreferences] and the signed-in profile's from the database, built into pages by [settingsPages]. */
class SettingsViewModel(
    private val preferences: DevicePreferences,
    private val profileSettings: ProfileSettings,
    private val connection: ConnectionManager,
    private val servers: ServerRepository,
    private val pins: PinLock,
    private val policy: ProfilePolicy,
    private val languagePreferences: LanguagePreferences,
    auth: AuthRepository,
    private val downloads: DownloadRepository,
    private val applicationScope: CoroutineScope,
    private val appVersion: String = "",
    private val subtitleStyleApplies: Boolean = true,
    private val downloadAlerts: Boolean = false,
    seerr: SeerrConnection? = null,
    private val appLanguages: List<AppLanguage> = emptyList(),
    language: StateFlow<String> = Texts.language,
) : ViewModel(),
    PinSheetControls {
    private val confirmSignOut = MutableStateFlow(false)
    private val confirmPurgeDownloads = MutableStateFlow(false)
    private val pin = PinGate(pins, auth, viewModelScope)
    private val kidsGuard = KidsGuard(profileSettings)

    // The profile whose PIN to open Settings is known not to be needed or has been entered. Only the gate collector below and a passed
    // PIN set it; the state is locked for every other profile, so the lock comes from the data and not from which collector ran first.
    private val unlockedFor = MutableStateFlow<ProfileKey?>(null)
    private val leave = MutableStateFlow(false)
    private val requestNotificationPermission = MutableStateFlow(false)
    private val notificationsBlocked = MutableStateFlow(false)
    private val policyState = MutableStateFlow<PolicyState>(PolicyState.Loading)
    private var policyLoad: Job? = null
    private val languagePrefs = MutableStateFlow<LanguagePrefs?>(null)
    private val languages = MutableStateFlow<List<Language>>(emptyList())
    private val languageStatus = MutableStateFlow<LanguageStatus>(LanguageStatus.Loading)
    private val searches = MutableStateFlow<Map<SettingsPageId, String>>(emptyMap())
    private val closePage = MutableStateFlow<SettingsPageId?>(null)
    private var languageLoad: Job? = null

    init {
        // Decided per profile as the connection gets one, so Settings opened before the profile is signed in still asks.
        // The same profile coming back (Forgot PIN signs in again) is not asked twice.
        viewModelScope.launch {
            connection.state
                .map { it.accountOrNull() }
                .distinctUntilChangedBy { it?.serverId to it?.userId }
                .collect { account ->
                    // Limits read for the previous profile must not show for this one.
                    policyLoad?.cancel()
                    policyState.value = PolicyState.Loading
                    // Nor its languages: they are read afresh for each profile, so the root row can sum them up.
                    languageLoad?.cancel()
                    languagePrefs.value = null
                    languages.value = emptyList()
                    languageStatus.value = LanguageStatus.Loading
                    searches.value = emptyMap()
                    if (account != null) loadLanguages()
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
            connection.state.collect {
                if (it !is ConnectionState.Connected) {
                    confirmSignOut.value = false
                    confirmPurgeDownloads.value = false
                }
            }
        }
    }

    // Each source with the value it starts from. A Room or DataStore read starts from null ("not read yet"), so the page is drawn from
    // the defaults at once and no source that is slow on a cold start, or right after a sign-in, holds the others back.
    private val sources: List<Pair<Flow<Any?>, Any?>> = listOf(
        unread(preferences.alwaysAskWhoIsWatching),
        profileSettings.values to ProfileValues(null),
        live(connection.state),
        servers.servers.onStart { emit(emptyList()) } to emptyList<Server>(),
        live(confirmSignOut),
        live(pin.prompt),
        live(unlockedFor),
        live(leave),
        live(policyState),
        live(requestNotificationPermission),
        live(notificationsBlocked),
        unread(preferences.offlineMode),
        live(languagePrefs),
        live(languages),
        live(languageStatus),
        live(searches),
        live(closePage),
        live(confirmPurgeDownloads),
        live(seerr?.state ?: MutableStateFlow<SeerrState>(SeerrState.Hidden)),
        unread(preferences.appLanguage),
        live(language),
    )

    /** Eager, so the reads start with the ViewModel and not when the screen first draws. */
    val uiState: StateFlow<SettingsUiState> = combine(sources.map { it.first }) { state(it.asList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, state(sources.map { it.second }))

    private fun state(v: List<Any?>): SettingsUiState {
        val defaults = SettingsValues()
        val connected = v[CONNECTION] as? ConnectionState.Connected
        val profile = connected?.account?.key()
        val settings = (v[PROFILE] as ProfileValues).shownFor(connected?.account?.let { ProfileRef(it.serverId, it.userId) })

        // The saved server, not the one the connection started with: addresses can be added later.
        val server = (v[SERVERS] as List<*>).filterIsInstance<Server>().firstOrNull { it.id == connected?.server?.id } ?: connected?.server
        val kids = settings.readOrNull(ProfileSettingDefinitions.KidsProfile)
        val values = SettingsValues(
            alwaysAskWhoIsWatching = v[ALWAYS_ASK] as Boolean? ?: defaults.alwaysAskWhoIsWatching,
            appLanguage = v[APP_LANGUAGE] as String?,
            appLanguages = appLanguages,
            appVersion = appVersion,
            subtitleStyleApplies = subtitleStyleApplies,
            hasProfile = settings.hasProfile,
            // Until the flag is read a signed-in profile counts as a kids one, so rows a kids profile must not see never flash by.
            kidsProfile = kids ?: settings.hasProfile,
            pinGates = settings[ProfileSettingDefinitions.PinGateSwitches],
            policy = v[POLICY] as PolicyState,
            notificationsEnabled = settings[ProfileSettingDefinitions.NotificationsEnabled],
            notificationScope = settings[ProfileSettingDefinitions.NotificationScope],
            downloadNotifications = settings[ProfileSettingDefinitions.DownloadNotifications],
            downloadAlerts = downloadAlerts,
            notificationsBlocked = v[NOTIFICATIONS_BLOCKED] as Boolean,
            showPlaybackStats = settings[ProfileSettingDefinitions.ShowPlaybackStats],
            bingeThreshold = settings[ProfileSettingDefinitions.BingeThreshold],
            homeGenreRows = settings[ProfileSettingDefinitions.HomeGenreRows],
            homeHeroItems = settings[ProfileSettingDefinitions.HomeHeroItems],
            showWatchedInRows = settings[ProfileSettingDefinitions.ShowWatchedInRows],
            skipBackSeconds = settings[ProfileSettingDefinitions.SkipBackSeconds],
            skipForwardSeconds = settings[ProfileSettingDefinitions.SkipForwardSeconds],
            autoSkipSegments = settings[ProfileSettingDefinitions.AutoSkipSegments],
            bufferAheadSeconds = settings[ProfileSettingDefinitions.BufferAheadSeconds],
            pictureInPicture = settings[ProfileSettingDefinitions.PictureInPicture],
            swipeGestures = settings[ProfileSettingDefinitions.SwipeGestures],
            dimWhenPausedMinutes = settings[ProfileSettingDefinitions.DimWhenPausedMinutes],
            offlineMode = v[OFFLINE_MODE] as Boolean? ?: defaults.offlineMode,
            downloadOverMobile = settings[ProfileSettingDefinitions.DownloadOverMobile],
            storageLimitGb = (settings[ProfileSettingDefinitions.StorageLimitMb] / MB_PER_GB).toInt(),
            maxStreamingBitrate = settings[ProfileSettingDefinitions.MaxStreamingBitrate],
            captionsFollowSystem = settings[ProfileSettingDefinitions.CaptionsFollowSystem],
            subtitleSize = settings[ProfileSettingDefinitions.SubtitleSize],
            subtitleTextColor = settings[ProfileSettingDefinitions.SubtitleTextColor],
            subtitleBackground = settings[ProfileSettingDefinitions.SubtitleBackground],
            profileName = connected?.account?.name.orEmpty(),
            serverName = server?.name.orEmpty(),
            serverAddresses = server?.addresses.orEmpty(),
            languagePrefs = v[LANGUAGE_PREFS] as LanguagePrefs?,
            languages = v[LANGUAGES].cast(),
            languageStatus = v[LANGUAGE_STATUS] as LanguageStatus,
            searches = v[SEARCHES].cast(),
            seerrStatus = seerrStatus(v[SEERR] as SeerrState),
        )
        return SettingsUiState(
            pages = settingsPages(values),
            hasProfile = values.hasProfile,
            confirmSignOut = v[CONFIRM_SIGN_OUT] as Boolean,
            confirmPurgeDownloads = v[CONFIRM_PURGE_DOWNLOADS] as Boolean,
            serverId = connected?.server?.id.orEmpty(),
            kidsProfile = kids == true,
            pinPrompt = v[PIN_PROMPT] as PinPrompt?,
            // Locked for a signed-in profile until its gate is known to be off or its PIN was entered; never by default unlocked.
            locked = profile != null && v[UNLOCKED_FOR] != profile,
            leave = v[LEAVE] as Boolean,
            disconnected = v[CONNECTION] is ConnectionState.Disconnected,
            requestNotificationPermission = v[REQUEST_PERMISSION] as Boolean,
            closePage = v[CLOSE_PAGE] as SettingsPageId?,
        )
    }

    /** A [ToggleItem] was switched. Keys that are not toggles are ignored. */
    fun onToggle(key: SettingKey, value: Boolean) {
        viewModelScope.launch {
            when (key) {
                SettingKey.AlwaysAsk -> preferences.setAlwaysAskWhoIsWatching(value)

                SettingKey.ShowStats -> profileSettings.setShowPlaybackStats(value)

                SettingKey.AutoSkip -> profileSettings.setAutoSkipSegments(value)

                SettingKey.PictureInPicture -> profileSettings.setPictureInPicture(value)

                SettingKey.SwipeGestures -> profileSettings.setSwipeGestures(value)

                SettingKey.ShowWatchedInRows -> profileSettings.setShowWatchedInRows(value)

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
                    if (kidsGuard.isKidsProfile()) return@launch
                    profileSettings.setNotificationsEnabled(value)
                    notificationsBlocked.value = false
                    requestNotificationPermission.value = value
                }

                SettingKey.DownloadNotifications -> profileSettings.setDownloadNotifications(value)

                SettingKey.PinOpenProfile -> profileSettings.setPinOpenProfile(value)

                SettingKey.PinOpenSettings -> profileSettings.setPinOpenSettings(value)

                SettingKey.PinSwitchProfile -> profileSettings.setPinSwitchProfile(value)

                SettingKey.RememberTracks -> saveLanguages({ it.copy(rememberTracks = value) }) { languagePreferences.setRememberTracks(value) }

                else -> Unit
            }
        }
    }

    /** A language was picked from [key]'s list by its [code]; "" is no preference. Other keys are ignored. */
    fun onChooseLanguage(key: SettingKey, code: String) {
        viewModelScope.launch {
            val language = code.ifBlank { null }
            when (key) {
                SettingKey.AudioLanguage -> {
                    pickedFrom(SettingsPageId.AudioLanguage)
                    saveLanguages({ it.copy(audioLanguage = language) }) { languagePreferences.setAudioLanguage(language) }
                }

                SettingKey.SubtitleLanguage -> {
                    pickedFrom(SettingsPageId.SubtitleLanguage)
                    saveLanguages({ it.copy(subtitleLanguage = language) }) { languagePreferences.setSubtitleLanguage(language) }
                }

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

                SettingKey.HomeHeroItems -> profileSettings.setHomeHeroItems(value)

                SettingKey.SkipBack -> profileSettings.setSkipBackSeconds(value)

                SettingKey.SkipForward -> profileSettings.setSkipForwardSeconds(value)

                SettingKey.BufferAhead -> profileSettings.setBufferAheadSeconds(value)

                SettingKey.DimWhenPaused -> profileSettings.setDimWhenPausedMinutes(value)

                SettingKey.Quality -> profileSettings.setMaxStreamingBitrate(value)

                SettingKey.SubtitleSize -> profileSettings.setSubtitleSize(value)

                SettingKey.SubtitleColor -> profileSettings.setSubtitleTextColor(value)

                SettingKey.SubtitleBackground -> profileSettings.setSubtitleBackground(value)

                SettingKey.NotificationScope -> profileSettings.setNotificationScope(value)

                SettingKey.SubtitleMode -> {
                    val mode = SubtitleMode.entries.getOrNull(value) ?: return@launch
                    saveLanguages({ it.copy(subtitleMode = mode) }) { languagePreferences.setSubtitleMode(mode) }
                }

                // In gigabytes; 0 is no limit.
                SettingKey.AppLanguage -> preferences.setAppLanguage(appLanguages.getOrNull(value - 1)?.tag)

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
                SettingKey.SignOut -> if (!kidsGuard.isKidsProfile()) confirmSignOut.value = true

                SettingKey.PurgeDownloads -> confirmPurgeDownloads.value = true

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
        // The languages were read with the profile; opening their page is the retry after a failed read.
        if (page == SettingsPageId.AudioSubtitles && languageStatus.value is LanguageStatus.LoadFailed) loadLanguages()
        // A language list opens with its whole list, not the search left from last time.
        if (page == SettingsPageId.AudioLanguage || page == SettingsPageId.SubtitleLanguage) searches.value = searches.value - page
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

    /** The native screen closed the page [SettingsUiState.closePage] asked for: the one-shot is spent. */
    fun onPageClosed() {
        closePage.value = null
    }

    // A language was picked from its list: the list is done with, so its search is cleared and the screen closes it.
    private fun pickedFrom(page: SettingsPageId) {
        searches.value = searches.value - page
        closePage.value = page
    }

    /** What is typed in the search field of [page]; the page comes back filtered. */
    fun onSearch(page: SettingsPageId, query: String) {
        searches.value = searches.value + (page to query)
    }

    // Reads the account's preferred languages, and the languages the server knows, for the profile signed in now.
    private fun loadLanguages() {
        languageLoad?.cancel()
        languageStatus.value = LanguageStatus.Loading
        languageLoad = viewModelScope.launch {
            val read = languagePreferences.load()
            if (read is ApiResult.Success) {
                // The names are a nicety: without them the rows show the codes.
                languages.value = languagePreferences.languages()
                languagePrefs.value = read.value
            }
            languageStatus.value = when (read) {
                is ApiResult.Success -> LanguageStatus.Ready
                is ApiResult.Failure -> LanguageStatus.LoadFailed(read.error)
            }
        }
    }

    // Shows [change] at once and saves it with [save]. What the server saved replaces it; a failure puts the values from before back and
    // says so on the page. Only while the values were read: the rows are inert otherwise.
    private suspend fun saveLanguages(change: (LanguagePrefs) -> LanguagePrefs, save: suspend () -> ApiResult<LanguagePrefs>) {
        val before = languagePrefs.value ?: return
        if (languageStatus.value !is LanguageStatus.Ready && languageStatus.value !is LanguageStatus.SaveFailed) return
        languagePrefs.value = change(before)
        when (val saved = save()) {
            is ApiResult.Success -> {
                languagePrefs.value = saved.value
                languageStatus.value = LanguageStatus.Ready
            }

            is ApiResult.Failure -> {
                languagePrefs.value = before
                languageStatus.value = LanguageStatus.SaveFailed(saved.error)
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
        connection.asAccount(Unit) { pin.choose(it.serverId, it.userId, it.name) }
    }

    /** The user confirmed signing out: revokes the token and returns to "Who's watching?". */
    fun onConfirmSignOut() {
        confirmSignOut.value = false
        val account = connection.state.value.accountOrNull()
        // The screen leaves right away and clears this view model, so the revoke, the lock and the clean-up must not depend on viewModelScope.
        applicationScope.launch {
            account?.let { downloads.purge(ProfileRef(it.serverId, it.userId)) }
            connection.signOut()
        }
    }

    fun onDismissSignOut() {
        confirmSignOut.value = false
    }

    /** The user confirmed deleting the profile's downloaded files. */
    fun onConfirmPurgeDownloads() {
        confirmPurgeDownloads.value = false
        connection.asAccount(Unit) { account ->
            // Deleting gigabytes must not stop because the screen was left.
            applicationScope.launch { downloads.purge(ProfileRef(account.serverId, account.userId)) }
        }
    }

    fun onDismissPurgeDownloads() {
        confirmPurgeDownloads.value = false
    }

    /** A PIN was entered in the open PIN sheet. */
    override fun onSubmitPin(pin: String) = this.pin.submitPin(pin)

    /** "Forgot PIN?": the sheet asks for the profile's Jellyfin password instead. */
    override fun onForgotPin() = pin.forgotPin()

    /** The Jellyfin password entered after "Forgot PIN?". */
    override fun onSubmitPassword(password: String) = pin.submitPassword(password)

    override fun onDismissPin() = pin.dismiss()

    // Runs [action] for the signed-in profile, after its PIN when [needed] says so for its current [PinGates].
    private suspend fun guarded(reason: PinReason, needed: (PinGates) -> Boolean, action: suspend () -> Unit) {
        connection.asAccount(Unit) { account ->
            if (needed(pins.gates(account.serverId, account.userId))) {
                pin.ask(reason, account.serverId, account.userId, account.name, onPass = action)
            } else {
                action()
            }
        }
    }
}

// The Seerr row's value; null hides it.
private fun seerrStatus(state: SeerrState): String? = when (state) {
    SeerrState.Hidden -> null
    SeerrState.NotSet -> Strings.seerrNotSetUp
    is SeerrState.Off -> Strings.seerrOff
    is SeerrState.Connecting -> Strings.seerrSigningIn
    is SeerrState.Ready -> Strings.seerrConnected
    is SeerrState.NeedsPassword, is SeerrState.Unavailable -> Strings.seerrNotSignedIn
}

// A source's value as the type its flow has; [combine] hands them over as `Any?`.
@Suppress("UNCHECKED_CAST")
private fun <T> Any?.cast(): T = this as T

private fun <T> unread(flow: Flow<T?>): Pair<Flow<Any?>, Any?> = flow.onStart { emit(null) } to null

private fun <T> live(flow: StateFlow<T>): Pair<Flow<Any?>, Any?> = flow to flow.value

/** A profile by server and user id. */
private typealias ProfileKey = Pair<String, String>

private fun Account.key(): ProfileKey = serverId to userId

// The positions of the sources in [SettingsViewModel.sources].
private const val ALWAYS_ASK = 0
private const val PROFILE = 1
private const val CONNECTION = 2
private const val SERVERS = 3
private const val CONFIRM_SIGN_OUT = 4
private const val PIN_PROMPT = 5
private const val UNLOCKED_FOR = 6
private const val LEAVE = 7
private const val POLICY = 8
private const val REQUEST_PERMISSION = 9
private const val NOTIFICATIONS_BLOCKED = 10
private const val OFFLINE_MODE = 11
private const val LANGUAGE_PREFS = 12
private const val LANGUAGES = 13
private const val LANGUAGE_STATUS = 14
private const val SEARCHES = 15
private const val CLOSE_PAGE = 16
private const val CONFIRM_PURGE_DOWNLOADS = 17
private const val SEERR = 18
private const val APP_LANGUAGE = 19

private const val MB_PER_GB = 1_000L
