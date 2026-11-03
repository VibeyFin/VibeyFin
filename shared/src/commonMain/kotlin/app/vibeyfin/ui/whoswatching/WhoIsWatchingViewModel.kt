package app.vibeyfin.ui.whoswatching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.domain.AccountRepository
import app.vibeyfin.domain.ActivateResult
import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.PinLock
import app.vibeyfin.domain.PublicProfiles
import app.vibeyfin.domain.QuickConnectState
import app.vibeyfin.domain.ServerRepository
import app.vibeyfin.domain.SignInResult
import app.vibeyfin.domain.mergeProfiles
import app.vibeyfin.domain.model.Profile
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.domain.model.Server
import app.vibeyfin.ui.common.PinGate
import app.vibeyfin.ui.common.PinPrompt
import app.vibeyfin.ui.common.PinReason
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The sheet open over the profile grid, if any. */
sealed interface SignInSheet {
    /** Asks for [profile]'s password. */
    data class Password(val profile: Profile) : SignInSheet

    /** Asks for a username and password, for a user the server doesn't list. */
    data object OtherUser : SignInSheet

    /**
     * Quick Connect: no code while it is asked for, then [code] and the time left until it expires, then [approved] while
     * signing in. [stop] says why it ended; an expired code stays on screen, crossed out.
     */
    data class QuickConnect(
        val code: String? = null,
        val expiresIn: Duration? = null,
        val approved: Boolean = false,
        val stop: QuickConnectStop? = null,
    ) : SignInSheet {
        val shownCode: String? get() = code?.let(Strings::quickConnectCode)
        val spokenCode: String? get() = code?.let(Strings::quickConnectSpokenCode)
        val status: String? get() = expiresIn?.takeIf { stop == null && !approved }?.let(Strings::quickConnectWaiting)
        val message: String? get() = stop?.let(SignInMessages::forQuickConnect)
    }
}

enum class QuickConnectStop { Expired, Off, Unreachable, Failed }

data class WhoIsWatchingUiState(
    /** True until the server has answered or failed to, so the grid doesn't jump. */
    val loading: Boolean = true,
    val serverName: String? = null,
    val profiles: List<Profile> = emptyList(),
    /** False when the server couldn't be reached; saved profiles still show. */
    val serverReachable: Boolean = true,
    val triedAddresses: List<String> = emptyList(),
    /** The server allows Quick Connect, so the grid offers it. */
    val quickConnectAvailable: Boolean = false,
    val sheet: SignInSheet? = null,
    val signingIn: Boolean = false,
    val openingUserId: String? = null,
    val notice: String? = null,
    val sheetNotice: String? = null,
    /** Shown inside the open sheet, or on the grid when no sheet is open. */
    val error: String? = null,
    /** Signed in; the screen moves on. */
    val signedIn: Boolean = false,
    /** The profile whose tile signed in, so the screen zooms into it on the way out. Null after Other user or Quick Connect. */
    val openedUserId: String? = null,
    /** The PIN sheet, over the grid and any sign-in sheet, while one is open. */
    val pinPrompt: PinPrompt? = null,
) {
    val busy: Boolean get() = signingIn || openingUserId != null
}

/**
 * "Who's watching?" on [serverId]. A tile whose profile asks for its PIN to open asks for it before signing in. With
 * [pinFor] set, the screen opens on that profile's PIN because showing the picker leaves that profile
 * ("switch profile"), and dismissing the PIN goes back to it.
 */
