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

    @Query("SELECT serverId, COUNT(*) AS count FROM accounts GROUP BY serverId")
    abstract fun observeProfileCounts(): Flow<List<ProfileCountRow>>

    @Query("SELECT sealedToken FROM accounts WHERE serverId = :serverId AND sealedToken IS NOT NULL")
    abstract suspend fun getSealedTokens(serverId: String): List<ByteArray>

    @Query("UPDATE accounts SET sealedToken = NULL WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun clearToken(serverId: String, userId: String)

    @Query("SELECT sealedSeerrSession, seerrUserId, seerrOff, seerrPasswordSignIn FROM accounts WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun getSeerrProfile(serverId: String, userId: String): SeerrProfileRow?

    @Query("SELECT sealedSeerrSession FROM accounts WHERE serverId = :serverId AND sealedSeerrSession IS NOT NULL")
    abstract suspend fun getSealedSeerrSessions(serverId: String): List<ByteArray>

    @Query(
        "UPDATE accounts SET sealedSeerrSession = :sealed, seerrUserId = :seerrUserId, seerrPasswordSignIn = :viaPassword " +
            "WHERE serverId = :serverId AND userId = :userId",
    )
    abstract suspend fun setSeerrSession(serverId: String, userId: String, sealed: ByteArray, seerrUserId: Int, viaPassword: Boolean)

    @Query("UPDATE accounts SET sealedSeerrSession = NULL, seerrUserId = NULL WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun clearSeerrSession(serverId: String, userId: String)

    @Query("UPDATE accounts SET sealedSeerrSession = NULL, seerrUserId = NULL WHERE serverId = :serverId")
    abstract suspend fun clearSeerrSessions(serverId: String)

    @Query("UPDATE accounts SET seerrOff = :off WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setSeerrOff(serverId: String, userId: String, off: Boolean)

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

    @Query("SELECT showWatchedInRows FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeShowWatchedInRows(serverId: String, userId: String): Flow<Boolean?>

    @Query("UPDATE account_settings SET showWatchedInRows = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setShowWatchedInRows(serverId: String, userId: String, value: Boolean)

    /** The hero's items: 0 while it is switched off, else how many. */
    @Query("SELECT CASE WHEN showHomeHero THEN homeHeroItems ELSE 0 END FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeHomeHeroItems(serverId: String, userId: String): Flow<Int?>

    /** 0 switches the hero off and keeps the count it had; anything else switches it on with that count. */
    @Query(
        "UPDATE account_settings SET showHomeHero = (:value > 0), homeHeroItems = CASE WHEN :value > 0 THEN :value ELSE homeHeroItems END " +
            "WHERE serverId = :serverId AND userId = :userId",
    )
    abstract suspend fun setHomeHeroItems(serverId: String, userId: String, value: Int)

    @Query("SELECT skipBackSeconds FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeSkipBackSeconds(serverId: String, userId: String): Flow<Int?>

    @Query("UPDATE account_settings SET skipBackSeconds = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setSkipBackSeconds(serverId: String, userId: String, value: Int)

    @Query("SELECT skipForwardSeconds FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeSkipForwardSeconds(serverId: String, userId: String): Flow<Int?>

    @Query("UPDATE account_settings SET skipForwardSeconds = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setSkipForwardSeconds(serverId: String, userId: String, value: Int)

    @Query("SELECT pictureInPicture FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observePictureInPicture(serverId: String, userId: String): Flow<Boolean?>

    @Query("UPDATE account_settings SET pictureInPicture = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setPictureInPicture(serverId: String, userId: String, value: Boolean)

    @Query("SELECT swipeGestures FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeSwipeGestures(serverId: String, userId: String): Flow<Boolean?>

    @Query("UPDATE account_settings SET swipeGestures = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setSwipeGestures(serverId: String, userId: String, value: Boolean)

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

    @Query("SELECT * FROM dismissed_next_up WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun dismissedNextUp(serverId: String, userId: String): List<DismissedNextUpEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun dismissNextUp(entity: DismissedNextUpEntity)

    @Query("DELETE FROM dismissed_next_up WHERE serverId = :serverId AND userId = :userId AND seriesId = :seriesId")
    abstract suspend fun forgetDismissedNextUp(serverId: String, userId: String, seriesId: String)

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

    /** How many profiles [getNotifiableProfiles] lists, as it changes: zero means nothing needs to poll in the background. */
    @Query(
        "SELECT COUNT(*) FROM accounts a JOIN account_settings s ON s.serverId = a.serverId AND s.userId = a.userId " +
            "WHERE s.notificationsEnabled = 1 AND s.kidsProfile = 0 AND a.sealedToken IS NOT NULL",
    )
    abstract fun observeNotifiableProfileCount(): Flow<Int>

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

    @Query("SELECT downloadNotifications FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeDownloadNotifications(serverId: String, userId: String): Flow<Boolean?>

    @Query("UPDATE account_settings SET downloadNotifications = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setDownloadNotifications(serverId: String, userId: String, value: Boolean)

    @Query("SELECT downloadOverMobile FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeDownloadOverMobile(serverId: String, userId: String): Flow<Boolean?>

    @Query("UPDATE account_settings SET downloadOverMobile = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setDownloadOverMobile(serverId: String, userId: String, value: Boolean)

    @Query("SELECT autoCleanWatched FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeAutoCleanWatched(serverId: String, userId: String): Flow<Boolean?>

    @Query("UPDATE account_settings SET autoCleanWatched = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setAutoCleanWatched(serverId: String, userId: String, value: Boolean)

    @Query("SELECT languagePrefs FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun getLanguagePrefs(serverId: String, userId: String): String?

    @Query("UPDATE account_settings SET languagePrefs = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setLanguagePrefs(serverId: String, userId: String, value: String?)

    @Query("SELECT storageLimitMb FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeStorageLimitMb(serverId: String, userId: String): Flow<Long?>

    @Query("UPDATE account_settings SET storageLimitMb = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setStorageLimitMb(serverId: String, userId: String, value: Long)

    @Query("SELECT themeId FROM account_settings WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeThemeId(serverId: String, userId: String): Flow<String?>

    @Query("UPDATE account_settings SET themeId = :value WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun setThemeId(serverId: String, userId: String, value: String)

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
