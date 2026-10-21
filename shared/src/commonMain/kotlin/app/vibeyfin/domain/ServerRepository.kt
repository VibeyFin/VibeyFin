package app.vibeyfin.domain

import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.data.db.ServerAddressEntity
import app.vibeyfin.data.db.ServerDao
import app.vibeyfin.data.db.ServerEntity
import app.vibeyfin.data.vault.TokenVault
import app.vibeyfin.domain.model.Server
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Clock

class ServerRepository(
    private val serverDao: ServerDao,
    private val accountDao: AccountDao,
    private val vault: TokenVault,
    private val clock: Clock,
) {
    val servers: Flow<List<Server>> = combine(
        serverDao.observeServers(),
        serverDao.observeAddresses(),
    ) { servers, addresses ->
        val byServer = addresses.groupBy { it.serverId }
        servers.map { it.toServer(byServer[it.id].orEmpty()) }
    }

    suspend fun get(id: String): Server? {
        val server = serverDao.getServer(id) ?: return null
        return server.toServer(serverDao.getAddresses(id))
    }

    /**
     * Saves a server that was just reached at [address]. If the server is already saved (same
     * [id]) the address is added to it instead of creating a second entry. [otherAddresses] also
     * reached it and are kept as fallbacks behind [address].
     */
    suspend fun save(
        id: String,
        name: String,
        version: String?,
        address: String,
        otherAddresses: List<String> = emptyList(),
    ) {
        serverDao.save(id, name, version, address, clock.now().toEpochMilliseconds(), otherAddresses)
    }

    /** Remembers that [address] worked, so it is tried first next time. */
    suspend fun markAddressWorked(serverId: String, address: String) {
        serverDao.markAddressWorked(serverId, address, clock.now().toEpochMilliseconds())
    }

    /** Removes the server with its addresses and profiles, and releases their stored tokens. */
    suspend fun delete(id: String) {
        accountDao.getSealedTokens(id).forEach(vault::discard)
        serverDao.delete(id)
    }
}

private fun ServerEntity.toServer(addresses: List<ServerAddressEntity>) = Server(
    id = id,
    name = name,
    version = version,
    addresses = addresses.sortedWith(addressOrder).map { it.url },
)

/** Last address that worked first, then ones that never did in the order they were added. */
private val addressOrder = compareByDescending<ServerAddressEntity> { it.lastSuccessAt != null }
    .thenByDescending { it.lastSuccessAt }
    .thenBy { it.addedAt }
    .thenBy { it.url }
