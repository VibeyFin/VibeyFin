package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.JellyfinApi
import app.vibeyfin.data.api.JellyfinApiFactory

/** The answer to a call, with the API for the address that gave it. */
class Reached<T>(val api: JellyfinApi, val result: ApiResult<T>)

/**
 * Reaches a saved server through its addresses. One server can be known by a LAN IP and a domain,
 * so a call is tried on each address, the one that last worked first, until one answers.
 */
class ServerConnector(
    private val servers: ServerRepository,
    private val apis: JellyfinApiFactory,
) {
    /**
     * Runs [call] against the server's addresses in order. An address that answers, even with an
     * error such as 401, is the end of the search and is remembered as the one that worked. Returns
     * null when the server isn't saved or no address answered.
     */
    suspend fun <T> firstAnswer(
        serverId: String,
        token: String? = null,
        call: suspend (JellyfinApi) -> ApiResult<T>,
    ): Reached<T>? {
        val server = servers.get(serverId) ?: return null
        for (address in server.addresses) {
            val api = apis.create(address, token)
            val result = call(api)
            if (result is ApiResult.Failure && result.error == ApiError.Network) continue
            servers.markAddressWorked(serverId, address)
            return Reached(api, result)
        }
        return null
    }

    /** An API on the server's best address without asking it anything, or null if it isn't saved. */
    suspend fun apiFor(serverId: String, token: String?): JellyfinApi? {
        val address = servers.get(serverId)?.addresses?.firstOrNull() ?: return null
        return apis.create(address, token)
    }
}
