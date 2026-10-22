package app.vibeyfin.domain

import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.domain.model.Account
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

    /** How many genre rows Home shows: [HOME_GENRES_ALL], [HOME_GENRES_NONE], or the biggest this many. Null when nobody is signed in. */
    val homeGenreRows: Flow<Int?> = forAccount { dao, a -> dao.observeHomeGenreRows(a.serverId, a.userId).map { it ?: HOME_GENRES_ALL } }

    /** Whether Home features one item above Continue watching. On by default. Null when nobody is signed in. */
    val showHomeHero: Flow<Boolean?> = forAccount { dao, a -> dao.observeShowHomeHero(a.serverId, a.userId).map { it ?: true } }

    /** Does nothing when nobody is signed in. */
    suspend fun setShowHomeHero(value: Boolean) {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setShowHomeHero(account.serverId, account.userId, value)
    }

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

    /** Accepts one of [HOME_GENRE_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setHomeGenreRows(value: Int) {
        if (value !in HOME_GENRE_CHOICES) return
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setHomeGenreRows(account.serverId, account.userId, value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> forAccount(observe: (AccountDao, Account) -> Flow<T?>): Flow<T?> = connection.state
        .map { (it as? ConnectionState.Connected)?.account }
        .distinctUntilChanged()
        .flatMapLatest { account -> if (account == null) flowOf(null) else observe(dao, account) }

    companion object {
        /** Episodes in a row before the player asks "Are you still watching?". */
        const val DEFAULT_BINGE_THRESHOLD = 3

        /** Every genre gets a row on Home (the default). */
        const val HOME_GENRES_ALL = 0

        /** Home shows no genre rows. */
        const val HOME_GENRES_NONE = -1

        /** The choices, in the order the setting lists them. */
        val HOME_GENRE_CHOICES = listOf(HOME_GENRES_ALL, 20, 10, 5, 3, HOME_GENRES_NONE)
    }
}
