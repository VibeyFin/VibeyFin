package app.vibeyfin.domain

import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.domain.model.Account
import app.vibeyfin.domain.model.ProfileRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The settings of the profile that is signed in now, read from [ConnectionManager] each time so a
 * switch of profile switches the settings too. Device-wide options are in [DevicePreferences].
 * Each setting is defined once in [ProfileSettingDefinitions]; the properties and setters here are one-liners on it.
 */
class ProfileSettings(
    private val dao: AccountDao,
    private val connection: ConnectionManager,
) {
    /**
     * Every setting of the profile signed in now, as far as read; a switch of profile starts again from nothing read, so the previous
     * profile's values never show under the new one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val values: Flow<ProfileValues> = connection.state
        .map { it.accountOrNull() }
        .distinctUntilChanged()
        .flatMapLatest { account -> if (account == null) flowOf(ProfileValues(null)) else valuesOf(account) }

    private fun valuesOf(account: Account): Flow<ProfileValues> = channelFlow {
        val read = MutableStateFlow(ProfileValues(ProfileRef(account.serverId, account.userId)))
        for (setting in ProfileSettingDefinitions.all) launch { collectInto(read, setting, account) }
        read.collect { send(it) }
    }

    private suspend fun <T : Any> collectInto(read: MutableStateFlow<ProfileValues>, setting: ProfileSetting<T>, account: Account) {
        setting.observe(dao, account).collect { value -> read.update { it.with(setting, value) } }
    }

    private fun <T : Any> observe(setting: ProfileSetting<T>): Flow<T?> = forAccount { account -> setting.observe(dao, account) }

    private suspend fun <T : Any> set(setting: ProfileSetting<T>, value: T) {
        if (!setting.accepts(value)) return
        connection.asAccount(Unit) { account -> setting.write(dao, account, value) }
    }

    private suspend fun update(write: suspend AccountDao.(Account) -> Unit) {
        connection.asAccount(Unit) { account -> dao.write(account) }
    }

    /** Whether the player shows its stats overlay. Null when nobody is signed in. Off by default. */
    val showPlaybackStats: Flow<Boolean?> = observe(ProfileSettingDefinitions.ShowPlaybackStats)

    /** Episodes in a row before "Still watching?" appears; 0 is off. Null when nobody is signed in. Three by default. */
    val bingeThreshold: Flow<Int?> = observe(ProfileSettingDefinitions.BingeThreshold)

    /** How many genre rows Home shows: [HOME_GENRES_ALL], [HOME_GENRES_NONE], or the biggest this many. Null when nobody is signed in. */
    val homeGenreRows: Flow<Int?> = observe(ProfileSettingDefinitions.HomeGenreRows)

    /** How many items Home features above Continue watching, one of [HOME_HERO_CHOICES]; 0 is off. One by default. Null when nobody is signed in. */
    val homeHeroItems: Flow<Int?> = observe(ProfileSettingDefinitions.HomeHeroItems)

    /** Whether Home's Recently added and genre rows, and More like this, include watched titles. On by default. Null when nobody is signed in. */
    val showWatchedInRows: Flow<Boolean?> = observe(ProfileSettingDefinitions.ShowWatchedInRows)

    /** True when the profile signed in now has switched watched titles out of the rows. */
    suspend fun hidesWatchedInRows(): Boolean = showWatchedInRows.first() == false

    /** Seconds the skip-back button jumps; one of [SKIP_BACK_CHOICES]. Null when nobody is signed in. */
    val skipBackSeconds: Flow<Int?> = observe(ProfileSettingDefinitions.SkipBackSeconds)

    /** Seconds the skip-forward button jumps; one of [SKIP_FORWARD_CHOICES]. Null when nobody is signed in. */
    val skipForwardSeconds: Flow<Int?> = observe(ProfileSettingDefinitions.SkipForwardSeconds)

    /** Whether the player skips intros and recaps without asking. Off by default. Null when nobody is signed in. */
    val autoSkipSegments: Flow<Boolean?> = observe(ProfileSettingDefinitions.AutoSkipSegments)

    /** Whether the player offers picture-in-picture and starts it when the app is left. On by default. Null when nobody is signed in. */
    val pictureInPicture: Flow<Boolean?> = observe(ProfileSettingDefinitions.PictureInPicture)

    /** Whether a vertical swipe on the player changes the volume and the brightness. On by default. Null when nobody is signed in. */
    val swipeGestures: Flow<Boolean?> = observe(ProfileSettingDefinitions.SwipeGestures)

    /** Seconds of video the player loads ahead; one of [BUFFER_AHEAD_CHOICES]. Null when nobody is signed in. */
    val bufferAheadSeconds: Flow<Int?> = observe(ProfileSettingDefinitions.BufferAheadSeconds)

    /** Highest streaming bitrate in bits per second, one of [QUALITY_CHOICES]; 0 is Maximum. Null when nobody is signed in. */
    val maxStreamingBitrate: Flow<Int?> = observe(ProfileSettingDefinitions.MaxStreamingBitrate)

    /** Whether subtitles use the platform's caption style. Null when nobody is signed in. */
    val captionsFollowSystem: Flow<Boolean?> = observe(ProfileSettingDefinitions.CaptionsFollowSystem)

    /** Subtitle size, one of [SUBTITLE_SIZE_CHOICES]. Null when nobody is signed in. */
    val subtitleSize: Flow<Int?> = observe(ProfileSettingDefinitions.SubtitleSize)

    /** Subtitle text colour, one of [SUBTITLE_COLOR_CHOICES]. Null when nobody is signed in. */
    val subtitleTextColor: Flow<Int?> = observe(ProfileSettingDefinitions.SubtitleTextColor)

    /** Subtitle background, one of [SUBTITLE_BACKGROUND_CHOICES]. Null when nobody is signed in. */
    val subtitleBackground: Flow<Int?> = observe(ProfileSettingDefinitions.SubtitleBackground)

    /** Whether the app posts notifications for the profile. Off by default. Null when nobody is signed in. */
    val notificationsEnabled: Flow<Boolean?> = observe(ProfileSettingDefinitions.NotificationsEnabled)

    /** Whether the app notifies when the profile's downloads finish or fail. On by default. Null when nobody is signed in. */
    val downloadNotifications: Flow<Boolean?> = observe(ProfileSettingDefinitions.DownloadNotifications)

    /** What notifies, one of [NOTIFICATION_SCOPE_CHOICES]. Null when nobody is signed in. */
    val notificationScope: Flow<Int?> = observe(ProfileSettingDefinitions.NotificationScope)

    /** Whether the profile is a Kids profile. Off by default. Null when nobody is signed in. */
    val kidsProfile: Flow<Boolean?> = observe(ProfileSettingDefinitions.KidsProfile)

    /** When the profile asks for its PIN. Null when nobody is signed in. */
    val pinGates: Flow<PinGates?> = observe(ProfileSettingDefinitions.PinGateSwitches)

    /** Whether downloads may use mobile data. Off by default. Null when nobody is signed in. */
    val downloadOverMobile: Flow<Boolean?> = observe(ProfileSettingDefinitions.DownloadOverMobile)

    /** Whether a download is removed once watched. Off by default. Null when nobody is signed in. */
    val autoCleanWatched: Flow<Boolean?> = observe(ProfileSettingDefinitions.AutoCleanWatched)

    /** The most the downloads may take, in megabytes; [NO_STORAGE_LIMIT] when there is no limit. Null when nobody is signed in. */
    val storageLimitMb: Flow<Long?> = observe(ProfileSettingDefinitions.StorageLimitMb)

    /** The id of the profile's colour theme, one of [ThemeIds.all]; an unknown stored id is [ThemeIds.DEFAULT]. Null when nobody is signed in. */
    val themeId: Flow<String?> = observe(ProfileSettingDefinitions.ThemeId)

    /** Accepts one of [ThemeIds.all]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setThemeId(value: String) = set(ProfileSettingDefinitions.ThemeId, value)

    /** Accepts one of [SKIP_BACK_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setSkipBackSeconds(value: Int) = set(ProfileSettingDefinitions.SkipBackSeconds, value)

    /** Accepts one of [SKIP_FORWARD_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setSkipForwardSeconds(value: Int) = set(ProfileSettingDefinitions.SkipForwardSeconds, value)

    /** Accepts one of [BUFFER_AHEAD_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setBufferAheadSeconds(value: Int) = set(ProfileSettingDefinitions.BufferAheadSeconds, value)

    /** Accepts one of [QUALITY_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setMaxStreamingBitrate(value: Int) = set(ProfileSettingDefinitions.MaxStreamingBitrate, value)

    /** Does nothing when nobody is signed in. */
    suspend fun setCaptionsFollowSystem(value: Boolean) = set(ProfileSettingDefinitions.CaptionsFollowSystem, value)

    /** Accepts one of [SUBTITLE_SIZE_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setSubtitleSize(value: Int) = set(ProfileSettingDefinitions.SubtitleSize, value)

    /** Accepts one of [SUBTITLE_COLOR_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setSubtitleTextColor(value: Int) = set(ProfileSettingDefinitions.SubtitleTextColor, value)

    /** Accepts one of [SUBTITLE_BACKGROUND_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setSubtitleBackground(value: Int) = set(ProfileSettingDefinitions.SubtitleBackground, value)

    /**
     * Does nothing when nobody is signed in. Switching off forgets what was notified, so switching on again starts
     * with [NewTitlesPoller]'s first-poll rule instead of announcing the titles added in between.
     */
    suspend fun setNotificationsEnabled(value: Boolean) = set(ProfileSettingDefinitions.NotificationsEnabled, value)

    /** Does nothing when nobody is signed in. */
    suspend fun setDownloadNotifications(value: Boolean) = set(ProfileSettingDefinitions.DownloadNotifications, value)

    /**
     * Accepts one of [NOTIFICATION_SCOPE_CHOICES]; anything else is ignored. Does nothing when nobody is signed in.
     * A different scope forgets what was notified, like switching off, so a wider scope doesn't announce a backlog.
     */
    suspend fun setNotificationScope(value: Int) = set(ProfileSettingDefinitions.NotificationScope, value)

    /** Does nothing when nobody is signed in. */
    suspend fun setKidsProfile(value: Boolean) = set(ProfileSettingDefinitions.KidsProfile, value)

    /** Does nothing when nobody is signed in. */
    suspend fun setPinOpenProfile(value: Boolean) = update { setPinOpenProfile(it.serverId, it.userId, value) }

    /** Does nothing when nobody is signed in. */
    suspend fun setPinOpenSettings(value: Boolean) = update { setPinOpenSettings(it.serverId, it.userId, value) }

    /** Does nothing when nobody is signed in. */
    suspend fun setPinSwitchProfile(value: Boolean) = update { setPinSwitchProfile(it.serverId, it.userId, value) }

    /** Puts all four subtitle options back to their defaults in one write. Does nothing when nobody is signed in. */
    suspend fun resetSubtitleStyle() = update { resetSubtitleStyle(it.serverId, it.userId) }

    /** Does nothing when nobody is signed in. */
    suspend fun setPictureInPicture(value: Boolean) = set(ProfileSettingDefinitions.PictureInPicture, value)

    /** Does nothing when nobody is signed in. */
    suspend fun setSwipeGestures(value: Boolean) = set(ProfileSettingDefinitions.SwipeGestures, value)

    /** Does nothing when nobody is signed in. */
    suspend fun setAutoSkipSegments(value: Boolean) = set(ProfileSettingDefinitions.AutoSkipSegments, value)

    /** Does nothing when nobody is signed in. */
    suspend fun setDownloadOverMobile(value: Boolean) = set(ProfileSettingDefinitions.DownloadOverMobile, value)

    /** Does nothing when nobody is signed in. */
    suspend fun setAutoCleanWatched(value: Boolean) = set(ProfileSettingDefinitions.AutoCleanWatched, value)

    /** Accepts [NO_STORAGE_LIMIT] or a positive size; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setStorageLimitMb(value: Long) = set(ProfileSettingDefinitions.StorageLimitMb, value)

    /** Accepts one of [HOME_HERO_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setHomeHeroItems(value: Int) = set(ProfileSettingDefinitions.HomeHeroItems, value)

    /** Does nothing when nobody is signed in. */
    suspend fun setShowWatchedInRows(value: Boolean) = set(ProfileSettingDefinitions.ShowWatchedInRows, value)

    /** Does nothing when nobody is signed in. */
    suspend fun setShowPlaybackStats(value: Boolean) = set(ProfileSettingDefinitions.ShowPlaybackStats, value)

    /** Accepts 0 (off) or 2 to 5; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setBingeThreshold(value: Int) = set(ProfileSettingDefinitions.BingeThreshold, value)

    /** Accepts one of [HOME_GENRE_CHOICES]; anything else is ignored. Does nothing when nobody is signed in. */
    suspend fun setHomeGenreRows(value: Int) = set(ProfileSettingDefinitions.HomeGenreRows, value)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> forAccount(observe: (Account) -> Flow<T?>): Flow<T?> = connection.state
        .map { it.accountOrNull() }
        .distinctUntilChanged()
        .flatMapLatest { account -> if (account == null) flowOf(null) else observe(account) }

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

        /** Notification scope: new episodes of the shows the profile follows, and new movies. */
        const val NOTIFY_FOLLOWED = 0

        /** Notification scope: everything new in the library. */
        const val NOTIFY_EVERYTHING = 1

        const val DEFAULT_NOTIFICATION_SCOPE = NOTIFY_FOLLOWED

        /** What notifies, in the order the setting lists them. */
        val NOTIFICATION_SCOPE_CHOICES = listOf(NOTIFY_FOLLOWED, NOTIFY_EVERYTHING)

        /** Episodes in a row before the player asks "Are you still watching?". */
        const val DEFAULT_BINGE_THRESHOLD = 3

        /** The downloads' storage limit when there is none. */
        const val NO_STORAGE_LIMIT = 0L

        /** Every genre gets a row on Home (what a profile that existed before the limit was added keeps). */
        const val HOME_GENRES_ALL = 0

        /** Home shows no genre rows. */
        const val HOME_GENRES_NONE = -1

        /** The Home hero features one item, as before it could feature more. */
        const val DEFAULT_HOME_HERO_ITEMS = 1

        /** How many items the Home hero features, in the order the setting lists them; 0 is off, 2 and up a carousel. */
        val HOME_HERO_CHOICES = listOf(0, 1, 2, 3, 4, 5)

        /** The choices, in the order the setting lists them. */
        val HOME_GENRE_CHOICES = listOf(HOME_GENRES_ALL, 20, 10, 5, 3, HOME_GENRES_NONE)
    }
}
