package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.AuthenticationResultDto
import app.vibeyfin.data.api.JellyfinApi
import app.vibeyfin.data.api.valueOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed interface SignInResult {
    /** Signed in; the session is saved and is now the current connection. */
    data object SignedIn : SignInResult

    /** The server refused the username or password. Nothing was saved. */
    data object InvalidCredentials : SignInResult

    /** No saved address of the server answered. */
    data object ServerUnreachable : SignInResult

    data class Failed(val error: ApiError) : SignInResult
}

/** Where a Quick Connect sign-in is: asking for a code, waiting for another device to approve it, or done. */
sealed interface QuickConnectState {
    data object Starting : QuickConnectState

    /** Show [code]; someone signed in on another device enters it to approve this sign-in. */
    data class Waiting(val code: String) : QuickConnectState

    data object SignedIn : QuickConnectState

    /** The code wasn't approved in time, or the server dropped it. */
    data object TimedOut : QuickConnectState

    /** The server has Quick Connect turned off. */
    data object Unavailable : QuickConnectState

    data object Unreachable : QuickConnectState

    data class Failed(val error: ApiError) : QuickConnectState
}

/** Signing in: who the server lists, and turning a password or an approved code into a saved session. */
class AuthRepository(
    private val connector: ServerConnector,
    private val accounts: AccountRepository,
    private val connection: ConnectionManager,
    private val pollInterval: Duration = 3.seconds,
    private val quickConnectTimeout: Duration = 5.minutes,
) {
    /** The server's public users and whether Quick Connect is on, or null if it can't be reached. */
    suspend fun loadPublicProfiles(serverId: String): PublicProfiles? {
        val reached = connector.firstAnswer(serverId) { api ->
            when (val users = api.publicUsers()) {
                is ApiResult.Success -> ApiResult.Success(
                    PublicProfiles(
                        users = users.value.map {
                            PublicUser(it.id, it.name.orEmpty(), it.primaryImageTag, it.hasPassword ?: true)
                        },
                        baseUrl = api.baseUrl,
                        quickConnectEnabled = api.quickConnectEnabled().valueOrNull() == true,
                    ),
                )

                is ApiResult.Failure -> users
            }
        } ?: return null
        return reached.result.valueOrNull()
    }

    /** Signs in with a username and password. An empty [password] is valid for a user without one. */
    suspend fun signIn(serverId: String, username: String, password: String): SignInResult = authenticate(serverId, fallbackName = username) { it.authenticateByName(username, password) }

    /**
     * Quick Connect: asks the server for a code, then polls every few seconds until another device
     * approves it, and signs in. Stops with [QuickConnectState.TimedOut] after the timeout. Cancel
     * the collector to give up.
     */
    fun quickConnect(serverId: String): Flow<QuickConnectState> = flow {
        emit(QuickConnectState.Starting)
        val started = connector.firstAnswer(serverId) { it.quickConnectInitiate() }
        if (started == null) {
            emit(QuickConnectState.Unreachable)
            return@flow
        }
        val code = when (val result = started.result) {
            is ApiResult.Success -> result.value

            is ApiResult.Failure -> {
                // Servers with Quick Connect off answer the request with 401.
                emit(if (result.error == ApiError.Unauthorized) QuickConnectState.Unavailable else QuickConnectState.Failed(result.error))
                return@flow
            }
        }
        emit(QuickConnectState.Waiting(code.code))

        val api = started.api
        var waited = Duration.ZERO
        while (waited < quickConnectTimeout) {
            delay(pollInterval)
            waited += pollInterval
            when (val status = api.quickConnectStatus(code.secret)) {
                is ApiResult.Success -> if (status.value.authenticated) {
                    emit(signInWithQuickConnect(serverId, code.secret))
                    return@flow
                }

                is ApiResult.Failure -> when (status.error) {
                    // A dropped connection is worth another try; anything else ends the attempt.
                    ApiError.Network -> Unit

                    ApiError.NotFound, ApiError.Unauthorized -> {
                        emit(QuickConnectState.TimedOut)
                        return@flow
                    }

                    else -> {
                        emit(QuickConnectState.Failed(status.error))
                        return@flow
                    }
                }
            }
        }
        emit(QuickConnectState.TimedOut)
    }

    private suspend fun signInWithQuickConnect(serverId: String, secret: String): QuickConnectState = when (val result = authenticate(serverId, fallbackName = "") { it.authenticateWithQuickConnect(secret) }) {
        SignInResult.SignedIn -> QuickConnectState.SignedIn
        SignInResult.ServerUnreachable -> QuickConnectState.Unreachable
        SignInResult.InvalidCredentials -> QuickConnectState.TimedOut
        is SignInResult.Failed -> QuickConnectState.Failed(result.error)
    }

    /** Saves the session from a successful [result] and makes it the current connection. */
    internal suspend fun authenticate(
        serverId: String,
        fallbackName: String,
        request: suspend (JellyfinApi) -> ApiResult<AuthenticationResultDto>,
    ): SignInResult {
        val reached = connector.firstAnswer(serverId, call = request) ?: return SignInResult.ServerUnreachable
        val result = reached.result
        if (result is ApiResult.Failure) {
            return if (result.error == ApiError.Unauthorized) SignInResult.InvalidCredentials else SignInResult.Failed(result.error)
        }
        val auth = (result as ApiResult.Success).value
        val token = auth.accessToken ?: return SignInResult.Failed(ApiError.BadResponse)
        accounts.saveSession(serverId, auth.user.id, auth.user.name ?: fallbackName, auth.user.primaryImageTag, token)
        connection.adopt(serverId, auth.user.id, token, reached.api)
        return SignInResult.SignedIn
    }
}
