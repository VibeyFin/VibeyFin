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

    /** Seconds the skip-back button jumps; one of [SKIP_BACK_CHOICES]. Null when nobody is signed in. */
    val skipBackSeconds: Flow<Int?> = forAccount { dao, a ->
        dao.observeSkipBackSeconds(a.serverId, a.userId).map { if (it in SKIP_BACK_CHOICES) it else DEFAULT_SKIP_BACK_SECONDS }
    }

    /** Seconds the skip-forward button jumps; one of [SKIP_FORWARD_CHOICES]. Null when nobody is signed in. */
    val skipForwardSeconds: Flow<Int?> = forAccount { dao, a ->
        dao.observeSkipForwardSeconds(a.serverId, a.userId).map { if (it in SKIP_FORWARD_CHOICES) it else DEFAULT_SKIP_FORWARD_SECONDS }
    }

    /** Whether the player skips intros and recaps without asking. Off by default. Null when nobody is signed in. */
    val autoSkipSegments: Flow<Boolean?> = forAccount { dao, a -> dao.observeAutoSkipSegments(a.serverId, a.userId).map { it ?: false } }

    /** Accepts one of [SKIP_BACK_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setSkipBackSeconds(value: Int) {
        if (value !in SKIP_BACK_CHOICES) return
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setSkipBackSeconds(account.serverId, account.userId, value)
    }

    /** Accepts one of [SKIP_FORWARD_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setSkipForwardSeconds(value: Int) {
        if (value !in SKIP_FORWARD_CHOICES) return
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setSkipForwardSeconds(account.serverId, account.userId, value)
    }

    /** Does nothing when nobody is signed in. */
    suspend fun setAutoSkipSegments(value: Boolean) {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setAutoSkipSegments(account.serverId, account.userId, value)
    }

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
        const val DEFAULT_SKIP_BACK_SECONDS = 10
        const val DEFAULT_SKIP_FORWARD_SECONDS = 10

        /** The skip-back lengths, in seconds, in the order the setting lists them. */
        val SKIP_BACK_CHOICES = listOf(5, 10, 15, 30)

        /** The skip-forward lengths, in seconds, in the order the setting lists them. */
        val SKIP_FORWARD_CHOICES = listOf(10, 15, 30, 45, 60)

        /** Episodes in a row before the player asks "Are you still watching?". */
        const val DEFAULT_BINGE_THRESHOLD = 3

        /** Every genre gets a row on Home (what a profile that existed before the limit was added keeps). */
        const val HOME_GENRES_ALL = 0

        /** Home shows no genre rows. */
        const val HOME_GENRES_NONE = -1

        /** The choices, in the order the setting lists them. */
        val HOME_GENRE_CHOICES = listOf(HOME_GENRES_ALL, 20, 10, 5, 3, HOME_GENRES_NONE)
    }
}
