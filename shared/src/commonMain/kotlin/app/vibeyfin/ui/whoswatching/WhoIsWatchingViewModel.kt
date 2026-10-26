package app.vibeyfin.ui.whoswatching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.domain.AccountRepository
import app.vibeyfin.domain.ActivateResult
import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.ConnectionManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The sheet open over the profile grid, if any. */
sealed interface SignInSheet {
    /** Asks for [profile]'s password. */
    data class Password(val profile: Profile) : SignInSheet

    /** Asks for a username and password, for a user the server doesn't list. */
    data object OtherUser : SignInSheet

    /**
     * Quick Connect. Shows [code] while waiting for another device to approve it; [message] says
     * why it stopped (expired, not allowed, no connection). Neither is set while the code is requested.
     */
    data class QuickConnect(val code: String?, val message: String?) : SignInSheet
}

data class WhoIsWatchingUiState(
    /** True until the server has answered or failed to, so the grid doesn't jump. */
    val loading: Boolean = true,
    val serverName: String? = null,
    val profiles: List<Profile> = emptyList(),
    /** False when the server couldn't be reached; saved profiles still show. */
    val serverReachable: Boolean = true,
    /** The server allows Quick Connect, so the grid offers it. */
    val quickConnectAvailable: Boolean = false,
    val sheet: SignInSheet? = null,
    val signingIn: Boolean = false,
    /** Shown inside the open sheet, or on the grid when no sheet is open. */
    val error: String? = null,
    /** Signed in; the screen moves on. */
    val signedIn: Boolean = false,
    /** The PIN sheet, over the grid and any sign-in sheet, while one is open. */
    val pinPrompt: PinPrompt? = null,
)

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
    private val pinFor: ProfileRef? = null,
) : ViewModel() {
    private data class Local(
        val server: Server? = null,
        val loaded: Boolean = false,
        val public: PublicProfiles? = null,
        val sheet: SignInSheet? = null,
        val signingIn: Boolean = false,
        val error: String? = null,
        val signedIn: Boolean = false,
    )

    private val local = MutableStateFlow(Local())
    private var quickConnectJob: Job? = null
    private val pin = PinGate(pins, auth, viewModelScope)

    val uiState: StateFlow<WhoIsWatchingUiState> =
        combine(accounts.observeAccounts(serverId), local, pin.prompt) { saved, local, pinPrompt ->
            WhoIsWatchingUiState(
                loading = !local.loaded,
                serverName = local.server?.name,
                profiles = mergeProfiles(saved, local.public, local.server?.addresses?.firstOrNull()),
                serverReachable = !local.loaded || local.public != null,
                quickConnectAvailable = local.public?.quickConnectEnabled == true,
                sheet = local.sheet,
                signingIn = local.signingIn,
                error = local.error,
                signedIn = local.signedIn,
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
        if (local.value.signingIn) return
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
            val profile = Profile(account.userId, account.name, null, account.hasSession, needsPassword = true)
            pin.ask(PinReason.SwitchProfile, ref.serverId, ref.userId, account.name, onDismiss = { goBackTo(profile) }) {}
        }
    }

    /** Dismissing the launch PIN goes back to that profile, through its open-this-profile PIN when it has one. */
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
        if (local.value.signingIn) return
        viewModelScope.launch {
            if (!profile.hasSession) return@launch withoutSession(profile, notice = null)
            local.update { it.copy(signingIn = true, error = null) }
            when (connection.activate(serverId, profile.userId)) {
                ActivateResult.Activated -> local.update { it.copy(signingIn = false, signedIn = true) }
                ActivateResult.Expired, ActivateResult.Unknown -> {
                    local.update { it.copy(signingIn = false) }
                    withoutSession(profile, notice = SignInMessages.SESSION_ENDED)
                }
                ActivateResult.Unreachable ->
                    local.update { it.copy(signingIn = false, error = SignInMessages.UNREACHABLE) }
            }
        }
    }

    fun onOtherUserTapped() {
        local.update { it.copy(sheet = SignInSheet.OtherUser, error = null) }
    }

    /** Signs in with [password] for the profile in the open password sheet, or checks it after "Forgot PIN?". */
    fun onSubmitPassword(password: String) {
        if (pin.prompt.value?.forgot == true) return pin.submitPassword(password)
        val sheet = local.value.sheet as? SignInSheet.Password ?: return
        signIn(sheet.profile.name, password, passwordOnly = true)
    }

    fun onSubmitOtherUser(username: String, password: String) {
        if (username.isBlank()) return
        signIn(username.trim(), password, passwordOnly = false)
    }

    /** Starts Quick Connect, or starts it over after it stopped. */
    fun onQuickConnectTapped() {
        quickConnectJob?.cancel()
        local.update { it.copy(sheet = SignInSheet.QuickConnect(code = null, message = null), error = null) }
        quickConnectJob = viewModelScope.launch {
            auth.quickConnect(serverId).collect { state ->
                local.update {
                    when (state) {
                        QuickConnectState.Starting -> it
                        is QuickConnectState.Waiting -> it.copy(sheet = SignInSheet.QuickConnect(state.code, null))
                        QuickConnectState.SignedIn -> it.copy(sheet = null, signedIn = true)
                        else -> it.copy(sheet = SignInSheet.QuickConnect(null, SignInMessages.forQuickConnect(state)))
                    }
                }
            }
        }
    }

    fun onDismissSheet() {
        quickConnectJob?.cancel()
        local.update { it.copy(sheet = null, error = null) }
    }

    /** A profile with no usable session: sign in without a password if it has none, else ask. */
    private suspend fun withoutSession(profile: Profile, notice: String?) {
        if (!profile.needsPassword) {
            signInNow(profile.name, "", passwordOnly = true)
        } else {
            local.update { it.copy(sheet = SignInSheet.Password(profile), error = notice) }
        }
    }

    private fun signIn(username: String, password: String, passwordOnly: Boolean) {
        if (local.value.signingIn) return
        viewModelScope.launch { signInNow(username, password, passwordOnly) }
    }

    private suspend fun signInNow(username: String, password: String, passwordOnly: Boolean) {
        local.update { it.copy(signingIn = true, error = null) }
        when (val result = auth.signIn(serverId, username, password)) {
            SignInResult.SignedIn -> local.update { it.copy(signingIn = false, sheet = null, signedIn = true) }
            else -> local.update {
                it.copy(signingIn = false, error = SignInMessages.forFailure(result, passwordOnly))
            }
        }
    }
}
