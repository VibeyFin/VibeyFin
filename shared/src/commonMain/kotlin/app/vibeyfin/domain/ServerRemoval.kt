package app.vibeyfin.domain

import kotlinx.coroutines.flow.first

/** Removes a saved server from this device: its profiles are signed out here and their downloads and searches go. The server itself is untouched. */
class ServerRemoval(
    private val servers: ServerRepository,
    private val accounts: AccountRepository,
    private val searchHistory: SearchHistory,
    private val preferences: DevicePreferences,
    private val connection: ConnectionManager,
) {
    suspend fun remove(serverId: String) {
        accounts.observeAccounts(serverId).first().forEach { searchHistory.clear(serverId, it.userId) }
        if (connection.asConnected(false) { it.server.id == serverId }) connection.switchProfile()
        servers.delete(serverId)
        if (preferences.lastServerId.first() == serverId) preferences.setLastServer(null)
    }
}
