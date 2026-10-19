package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.api.ApiError
import com.liamgleeson.vibeyfin.data.api.ApiResult
import com.liamgleeson.vibeyfin.data.api.JellyfinApi
import com.liamgleeson.vibeyfin.data.api.UnauthorizedEvents
import com.liamgleeson.vibeyfin.domain.model.Account
import com.liamgleeson.vibeyfin.domain.model.ProfileRef
import com.liamgleeson.vibeyfin.domain.model.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConnectionState {
    /** Nobody is signed in on this run: before sign-in, after switching profile, after sign-out. */
    data object Disconnected : ConnectionState

    /** Signed in. Features read [api] from here; nothing else holds a token. */
    data class Connected(val server: Server, val account: Account, val api: JellyfinApi) : ConnectionState

    /** The server rejected the profile's token. The profile is locked and needs a password again. */
    data class Expired(val serverId: String, val userId: String) : ConnectionState
}

enum class ActivateResult {
    Activated,

    /** There was no token, or the server rejected it (HTTP 401); the profile is now locked. */
    Expired,

    /** No saved address answered, or the server errored. The token is kept. */
    Unreachable,

    /** The server or the profile isn't saved. */
    Unknown,
}

/**
 * Owns the current server, profile and authenticated API as a [StateFlow]. A token that any call
 * sees rejected (HTTP 401) ends up here too: it locks the profile and moves the state to
 * [ConnectionState.Expired], and the screens route to "Who's watching?".
 */
class ConnectionManager(
    private val connector: ServerConnector,
    private val servers: ServerRepository,
    private val accounts: AccountRepository,
    private val preferences: DevicePreferences,
    unauthorized: UnauthorizedEvents,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private class Session(val serverId: String, val userId: String, val token: String)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var session: Session? = null

    init {
        scope.launch { unauthorized.tokens.collect { onTokenRejected(it) } }
    }

    /**
     * Signs in as a saved profile using its saved token, after checking with the server that the
     * token still works. Never asks for a password.
     */
    suspend fun activate(serverId: String, userId: String): ActivateResult {
        val server = servers.get(serverId) ?: return ActivateResult.Unknown
        val account = accounts.account(serverId, userId) ?: return ActivateResult.Unknown
        val token = accounts.sessionToken(serverId, userId)
        if (token == null) {
            markExpired(serverId, userId)
            return ActivateResult.Expired
        }
        val reached = connector.firstAnswer(serverId, token) { it.me() } ?: return ActivateResult.Unreachable
        return when (val result = reached.result) {
            is ApiResult.Success -> {
                connect(server, account, token, reached.api)
                ActivateResult.Activated
            }
            is ApiResult.Failure -> if (result.error == ApiError.Unauthorized) {
                accounts.expire(serverId, userId)
                markExpired(serverId, userId)
                ActivateResult.Expired
            } else {
                ActivateResult.Unreachable
            }
        }
    }

    /** Takes over a session that was just created by signing in, so there is no need to check it again. */
    suspend fun adopt(serverId: String, userId: String, token: String, api: JellyfinApi): Boolean {
        val server = servers.get(serverId) ?: return false
        val account = accounts.account(serverId, userId) ?: return false
        connect(server, account, token, api.withToken(token))
        return true
    }

    /** Leaves the current profile signed in on the server and returns to "Who's watching?". */
    fun switchProfile() {
        session = null
        _state.value = ConnectionState.Disconnected
    }

    /** Revokes the token on the server (best effort), forgets it here, and locks the profile. */
    suspend fun signOut() {
        val current = (_state.value as? ConnectionState.Connected) ?: return
        session = null
        _state.value = ConnectionState.Disconnected
        current.api.logout()
        accounts.expire(current.account.serverId, current.account.userId)
    }

    private suspend fun connect(server: Server, account: Account, token: String, api: JellyfinApi) {
        session = Session(server.id, account.userId, token)
        accounts.markUsed(server.id, account.userId)
        preferences.setLastProfile(ProfileRef(server.id, account.userId))
        _state.value = ConnectionState.Connected(server, account, api)
    }

    private suspend fun onTokenRejected(token: String) {
        val current = session?.takeIf { it.token == token } ?: return
        session = null
        accounts.expire(current.serverId, current.userId)
        markExpired(current.serverId, current.userId)
    }

    private fun markExpired(serverId: String, userId: String) {
        _state.value = ConnectionState.Expired(serverId, userId)
    }
}
