package app.vibeyfin.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AccountDao {
    /** Most recently used first, so the profile you last watched as is the first tile. */
    @Query("SELECT * FROM accounts WHERE serverId = :serverId ORDER BY lastUsedAt DESC, name")
    abstract fun observeAccounts(serverId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun getAccount(serverId: String, userId: String): AccountEntity?

    @Query("SELECT sealedToken FROM accounts WHERE serverId = :serverId AND sealedToken IS NOT NULL")
    abstract suspend fun getSealedTokens(serverId: String): List<ByteArray>

    @Query("UPDATE accounts SET sealedToken = NULL WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun clearToken(serverId: String, userId: String)

    @Query("UPDATE accounts SET lastUsedAt = :at WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun markUsed(serverId: String, userId: String, at: Long)

    @Query("SELECT showPlaybackStats FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeShowPlaybackStats(serverId: String, userId: String): Flow<Boolean?>

    @Query("UPDATE account_settings SET showPlaybackStats = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setShowPlaybackStats(serverId: String, userId: String, value: Boolean)

    @Query("SELECT bingeThreshold FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeBingeThreshold(serverId: String, userId: String): Flow<Int?>

    @Query("UPDATE account_settings SET bingeThreshold = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setBingeThreshold(serverId: String, userId: String, value: Int)

    @Query("SELECT homeGenreRows FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeHomeGenreRows(serverId: String, userId: String): Flow<Int?>

    @Query("UPDATE account_settings SET homeGenreRows = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setHomeGenreRows(serverId: String, userId: String, value: Int)

    @Query("SELECT showHomeHero FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeShowHomeHero(serverId: String, userId: String): Flow<Boolean?>

    @Query("UPDATE account_settings SET showHomeHero = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setShowHomeHero(serverId: String, userId: String, value: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertAccount(account: AccountEntity): Long

    @Query(
        "UPDATE accounts SET name = :name, imageTag = :imageTag, sealedToken = :sealedToken, " +
            "lastUsedAt = :now WHERE serverId = :serverId AND userId = :userId",
    )
    protected abstract suspend fun updateAccount(
        serverId: String,
        userId: String,
        name: String,
        imageTag: String?,
        sealedToken: ByteArray,
        now: Long,
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSettings(settings: AccountSettingsEntity): Long

    /** Creates the profile if it is new, otherwise refreshes it, and stores the sealed token. */
    @Transaction
    open suspend fun saveSession(
        serverId: String,
        userId: String,
        name: String,
        imageTag: String?,
        sealedToken: ByteArray,
        now: Long,
    ) {
        insertAccount(AccountEntity(serverId, userId, name, imageTag, sealedToken, now))
        updateAccount(serverId, userId, name, imageTag, sealedToken, now)
        insertSettings(AccountSettingsEntity(serverId, userId))
    }
}
