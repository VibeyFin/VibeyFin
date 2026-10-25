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

    @Query("SELECT skipBackSeconds FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeSkipBackSeconds(serverId: String, userId: String): Flow<Int?>

    @Query("UPDATE account_settings SET skipBackSeconds = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setSkipBackSeconds(serverId: String, userId: String, value: Int)

    @Query("SELECT skipForwardSeconds FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeSkipForwardSeconds(serverId: String, userId: String): Flow<Int?>

    @Query("UPDATE account_settings SET skipForwardSeconds = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setSkipForwardSeconds(serverId: String, userId: String, value: Int)

    @Query("SELECT autoSkipSegments FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeAutoSkipSegments(serverId: String, userId: String): Flow<Boolean?>

    @Query("UPDATE account_settings SET autoSkipSegments = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setAutoSkipSegments(serverId: String, userId: String, value: Boolean)

    @Query("SELECT bufferAheadSeconds FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeBufferAheadSeconds(serverId: String, userId: String): Flow<Int?>

    @Query("UPDATE account_settings SET bufferAheadSeconds = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setBufferAheadSeconds(serverId: String, userId: String, value: Int)

    @Query("SELECT maxStreamingBitrate FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeMaxStreamingBitrate(serverId: String, userId: String): Flow<Int?>

    @Query("UPDATE account_settings SET maxStreamingBitrate = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setMaxStreamingBitrate(serverId: String, userId: String, value: Int)

    @Query("SELECT captionsFollowSystem FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeCaptionsFollowSystem(serverId: String, userId: String): Flow<Boolean?>

    @Query("UPDATE account_settings SET captionsFollowSystem = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setCaptionsFollowSystem(serverId: String, userId: String, value: Boolean)

    @Query("SELECT subtitleSize FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeSubtitleSize(serverId: String, userId: String): Flow<Int?>

    @Query("UPDATE account_settings SET subtitleSize = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setSubtitleSize(serverId: String, userId: String, value: Int)

    @Query("SELECT subtitleTextColor FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeSubtitleTextColor(serverId: String, userId: String): Flow<Int?>

    @Query("UPDATE account_settings SET subtitleTextColor = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setSubtitleTextColor(serverId: String, userId: String, value: Int)

    @Query("SELECT subtitleBackground FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeSubtitleBackground(serverId: String, userId: String): Flow<Int?>

    @Query("UPDATE account_settings SET subtitleBackground = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setSubtitleBackground(serverId: String, userId: String, value: Int)

    @Query(
        "UPDATE account_settings SET captionsFollowSystem = 1, subtitleSize = 1, subtitleTextColor = 0, subtitleBackground = 1 " +
            "WHERE serverId = :serverId AND userId = :userId",
    )
    abstract suspend fun resetSubtitleStyle(serverId: String, userId: String)

    @Query("SELECT notificationsEnabled FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeNotificationsEnabled(serverId: String, userId: String): Flow<Boolean?>

    @Query("UPDATE account_settings SET notificationsEnabled = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setNotificationsEnabled(serverId: String, userId: String, value: Boolean)

    @Query("SELECT notificationScope FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeNotificationScope(serverId: String, userId: String): Flow<Int?>

    @Query("UPDATE account_settings SET notificationScope = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setNotificationScope(serverId: String, userId: String, value: Int)

    @Query("SELECT COUNT(*) > 0 FROM notified_items WHERE serverId = :serverId AND userId = :userId AND itemId = :itemId")
    abstract suspend fun wasNotified(serverId: String, userId: String, itemId: String): Boolean

    /** Whether anything was ever notified for the profile: false before its first poll. */
    @Query("SELECT COUNT(*) > 0 FROM notified_items WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun hasNotified(serverId: String, userId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun markNotified(entity: NotifiedItemEntity)

    /** Records [entity] unless it is already there; the row id, or -1 when it was (so two polls at once report it once). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertNotified(entity: NotifiedItemEntity): Long

    /** Forgets everything notified for the profile, so its next poll is a first poll again. */
    @Query("DELETE FROM notified_items WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun clearNotified(serverId: String, userId: String)

    @Query("DELETE FROM notified_items WHERE serverId = :serverId AND userId = :userId AND notifiedAt < :before")
    abstract suspend fun pruneNotified(serverId: String, userId: String, before: Long)

    /** Every saved profile on any server that has notifications on, a session and is not a kids profile. */
    @Query(
        "SELECT a.serverId, a.userId, a.name FROM accounts a JOIN account_settings s ON s.serverId = a.serverId AND s.userId = a.userId " +
            "WHERE s.notificationsEnabled = 1 AND s.kidsProfile = 0 AND a.sealedToken IS NOT NULL ORDER BY a.serverId, a.name",
    )
    abstract suspend fun getNotifiableProfiles(): List<NotifiableProfile>

    @Query("SELECT kidsProfile FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeKidsProfile(serverId: String, userId: String): Flow<Boolean?>

    @Query("UPDATE account_settings SET kidsProfile = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setKidsProfile(serverId: String, userId: String, value: Boolean)

    @Query("SELECT pinHash, pinSalt FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun getPin(serverId: String, userId: String): PinCredentials?

    @Query("UPDATE account_settings SET pinHash = :hash, pinSalt = :salt WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setPin(serverId: String, userId: String, hash: ByteArray?, salt: ByteArray?)

    @Query(
        "SELECT pinHash IS NOT NULL AS hasPin, pinOpenProfile, pinOpenSettings, pinSwitchProfile " +
            "FROM account_settings WHERE serverId = :serverId AND userId = :userId",
    )
    abstract fun observePinGates(serverId: String, userId: String): Flow<PinGateRow?>

    @Query(
        "SELECT pinHash IS NOT NULL AS hasPin, pinOpenProfile, pinOpenSettings, pinSwitchProfile " +
            "FROM account_settings WHERE serverId = :serverId AND userId = :userId",
    )
    abstract suspend fun getPinGates(serverId: String, userId: String): PinGateRow?

    /** The profiles on [serverId] that have a PIN and ask for it before they open. */
    @Query("SELECT userId FROM account_settings WHERE serverId = :serverId AND pinHash IS NOT NULL AND pinOpenProfile = 1")
    abstract fun observePinToOpen(serverId: String): Flow<List<String>>

    @Query("UPDATE account_settings SET pinOpenProfile = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setPinOpenProfile(serverId: String, userId: String, value: Boolean)

    @Query("UPDATE account_settings SET pinOpenSettings = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setPinOpenSettings(serverId: String, userId: String, value: Boolean)

    @Query("UPDATE account_settings SET pinSwitchProfile = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setPinSwitchProfile(serverId: String, userId: String, value: Boolean)

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
