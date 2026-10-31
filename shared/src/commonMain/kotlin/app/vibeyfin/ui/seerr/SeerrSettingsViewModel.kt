package app.vibeyfin.ui.seerr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.SeerrConnectResult
import app.vibeyfin.domain.SeerrConnection
import app.vibeyfin.domain.SeerrPasswordResult
import app.vibeyfin.domain.SeerrProblem
import app.vibeyfin.domain.SeerrRepository
import app.vibeyfin.domain.SeerrState
import app.vibeyfin.domain.model.RequestQuota
import app.vibeyfin.domain.normalizeSeerrAddress
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which version of the Seerr page shows. */
enum class SeerrSettingsLayout {
    /** Seerr isn't for this profile (a kids profile, or nobody signed in): nothing to draw. */
    Hidden,

    /** The address field and Connect: no Seerr yet, this profile turned it off, or the address is being changed. */
    Setup,

    /** The profile is being signed in by itself, as when Settings opens right after a start. */
    SigningIn,

    /** The server has a Seerr but this profile isn't signed in to it; [SeerrSettingsUiState.problem] says why. */
    NotSignedIn,

    Connected,
}

/** What the profile can do about not being signed in, beside changing the address or disconnecting. */
enum class SeerrFix {
    /** Quick Connect is off: sign in once with the Jellyfin password. */
    SignInWithPassword,

    /** Seerr couldn't be reached or sign-in failed: try again. */
    TryAgain,

    /** Nothing here helps (Seerr belongs to another server, or refuses this user). */
    None,
}

/** The password sheet, while it is open. The password itself stays in the native field until it is submitted. */
data class SeerrPasswordSheet(
    val userName: String,
    val submitting: Boolean = false,
    /** Why the last try failed, under the field. */
    val error: String? = null,
)

data class SeerrSettingsUiState(
    val layout: SeerrSettingsLayout = SeerrSettingsLayout.Hidden,
    /** What the address field holds; on a server that already has a Seerr it starts with that address. */
    val address: String = "",
    /** The server already has a Seerr address, so the help says changing it changes it for every profile. */
    val addressShared: Boolean = false,
    /** Connect was tapped and is checking the address and signing in: the button shows a spinner. */
    val connecting: Boolean = false,
    /** Why the address didn't work, shown under the field. */
    val addressError: String? = null,
    /** The Seerr's address as saved for the server. */
    val savedAddress: String = "",
    /** The Seerr's version, once read. */
    val version: String? = null,
    /** The profile's name on Seerr, when signed in. */
    val userName: String = "",
    val signedInWithPassword: Boolean = false,
    /** What is left of the request quota ("3 movies, 2 seasons"); null when there is no limit or it isn't known. */
    val quotaLeft: String? = null,
    /** How often the quota starts again ("Every week"), with [quotaLeft]. */
    val quotaPeriod: String? = null,
    /** On [SeerrSettingsLayout.NotSignedIn], why. */
    val problem: String? = null,
    val fix: SeerrFix = SeerrFix.None,
    val passwordSheet: SeerrPasswordSheet? = null,
    /** Disconnect was tapped and waits for the confirmation. */
    val confirmDisconnect: Boolean = false,
    /** Seerr went away for this profile while the page was open (kids mode turned on, signed out): the native screen goes back. */
    val leave: Boolean = false,
)

/**
 * Settings › Seerr: connecting the server's Seerr, this profile's sign-in, its quota, and turning Seerr off for the profile.
 * The state comes from [SeerrConnection]; this adds what the page itself holds (the field, the sheet, the confirmation).
 */
