package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.api.ApiError
import com.liamgleeson.vibeyfin.data.api.ApiResult
import com.liamgleeson.vibeyfin.data.api.AuthenticationResultDto
import com.liamgleeson.vibeyfin.data.api.JellyfinApi
import com.liamgleeson.vibeyfin.data.api.valueOrNull

sealed interface SignInResult {
    /** Signed in; the session is saved and is now the current connection. */
    data object SignedIn : SignInResult

    /** The server refused the username or password. Nothing was saved. */
    data object InvalidCredentials : SignInResult

    /** No saved address of the server answered. */
    data object ServerUnreachable : SignInResult

    data class Failed(val error: ApiError) : SignInResult
}

/** Signing in: who the server lists, and turning a password or an approved code into a saved session. */
class AuthRepository(
    private val connector: ServerConnector,
    private val accounts: AccountRepository,
    private val connection: ConnectionManager,
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
    suspend fun signIn(serverId: String, username: String, password: String): SignInResult =
        authenticate(serverId, fallbackName = username) { it.authenticateByName(username, password) }

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
