package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.db.AccountDao
import com.liamgleeson.vibeyfin.domain.model.Account
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The settings of the profile that is signed in now, read from [ConnectionManager] each time so a
 * switch of profile switches the settings too. Device-wide options are in [DevicePreferences].
 */
class ProfileSettings(
    private val dao: AccountDao,
    private val connection: ConnectionManager,
) {
    /** Whether the player shows its stats overlay. Null when nobody is signed in. Off by default. */
    val showPlaybackStats: Flow<Boolean?> = forAccount { dao, a -> dao.observeShowPlaybackStats(a.serverId, a.userId).map { it ?: false } }

    /** Episodes in a row before "Still watching?" appears; 0 is off. Null when nobody is signed in. Three by default. */
    val bingeThreshold: Flow<Int?> = forAccount { dao, a -> dao.observeBingeThreshold(a.serverId, a.userId).map { it ?: DEFAULT_BINGE_THRESHOLD } }

    /** Does nothing when nobody is signed in. */
    suspend fun setShowPlaybackStats(value: Boolean) {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setShowPlaybackStats(account.serverId, account.userId, value)
    }

    /** Accepts 0 (off) or 2 to 5; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setBingeThreshold(value: Int) {
        if (value != 0 && value !in 2..5) return
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setBingeThreshold(account.serverId, account.userId, value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> forAccount(observe: (AccountDao, Account) -> Flow<T?>): Flow<T?> = connection.state
        .map { (it as? ConnectionState.Connected)?.account }
        .distinctUntilChanged()
        .flatMapLatest { account -> if (account == null) flowOf(null) else observe(dao, account) }

    private companion object {
        const val DEFAULT_BINGE_THRESHOLD = 3
    }
}