class SeerrSettingsViewModel(
    private val seerr: SeerrConnection,
    private val repository: SeerrRepository,
    private val connection: ConnectionManager,
    private val applicationScope: CoroutineScope,
) : ViewModel() {
    // What the page holds beside the connection's state.
    private data class Local(
        /** What was typed; null until the user types, so the field starts from the server's address. */
        val typed: String? = null,
        val connecting: Boolean = false,
        val addressError: String? = null,
        /** "Change address" was tapped on a profile that isn't signed in. */
        val editing: Boolean = false,
        val version: String? = null,
        val quota: RequestQuota? = null,
        val passwordSheet: SeerrPasswordSheet? = null,
        val confirmDisconnect: Boolean = false,
        val seenVisible: Boolean = false,
        val leave: Boolean = false,
    )

    private val local = MutableStateFlow(Local())
    private var detailsFor: SeerrState.Ready? = null
    private var detailsLoad: Job? = null

    val uiState: StateFlow<SeerrSettingsUiState> = combine(seerr.state, local) { state, page -> state(state, page) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, state(seerr.state.value, local.value))

    init {
        viewModelScope.launch {
            seerr.state.collect { state ->
                when {
                    state == SeerrState.Hidden -> local.update { if (it.seenVisible) it.copy(leave = true, passwordSheet = null, confirmDisconnect = false) else it }
                    else -> local.update { it.copy(seenVisible = true) }
                }
                if (state is SeerrState.Ready) {
                    local.update { it.copy(editing = false, passwordSheet = null) }
                    loadDetails(state)
                }
            }
        }
    }

    private fun state(state: SeerrState, page: Local): SeerrSettingsUiState {
        val base = SeerrSettingsUiState(
            address = page.typed.orEmpty(),
            connecting = page.connecting,
            addressError = page.addressError,
            passwordSheet = page.passwordSheet,
            confirmDisconnect = page.confirmDisconnect,
            leave = page.leave,
        )
        val saved = state.address()
        // Connecting from this page keeps the field on screen, with the button's spinner.
        if (showsField(state, page)) {
            return base.copy(
                layout = SeerrSettingsLayout.Setup,
                address = page.typed ?: saved.orEmpty(),
                addressShared = saved != null,
                savedAddress = saved.orEmpty(),
            )
        }
        return when (state) {
            SeerrState.Hidden -> base

            is SeerrState.Connecting -> base.copy(layout = SeerrSettingsLayout.SigningIn, savedAddress = state.address)

            is SeerrState.Ready -> {
                val quota = page.quota
                val movies = quota?.movies
                val shows = quota?.shows
                base.copy(
                    layout = SeerrSettingsLayout.Connected,
                    savedAddress = state.address,
                    version = page.version,
                    userName = state.user.name,
                    signedInWithPassword = state.signedInWithPassword,
                    quotaLeft = if (movies == null && shows == null) null else Strings.seerrQuotaLeft(movies?.remaining, shows?.remaining),
                    quotaPeriod = (movies?.days ?: shows?.days)?.let(Strings::seerrQuotaPeriod),
                )
            }

            is SeerrState.NeedsPassword -> base.copy(
                layout = SeerrSettingsLayout.NotSignedIn,
                savedAddress = state.address,
                problem = SeerrMessages.QUICK_CONNECT_OFF,
                fix = SeerrFix.SignInWithPassword,
            )

            is SeerrState.Unavailable -> base.copy(
                layout = SeerrSettingsLayout.NotSignedIn,
                savedAddress = state.address,
                problem = SeerrMessages.forProblem(state.problem),
                fix = if (state.problem == SeerrProblem.Unreachable || state.problem == SeerrProblem.Failed) SeerrFix.TryAgain else SeerrFix.None,
            )

            // Handled above, with the field.
            SeerrState.NotSet, is SeerrState.Off -> base
        }
    }

    // The address field shows with no Seerr, for a profile that turned it off, while changing the address, and while connecting from it.
    private fun showsField(state: SeerrState, page: Local): Boolean = page.connecting || page.editing || state == SeerrState.NotSet || state is SeerrState.Off

    fun onAddressChange(text: String) {
        local.update { it.copy(typed = text, addressError = null) }
    }

    /** Checks the address, saves it for the server and signs this profile in. */
    fun onConnect() {
        val current = local.value
        if (current.connecting) return
        // From what was typed, not from [uiState], which may not have caught up with the last keystroke yet.
        val address = current.typed ?: seerr.state.value.address().orEmpty()
        if (normalizeSeerrAddress(address) == null) {
            local.update { it.copy(addressError = SeerrMessages.INVALID_ADDRESS) }
            return
        }
        local.update { it.copy(connecting = true, addressError = null) }
        viewModelScope.launch {
            val result = seerr.connect(address)
            val done = result == SeerrConnectResult.Connected || result == SeerrConnectResult.NeedsPassword
            local.update {
                it.copy(
                    connecting = false,
                    addressError = SeerrMessages.forConnect(result),
                    editing = if (done) false else it.editing,
                    typed = if (done) null else it.typed,
                )
            }
        }
    }

    /** "Change address": the field comes back, filled with the saved address. */
    fun onChangeAddress() {
        local.update { it.copy(editing = true, typed = null, addressError = null) }
    }

    fun onRetry() {
        viewModelScope.launch { seerr.retry() }
    }

    fun onSignInWithPassword() {
        val name = (connection.state.value as? ConnectionState.Connected)?.account?.name ?: return
        local.update { it.copy(passwordSheet = SeerrPasswordSheet(name)) }
    }

    /** The password from the sheet. It goes to Seerr once and is never kept. */
    fun onSubmitPassword(password: String) {
        val sheet = local.value.passwordSheet ?: return
        if (sheet.submitting || password.isEmpty()) return
        local.update { it.copy(passwordSheet = sheet.copy(submitting = true, error = null)) }
        viewModelScope.launch {
            val result = seerr.signInWithPassword(password)
            local.update { page ->
                if (result == SeerrPasswordResult.SignedIn) {
                    page.copy(passwordSheet = null)
                } else {
                    page.copy(passwordSheet = page.passwordSheet?.copy(submitting = false, error = SeerrMessages.forPassword(result)))
                }
            }
        }
    }

    fun onDismissPassword() {
        local.update { it.copy(passwordSheet = null) }
    }

    fun onDisconnect() {
        local.update { it.copy(confirmDisconnect = true) }
    }

    fun onDismissDisconnect() {
        local.update { it.copy(confirmDisconnect = false) }
    }

    /** Turns Seerr off for this profile. It finishes even if the page is left at once. */
    fun onConfirmDisconnect() {
        local.update { it.copy(confirmDisconnect = false, typed = null, version = null, quota = null) }
        applicationScope.launch { seerr.disconnect() }
    }

    // The version and the quota, read once for each sign-in.
    private fun loadDetails(ready: SeerrState.Ready) {
        if (detailsFor?.api === ready.api) return
        detailsFor = ready
        detailsLoad?.cancel()
        local.update { it.copy(version = null, quota = null) }
        detailsLoad = viewModelScope.launch {
            val version = (repository.version() as? ApiResult.Success)?.value
            val quota = (repository.quota() as? ApiResult.Success)?.value
            local.update { it.copy(version = version, quota = quota) }
        }
    }
}

private fun SeerrState.address(): String? = when (this) {
    is SeerrState.Off -> address
    is SeerrState.Connecting -> address
    is SeerrState.Ready -> address
    is SeerrState.NeedsPassword -> address
    is SeerrState.Unavailable -> address
    SeerrState.Hidden, SeerrState.NotSet -> null
}
