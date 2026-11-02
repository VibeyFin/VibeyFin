package app.vibeyfin.domain

import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.data.db.ServerDao
import app.vibeyfin.data.vault.TokenVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A profile's saved Seerr session: the cookie's value, the profile's user id on Seerr, and whether the password opened it. */
class StoredSeerrSession(val session: String, val seerrUserId: Int, val viaPassword: Boolean = false)

/**
 * Where Seerr's address and each profile's Seerr session are kept: the address on the server's row (one Seerr per server),
 * the session sealed in the [TokenVault] on the profile's row, like its Jellyfin token. Only [SeerrConnection] uses it.
 */
class SeerrStore(
    private val servers: ServerDao,
    private val accounts: AccountDao,
    private val vault: TokenVault,
) {
    suspend fun address(serverId: String): String? = servers.getSeerrUrl(serverId)

    /**
     * Sets the server's Seerr address. A different address is a different Seerr, so every profile's session on the
     * server goes with the old one; each signs in again by itself.
     */
    suspend fun setAddress(serverId: String, address: String) {
        if (servers.getSeerrUrl(serverId) == address) return
        accounts.getSealedSeerrSessions(serverId).forEach(vault::discard)
        accounts.clearSeerrSessions(serverId)
        servers.setSeerrUrl(serverId, address)
    }

    /** The saved session, or null. One that can't be read (its key is gone) is cleared, as a Jellyfin token is. */
    suspend fun session(serverId: String, userId: String): StoredSeerrSession? {
        val row = accounts.getSeerrProfile(serverId, userId) ?: return null
        val sealed = row.sealedSeerrSession ?: return null
        val userIdOnSeerr = row.seerrUserId
        val session = vault.open(sealed)
        if (session == null || userIdOnSeerr == null) {
            clearSession(serverId, userId)
            return null
        }
        return StoredSeerrSession(session, userIdOnSeerr, row.seerrPasswordSignIn)
    }

    suspend fun saveSession(serverId: String, userId: String, session: String, seerrUserId: Int, viaPassword: Boolean = false) {
        val previous = accounts.getSeerrProfile(serverId, userId)?.sealedSeerrSession
        accounts.setSeerrSession(serverId, userId, vault.seal(session), seerrUserId, viaPassword)
        previous?.let(vault::discard)
    }

    suspend fun clearSession(serverId: String, userId: String) {
        val sealed = accounts.getSeerrProfile(serverId, userId)?.sealedSeerrSession ?: return
        accounts.clearSeerrSession(serverId, userId)
        vault.discard(sealed)
    }

    /** Whether the profile turned Seerr off. */
    suspend fun isOff(serverId: String, userId: String): Boolean = accounts.getSeerrProfile(serverId, userId)?.seerrOff == true

    suspend fun setOff(serverId: String, userId: String, off: Boolean) = accounts.setSeerrOff(serverId, userId, off)

    fun observeKidsProfile(serverId: String, userId: String): Flow<Boolean> = accounts.observeKidsProfile(serverId, userId).map { it == true }
}
