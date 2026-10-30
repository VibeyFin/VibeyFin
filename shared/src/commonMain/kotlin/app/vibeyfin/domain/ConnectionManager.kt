package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.JellyfinApi
import app.vibeyfin.data.api.UnauthorizedEvents
import app.vibeyfin.domain.model.Account
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.domain.model.Server
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

/** The answer to [ConnectionManager.asProfile]. */
sealed interface ProfileCall<out T> {
    /** A saved address answered; [result] may still be a failure such as a 401. */
    data class Answered<T>(val result: ApiResult<T>) : ProfileCall<T>

    /** The profile has no usable token: it is locked, or its token can't be read. */
    data object NoSession : ProfileCall<Nothing>

    /** The server isn't saved, or none of its addresses answered. */
    data object Unreachable : ProfileCall<Nothing>
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
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
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
            expireSession(serverId, userId)
            return ActivateResult.Expired
        }
        val reached = connector.firstAnswer(serverId, token) { it.me() } ?: return ActivateResult.Unreachable
        return when (val result = reached.result) {
            is ApiResult.Success -> {
                connect(server, account, token, reached.api)
                ActivateResult.Activated
            }

            is ApiResult.Failure -> if (result.error == ApiError.Unauthorized) {
                expireSession(serverId, userId)
                ActivateResult.Expired
            } else {
                ActivateResult.Unreachable
            }
        }
    }

    /**
     * Signs in as a saved profile on a cold start without waiting for the network: the saved token is
     * trusted and the server is asked in the background. If it rejects the token, the profile is
     * locked and the state moves to [ConnectionState.Expired], like any other rejected call. If a
     * different saved address is the one that answers, calls move to it.
     */
    suspend fun resume(serverId: String, userId: String): ActivateResult {
        val server = servers.get(serverId) ?: return ActivateResult.Unknown
        val account = accounts.account(serverId, userId) ?: return ActivateResult.Unknown
        val token = accounts.sessionToken(serverId, userId)
        if (token == null) {
            expireSession(serverId, userId)
            return ActivateResult.Expired
        }
        val api = connector.apiFor(serverId, token) ?: return ActivateResult.Unknown
        connect(server, account, token, api)
        scope.launch { verify(serverId, token) }
        return ActivateResult.Activated
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

    /**
     * Runs [call] as a saved profile that need not be the signed-in one, for work that belongs to
     * the profile that began it (telling the server a play session stopped after the user switched
     * profile, or after a restart). Tries the server's addresses in order. The current state is not
     * touched; but a token the server rejects (HTTP 401) is locked here like anywhere else, whether
     * or not it is the current profile's.
     */
    suspend fun <T> asProfile(serverId: String, userId: String, call: suspend (JellyfinApi) -> ApiResult<T>): ProfileCall<T> {
        val token = accounts.sessionToken(serverId, userId) ?: return ProfileCall.NoSession
        val reached = connector.firstAnswer(serverId, token, call) ?: return ProfileCall.Unreachable
        val result = reached.result
        // The current profile's rejection reaches onTokenRejected through the call's own API; another
        // profile's is ignored there, so it is locked here, and signed-in state is left alone.
        val isCurrent = session?.let { it.serverId == serverId && it.userId == userId } == true
        if (!isCurrent && result is ApiResult.Failure && result.error == ApiError.Unauthorized) {
            accounts.expire(serverId, userId)
        }
        return ProfileCall.Answered(result)
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
        expireSession(current.serverId, current.userId)
    }

    // Asks the server about the token. A rejection is handled by [onTokenRejected] like any other.
    private suspend fun verify(serverId: String, token: String) {
        val reached = connector.firstAnswer(serverId, token) { it.me() } ?: return
        if (reached.result !is ApiResult.Success) return
        val current = _state.value as? ConnectionState.Connected ?: return
        if (session?.token == token && current.api.baseUrl != reached.api.baseUrl) {
            _state.value = current.copy(api = reached.api)
        }
    }

    // The one place a rejected token ends a session: drops the token, locks the profile and moves
    // the state to [ConnectionState.Expired] so the app routes to "Who's watching?". Explicit
    // sign-out is the only other way a token is cleared, and it goes through [signOut]; [asProfile]
    // locks a rejected token of a profile that isn't signed in, which has no state to move.
    private suspend fun expireSession(serverId: String, userId: String) {
        accounts.expire(serverId, userId)
        _state.value = ConnectionState.Expired(serverId, userId)
    }
}
