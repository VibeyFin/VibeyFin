package app.vibeyfin.domain

import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.data.db.AccountEntity
import app.vibeyfin.data.vault.TokenVault
import app.vibeyfin.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

class AccountRepository(
    private val dao: AccountDao,
    private val vault: TokenVault,
    private val clock: Clock,
) {
    /** Profiles on [serverId], most recently used first, each knowing whether it asks for its PIN to open. */
    fun observeAccounts(serverId: String): Flow<List<Account>> = combine(dao.observeAccounts(serverId), dao.observePinToOpen(serverId)) { accounts, pinToOpen ->
        accounts.map { it.toAccount().copy(pinRequiredToOpen = it.userId in pinToOpen) }
    }

    suspend fun account(serverId: String, userId: String): Account? = dao.getAccount(serverId, userId)?.toAccount()

    /** How many profiles each server has on this device, by server ID. */
    fun observeProfileCounts(): Flow<Map<String, Int>> = dao.observeProfileCounts().map { rows -> rows.associate { it.serverId to it.count } }

    /** Stores the token from a successful sign-in, creating the profile or refreshing it. */
    suspend fun saveSession(serverId: String, userId: String, name: String, imageTag: String?, token: String) {
        val previous = dao.getAccount(serverId, userId)?.sealedToken
        val sealed = vault.seal(token)
        dao.saveSession(serverId, userId, name, imageTag, sealed, clock.now().toEpochMilliseconds())
        previous?.let(vault::discard)
    }

    /**
     * The saved token, or null when the profile has none. A token that can't be read (its key is
     * gone, or it was tampered with) is treated as expired: it is cleared and the profile locks.
     */
    suspend fun sessionToken(serverId: String, userId: String): String? {
        val sealed = dao.getAccount(serverId, userId)?.sealedToken ?: return null
        val token = vault.open(sealed)
        if (token == null) expire(serverId, userId)
        return token
    }

    /**
     * Drops the token but keeps the profile, which then shows as locked. Used for a 401 and for
     * an explicit sign-out; nothing else ends a session. The profile's Seerr session goes too: it
     * signs in to Seerr again once the profile is signed in again.
     */
    suspend fun expire(serverId: String, userId: String) {
        val account = dao.getAccount(serverId, userId) ?: return
        account.sealedSeerrSession?.let { seerr ->
            dao.clearSeerrSession(serverId, userId)
            vault.discard(seerr)
        }
        val sealed = account.sealedToken ?: return
        dao.clearToken(serverId, userId)
        vault.discard(sealed)
    }

    /** Moves the profile to the front of the "Who's watching?" order. */
    suspend fun markUsed(serverId: String, userId: String) {
        dao.markUsed(serverId, userId, clock.now().toEpochMilliseconds())
    }
}

private fun AccountEntity.toAccount() = Account(
    serverId = serverId,
    userId = userId,
    name = name,
    imageTag = imageTag,
    hasSession = sealedToken != null,
    lastUsedAt = Instant.fromEpochMilliseconds(lastUsedAt),
)