class WhoIsWatchingViewModel(
    private val serverId: String,
    private val servers: ServerRepository,
    private val accounts: AccountRepository,
    private val auth: AuthRepository,
    private val connection: ConnectionManager,
    private val pins: PinLock,
    private val clock: Clock,
    private val pinFor: ProfileRef? = null,
) : ViewModel() {
    private data class Local(
        val server: Server? = null,
        val loaded: Boolean = false,
        val public: PublicProfiles? = null,
        val quickConnectOff: Boolean = false,
        val sheet: SignInSheet? = null,
        val signingIn: Boolean = false,
        val openingUserId: String? = null,
        val sheetNotice: String? = null,
        val error: String? = null,
        val signedIn: Boolean = false,
        val openedUserId: String? = null,
    ) {
        val busy get() = signingIn || openingUserId != null || signedIn
    }

    private val local = MutableStateFlow(Local())
    private val pollNow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var quickConnectJob: Job? = null
    private var countdownJob: Job? = null
    private val pin = PinGate(pins, auth, viewModelScope)

    // The grid as last shown. Opening a profile makes it the most recently used, which would move it to the front under the zoom.
    private var shownProfiles: List<Profile> = emptyList()

    val uiState: StateFlow<WhoIsWatchingUiState> =
        combine(accounts.observeAccounts(serverId), local, pin.prompt, connection.state) { saved, local, pinPrompt, connected ->
            val merged = mergeProfiles(saved, local.public)
            // While a profile opens the tiles keep their places, so the zoom starts from the one that was tapped
            val profiles = if (local.busy) merged.inOrderOf(shownProfiles) else merged
            shownProfiles = profiles
            WhoIsWatchingUiState(
                loading = !local.loaded,
                serverName = local.server?.name,
                profiles = profiles,
                serverReachable = !local.loaded || local.public != null,
                triedAddresses = local.server?.addresses.orEmpty(),
                quickConnectAvailable = local.public?.quickConnectEnabled == true && !local.quickConnectOff,
                sheet = local.sheet,
                signingIn = local.signingIn,
                openingUserId = local.openingUserId,
                notice = expiredUserId(connected)?.let { id -> profiles.find { it.userId == id } }?.name?.let(Strings::sessionEndedFor),
                sheetNotice = local.sheetNotice,
                error = local.error,
                signedIn = local.signedIn,
                openedUserId = local.openedUserId,
                pinPrompt = pinPrompt,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhoIsWatchingUiState())

    init {
        pinFor?.let { askAtLaunch(it) }
        viewModelScope.launch {
            // Both are in place before the grid stops loading, so the header never appears late.
            val server = servers.get(serverId)
            local.update { it.copy(server = server) }
            load()
        }
    }

    /** Asks the server for its users again, e.g. after it couldn't be reached. */
    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val public = auth.loadPublicProfiles(serverId)
        local.update { it.copy(loaded = true, public = public) }
    }

    fun onProfileTapped(profile: Profile) {
        if (local.value.busy) return
        if (profile.pinRequiredToOpen) {
            pin.ask(PinReason.OpenProfile, serverId, profile.userId, profile.name) { open(profile) }
        } else {
            open(profile)
        }
    }

    /** A PIN was entered in the open PIN sheet. */
    fun onSubmitPin(pin: String) = this.pin.submitPin(pin)

    /** "Forgot PIN?": the PIN sheet asks for the profile's Jellyfin password instead. */
    fun onForgotPin() = pin.forgotPin()

    fun onDismissPin() = pin.dismiss()

    private fun askAtLaunch(ref: ProfileRef) {
        viewModelScope.launch {
            val account = accounts.account(ref.serverId, ref.userId) ?: return@launch
            // The picker does not know yet whether the server wants a password; asking for one is the safe guess.
            val profile = Profile(account.userId, account.name, account.hasSession, needsPassword = true)
            pin.ask(PinReason.SwitchProfile, ref.serverId, ref.userId, account.name, onDismiss = { goBackTo(profile) }) {}
        }
    }

    // Dismissing the launch PIN goes back to that profile, through its open-this-profile PIN when it has one.
    private fun goBackTo(profile: Profile) {
        viewModelScope.launch {
            if (pins.gates(serverId, profile.userId).openProfile) {
                pin.ask(PinReason.OpenProfile, serverId, profile.userId, profile.name) { open(profile) }
            } else {
                open(profile)
            }
        }
    }

    private fun open(profile: Profile) {
        if (local.value.busy) return
        viewModelScope.launch {
            if (!profile.hasSession) {
                val ended = profile.userId == expiredUserId(connection.state.value)
                return@launch withoutSession(profile, notice = SignInMessages.SESSION_ENDED.takeIf { ended })
            }
            local.update { it.copy(openingUserId = profile.userId, error = null) }
            when (connection.activate(serverId, profile.userId)) {
                ActivateResult.Activated -> local.update { it.copy(openingUserId = null, signedIn = true, openedUserId = profile.userId) }

                ActivateResult.Expired, ActivateResult.Unknown -> {
                    local.update { it.copy(openingUserId = null) }
                    withoutSession(profile, notice = SignInMessages.SESSION_ENDED)
                }

                ActivateResult.Unreachable ->
                    local.update { it.copy(openingUserId = null, error = SignInMessages.UNREACHABLE) }
            }
        }
    }

    fun onOtherUserTapped() {
        if (local.value.signedIn) return
        stopQuickConnect()
        local.update { it.copy(sheet = SignInSheet.OtherUser, error = null, sheetNotice = null) }
    }

    /** Signs in with [password] for the profile in the open password sheet, or checks it after "Forgot PIN?". */
    fun onSubmitPassword(password: String) {
        if (pin.prompt.value?.forgot == true) return pin.submitPassword(password)
        val sheet = local.value.sheet as? SignInSheet.Password ?: return
        signIn(sheet.profile.name, password, passwordOnly = true, userId = sheet.profile.userId)
    }

    fun onSubmitOtherUser(username: String, password: String) {
        if (username.isBlank()) return
        signIn(username.trim(), password, passwordOnly = false)
    }

    fun onQuickConnectTapped() {
        if (local.value.signedIn) return
        stopQuickConnect()
        local.update { it.copy(sheet = SignInSheet.QuickConnect(), error = null, sheetNotice = null) }
        quickConnectJob = viewModelScope.launch {
            auth.quickConnect(serverId, pollNow).collect(::onQuickConnect)
        }
    }

    fun onAppResumed() {
        pollNow.tryEmit(Unit)
    }

    fun onDismissSheet() {
        stopQuickConnect()
        local.update { it.copy(sheet = null, error = null, sheetNotice = null) }
    }

    private fun onQuickConnect(state: QuickConnectState) {
        when (state) {
            QuickConnectState.Starting -> Unit

            is QuickConnectState.Waiting -> {
                updateQuickConnect { it.copy(code = state.code, expiresIn = state.expiresAt - clock.now()) }
                countDown(state.expiresAt)
            }

            QuickConnectState.Approved -> {
                countdownJob?.cancel()
                updateQuickConnect { it.copy(approved = true) }
            }

            QuickConnectState.SignedIn -> local.update { it.copy(sheet = null, signedIn = true) }

            QuickConnectState.TimedOut -> stopQuickConnectWith(QuickConnectStop.Expired)

            QuickConnectState.Unavailable -> {
                stopQuickConnectWith(QuickConnectStop.Off)
                local.update { it.copy(quickConnectOff = true) }
            }

            QuickConnectState.Unreachable -> stopQuickConnectWith(QuickConnectStop.Unreachable)

            is QuickConnectState.Failed -> stopQuickConnectWith(QuickConnectStop.Failed)
        }
    }

    private fun countDown(expiresAt: Instant) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val left = (expiresAt - clock.now()).coerceAtLeast(Duration.ZERO)
                updateQuickConnect { it.copy(expiresIn = left) }
                if (left == Duration.ZERO) break
                delay(1.seconds)
            }
        }
    }

    private fun updateQuickConnect(change: (SignInSheet.QuickConnect) -> SignInSheet.QuickConnect) {
        local.update { current -> (current.sheet as? SignInSheet.QuickConnect)?.let { current.copy(sheet = change(it)) } ?: current }
    }

    private fun stopQuickConnectWith(stop: QuickConnectStop) {
        countdownJob?.cancel()
        updateQuickConnect { it.copy(code = it.code.takeIf { stop == QuickConnectStop.Expired }, expiresIn = null, approved = false, stop = stop) }
    }

    private fun stopQuickConnect() {
        quickConnectJob?.cancel()
        countdownJob?.cancel()
    }

    private fun expiredUserId(state: ConnectionState): String? = (state as? ConnectionState.Expired)?.takeIf { it.serverId == serverId }?.userId

    // A profile with no usable session: sign in without a password if it has none, else ask.
    private suspend fun withoutSession(profile: Profile, notice: String?) {
        if (!profile.needsPassword) {
            local.update { it.copy(openingUserId = profile.userId) }
            signInNow(profile.name, "", passwordOnly = true, userId = profile.userId)
            local.update { it.copy(openingUserId = null) }
        } else {
            local.update { it.copy(sheet = SignInSheet.Password(profile), sheetNotice = notice, error = null) }
        }
    }

    private fun signIn(username: String, password: String, passwordOnly: Boolean, userId: String? = null) {
        if (local.value.busy) return
        viewModelScope.launch { signInNow(username, password, passwordOnly, userId) }
    }

    private suspend fun signInNow(username: String, password: String, passwordOnly: Boolean, userId: String? = null) {
        local.update { it.copy(signingIn = true, error = null) }
        when (val result = auth.signIn(serverId, username, password)) {
            SignInResult.SignedIn -> local.update {
                it.copy(signingIn = false, sheet = null, sheetNotice = null, signedIn = true, openedUserId = userId)
            }

            else -> local.update {
                it.copy(signingIn = false, error = SignInMessages.forFailure(result, passwordOnly))
            }
        }
    }
}

internal fun List<Profile>.inOrderOf(shown: List<Profile>): List<Profile> {
    val place = shown.withIndex().associate { (index, profile) -> profile.userId to index }
    return sortedBy { place[it.userId] ?: Int.MAX_VALUE }
}
