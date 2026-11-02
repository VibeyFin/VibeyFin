package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.JellyfinApi
import app.vibeyfin.data.api.JellyfinApiFactory
import app.vibeyfin.data.api.valueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

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
    private val elsewhere = MutableStateFlow(emptySet<Pair<String, String>>())

    /**
     * Runs [call] against the server's addresses in order. An address that answers, even with an
     * error such as 401, is the end of the search and is remembered as the one that worked, unless
     * its 401 comes from a different server now at that address: that address is skipped for the run.
     * Returns null when the server isn't saved or no address answered.
     */
    suspend fun <T> firstAnswer(
        serverId: String,
        token: String? = null,
        call: suspend (JellyfinApi) -> ApiResult<T>,
    ): Reached<T>? {
        for (address in addresses(serverId)) {
            val api = apis.create(address, token)
            val result = call(api)
            if (isThisServersAnswer(serverId, address, result)) {
                servers.markAddressWorked(serverId, address)
                return Reached(api, result)
            }
        }
        return null
    }

    /** An API on the server's best address without asking it anything, or null if it isn't saved. */
    suspend fun apiFor(serverId: String, token: String?): JellyfinApi? {
        val address = addresses(serverId).firstOrNull() ?: return null
        return apis.create(address, token)
    }

    /** Whether [address] now belongs to a server other than [serverId]. An answer that can't tell counts as no. */
    suspend fun answersAsAnotherServer(serverId: String, address: String): Boolean {
        val id = apis.create(address).publicSystemInfo().valueOrNull()?.id ?: return false
        val other = id != serverId
        if (other) elsewhere.update { it + (serverId to address) }
        return other
    }

    private suspend fun isThisServersAnswer(serverId: String, address: String, result: ApiResult<*>): Boolean = when {
        result !is ApiResult.Failure -> true
        result.error == ApiError.Network -> false
        result.error == ApiError.Unauthorized -> !answersAsAnotherServer(serverId, address)
        else -> true
    }

    private suspend fun addresses(serverId: String): List<String> {
        val skipped = elsewhere.value
        return servers.get(serverId)?.addresses.orEmpty().filterNot { (serverId to it) in skipped }
    }
}
