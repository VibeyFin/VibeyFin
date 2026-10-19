package com.liamgleeson.vibeyfin.ui.whoswatching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liamgleeson.vibeyfin.domain.AccountRepository
import com.liamgleeson.vibeyfin.domain.ActivateResult
import com.liamgleeson.vibeyfin.domain.AuthRepository
import com.liamgleeson.vibeyfin.domain.ConnectionManager
import com.liamgleeson.vibeyfin.domain.PublicProfiles
import com.liamgleeson.vibeyfin.domain.ServerRepository
import com.liamgleeson.vibeyfin.domain.SignInResult
import com.liamgleeson.vibeyfin.domain.mergeProfiles
import com.liamgleeson.vibeyfin.domain.model.Profile
import com.liamgleeson.vibeyfin.domain.model.Server
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
}

data class WhoIsWatchingUiState(
    /** True until the server has answered or failed to, so the grid doesn't jump. */
    val loading: Boolean = true,
    val serverName: String? = null,
    val profiles: List<Profile> = emptyList(),
    /** False when the server couldn't be reached; saved profiles still show. */
    val serverReachable: Boolean = true,
    val sheet: SignInSheet? = null,
    val signingIn: Boolean = false,
    /** Shown inside the open sheet, or on the grid when no sheet is open. */
    val error: String? = null,
    /** Signed in; the screen moves on. */
    val signedIn: Boolean = false,
)

class WhoIsWatchingViewModel(
    private val serverId: String,
    private val servers: ServerRepository,
    accounts: AccountRepository,
    private val auth: AuthRepository,
    private val connection: ConnectionManager,
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

    val uiState: StateFlow<WhoIsWatchingUiState> =
        combine(accounts.observeAccounts(serverId), local) { saved, local ->
            WhoIsWatchingUiState(
                loading = !local.loaded,
                serverName = local.server?.name,
                profiles = mergeProfiles(saved, local.public, local.server?.addresses?.firstOrNull()),
                serverReachable = !local.loaded || local.public != null,
                sheet = local.sheet,
                signingIn = local.signingIn,
                error = local.error,
                signedIn = local.signedIn,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhoIsWatchingUiState())

    init {
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

    /** Signs in with [password] for the profile in the open password sheet. */
    fun onSubmitPassword(password: String) {
        val sheet = local.value.sheet as? SignInSheet.Password ?: return
        signIn(sheet.profile.name, password, passwordOnly = true)
    }

    fun onSubmitOtherUser(username: String, password: String) {
        if (username.isBlank()) return
        signIn(username.trim(), password, passwordOnly = false)
    }

    fun onDismissSheet() {
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
