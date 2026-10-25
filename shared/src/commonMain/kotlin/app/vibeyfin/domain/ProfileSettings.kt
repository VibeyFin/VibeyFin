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

    /** Seconds of video the player loads ahead; one of [BUFFER_AHEAD_CHOICES]. Null when nobody is signed in. */
    val bufferAheadSeconds: Flow<Int?> = forAccount { dao, a ->
        dao.observeBufferAheadSeconds(a.serverId, a.userId).map { if (it in BUFFER_AHEAD_CHOICES) it else DEFAULT_BUFFER_AHEAD_SECONDS }
    }

    /** Accepts one of [BUFFER_AHEAD_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setBufferAheadSeconds(value: Int) {
        if (value !in BUFFER_AHEAD_CHOICES) return
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setBufferAheadSeconds(account.serverId, account.userId, value)
    }

    /** Highest streaming bitrate in bits per second, one of [QUALITY_CHOICES]; 0 is Maximum. Null when nobody is signed in. */
    val maxStreamingBitrate: Flow<Int?> = forAccount { dao, a ->
        dao.observeMaxStreamingBitrate(a.serverId, a.userId).map { if (it in QUALITY_CHOICES) it else 0 }
    }

    /** Accepts one of [QUALITY_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setMaxStreamingBitrate(value: Int) {
        if (value !in QUALITY_CHOICES) return
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setMaxStreamingBitrate(account.serverId, account.userId, value)
    }

    /** Whether subtitles use the platform's caption style. Null when nobody is signed in. */
    val captionsFollowSystem: Flow<Boolean?> = forAccount { dao, a -> dao.observeCaptionsFollowSystem(a.serverId, a.userId) }

    /** Does nothing when nobody is signed in. */
    suspend fun setCaptionsFollowSystem(value: Boolean) {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setCaptionsFollowSystem(account.serverId, account.userId, value)
    }

    /** Subtitle size, one of [SUBTITLE_SIZE_CHOICES]. Null when nobody is signed in. */
    val subtitleSize: Flow<Int?> = forAccount { dao, a ->
        dao.observeSubtitleSize(a.serverId, a.userId).map { if (it in SUBTITLE_SIZE_CHOICES) it else DEFAULT_SUBTITLE_SIZE }
    }

    /** Accepts one of [SUBTITLE_SIZE_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setSubtitleSize(value: Int) {
        if (value !in SUBTITLE_SIZE_CHOICES) return
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setSubtitleSize(account.serverId, account.userId, value)
    }

    /** Subtitle text colour, one of [SUBTITLE_COLOR_CHOICES]. Null when nobody is signed in. */
    val subtitleTextColor: Flow<Int?> = forAccount { dao, a ->
        dao.observeSubtitleTextColor(a.serverId, a.userId).map { if (it in SUBTITLE_COLOR_CHOICES) it else DEFAULT_SUBTITLE_TEXT_COLOR }
    }

    /** Accepts one of [SUBTITLE_COLOR_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setSubtitleTextColor(value: Int) {
        if (value !in SUBTITLE_COLOR_CHOICES) return
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setSubtitleTextColor(account.serverId, account.userId, value)
    }

    /** Subtitle background, one of [SUBTITLE_BACKGROUND_CHOICES]. Null when nobody is signed in. */
    val subtitleBackground: Flow<Int?> = forAccount { dao, a ->
        dao.observeSubtitleBackground(a.serverId, a.userId).map { if (it in SUBTITLE_BACKGROUND_CHOICES) it else DEFAULT_SUBTITLE_BACKGROUND }
    }

    /** Accepts one of [SUBTITLE_BACKGROUND_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setSubtitleBackground(value: Int) {
        if (value !in SUBTITLE_BACKGROUND_CHOICES) return
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setSubtitleBackground(account.serverId, account.userId, value)
    }

    /** Whether the app posts notifications for the profile. Off by default. Null when nobody is signed in. */
    val notificationsEnabled: Flow<Boolean?> = forAccount { dao, a -> dao.observeNotificationsEnabled(a.serverId, a.userId).map { it ?: false } }

    /** Does nothing when nobody is signed in. */
    suspend fun setNotificationsEnabled(value: Boolean) {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setNotificationsEnabled(account.serverId, account.userId, value)
    }

    /** What notifies, one of [NOTIFICATION_SCOPE_CHOICES]. Null when nobody is signed in. */
    val notificationScope: Flow<Int?> = forAccount { dao, a ->
        dao.observeNotificationScope(a.serverId, a.userId).map { if (it in NOTIFICATION_SCOPE_CHOICES) it else DEFAULT_NOTIFICATION_SCOPE }
    }

    /** Accepts one of [NOTIFICATION_SCOPE_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setNotificationScope(value: Int) {
        if (value !in NOTIFICATION_SCOPE_CHOICES) return
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setNotificationScope(account.serverId, account.userId, value)
    }

    /** Whether the profile is a Kids profile. Off by default. Null when nobody is signed in. */
    val kidsProfile: Flow<Boolean?> = forAccount { dao, a -> dao.observeKidsProfile(a.serverId, a.userId).map { it ?: false } }

    /** Does nothing when nobody is signed in. */
    suspend fun setKidsProfile(value: Boolean) {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setKidsProfile(account.serverId, account.userId, value)
    }

    /** When the profile asks for its PIN. Null when nobody is signed in. */
    val pinGates: Flow<PinGates?> = forAccount { dao, a -> dao.observePinGates(a.serverId, a.userId).map { it.toGates() } }

    /** Does nothing when nobody is signed in. */
    suspend fun setPinOpenProfile(value: Boolean) {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setPinOpenProfile(account.serverId, account.userId, value)
    }

    /** Does nothing when nobody is signed in. */
    suspend fun setPinOpenSettings(value: Boolean) {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setPinOpenSettings(account.serverId, account.userId, value)
    }

    /** Does nothing when nobody is signed in. */
    suspend fun setPinSwitchProfile(value: Boolean) {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.setPinSwitchProfile(account.serverId, account.userId, value)
    }

    /** Puts all four subtitle options back to their defaults in one write. Does nothing when nobody is signed in. */
    suspend fun resetSubtitleStyle() {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        dao.resetSubtitleStyle(account.serverId, account.userId)
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

        const val DEFAULT_BUFFER_AHEAD_SECONDS = 20

        /** How far ahead the player loads, in seconds, in the order the setting lists them; 0 is Auto, the platform player's own choice. */
        val BUFFER_AHEAD_CHOICES = listOf(0, 10, 20, 30, 40, 50, 60)

        /** Streaming bitrate caps in bits per second, in the order the setting lists them; 0 is Maximum. */
        val QUALITY_CHOICES = listOf(0, 40_000_000, 10_000_000, 4_000_000, 1_500_000)

        /** Medium text, white, over a shadow; the same values the DAO's reset query writes. */
        const val DEFAULT_SUBTITLE_SIZE = 1

        const val DEFAULT_SUBTITLE_TEXT_COLOR = 0

        const val DEFAULT_SUBTITLE_BACKGROUND = 1

        /** Subtitle sizes: small, medium, large, extra large. */
        val SUBTITLE_SIZE_CHOICES = listOf(0, 1, 2, 3)

        /** Subtitle text colours: white, yellow, light blue. */
        val SUBTITLE_COLOR_CHOICES = listOf(0, 1, 2)

        /** Subtitle backgrounds: none, shadow, box. */
        val SUBTITLE_BACKGROUND_CHOICES = listOf(0, 1, 2)

        /** Shows the profile follows and new movies. */
        const val DEFAULT_NOTIFICATION_SCOPE = 0

        /** What notifies: 0 shows I follow and new movies, 1 everything new. */
        val NOTIFICATION_SCOPE_CHOICES = listOf(0, 1)

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
