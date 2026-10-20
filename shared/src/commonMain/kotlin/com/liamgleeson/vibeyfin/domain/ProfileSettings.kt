package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.db.AccountDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    /** Whether the server may re-encode video for this profile. Null when nobody is signed in. Off until the user turns it on. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val allowTranscoding: Flow<Boolean?> = connection.state
        .map { (it as? ConnectionState.Connected)?.account }
        .distinctUntilChanged()
        .flatMapLatest { account ->
            if (account == null) flowOf(null) else dao.observeAllowTranscoding(account.serverId, account.userId).map { it ?: false }
        }

    /** The setting now; off when nobody is signed in. */
    suspend fun transcodingAllowed(): Boolean = allowTranscoding.first() ?: false

    /** Does nothing when nobody is signed in. */
    suspend fun setAllowTranscoding(value: Boolean) {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setAllowTranscoding(account.serverId, account.userId, value)
    }
}
