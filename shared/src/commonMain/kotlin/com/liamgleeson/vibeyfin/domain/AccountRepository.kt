package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.db.AccountDao
import com.liamgleeson.vibeyfin.data.db.AccountEntity
import com.liamgleeson.vibeyfin.data.vault.TokenVault
import com.liamgleeson.vibeyfin.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

class AccountRepository(
    private val dao: AccountDao,
    private val vault: TokenVault,
    private val clock: Clock,
) {
    /** Profiles on [serverId], most recently used first. */
    fun observeAccounts(serverId: String): Flow<List<Account>> =
        dao.observeAccounts(serverId).map { accounts -> accounts.map { it.toAccount() } }

    suspend fun account(serverId: String, userId: String): Account? = dao.getAccount(serverId, userId)?.toAccount()

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
     * an explicit sign-out; nothing else ends a session.
     */
    suspend fun expire(serverId: String, userId: String) {
        val sealed = dao.getAccount(serverId, userId)?.sealedToken ?: return
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
