package app.vibeyfin.domain

import app.vibeyfin.data.db.AccountDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Every setting of a profile, once: its default, which values it accepts, and how the [AccountDao] reads and writes it. */
object ProfileSettingDefinitions {
    val ShowPlaybackStats = ProfileSetting(
        default = false,
        read = { dao, a -> dao.observeShowPlaybackStats(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setShowPlaybackStats(a.serverId, a.userId, v) },
    )

    val BingeThreshold = ProfileSetting(
        default = ProfileSettings.DEFAULT_BINGE_THRESHOLD,
        accepts = { it == 0 || it in 2..5 },
        read = { dao, a -> dao.observeBingeThreshold(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setBingeThreshold(a.serverId, a.userId, v) },
    )

    val HomeGenreRows = ProfileSetting(
        default = ProfileSettings.HOME_GENRES_ALL,
        accepts = { it in ProfileSettings.HOME_GENRE_CHOICES },
        read = { dao, a -> dao.observeHomeGenreRows(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setHomeGenreRows(a.serverId, a.userId, v) },
    )

    val HomeHeroItems = ProfileSetting(
        default = ProfileSettings.DEFAULT_HOME_HERO_ITEMS,
        accepts = { it in ProfileSettings.HOME_HERO_CHOICES },
        read = { dao, a -> dao.observeHomeHeroItems(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setHomeHeroItems(a.serverId, a.userId, v) },
    )

    val ShowWatchedInRows = ProfileSetting(
        default = true,
        read = { dao, a -> dao.observeShowWatchedInRows(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setShowWatchedInRows(a.serverId, a.userId, v) },
    )

    val SkipBackSeconds = ProfileSetting(
        default = ProfileSettings.DEFAULT_SKIP_BACK_SECONDS,
        accepts = { it in ProfileSettings.SKIP_BACK_CHOICES },
        read = { dao, a -> dao.observeSkipBackSeconds(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setSkipBackSeconds(a.serverId, a.userId, v) },
    )

    val SkipForwardSeconds = ProfileSetting(
        default = ProfileSettings.DEFAULT_SKIP_FORWARD_SECONDS,
        accepts = { it in ProfileSettings.SKIP_FORWARD_CHOICES },
        read = { dao, a -> dao.observeSkipForwardSeconds(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setSkipForwardSeconds(a.serverId, a.userId, v) },
    )

    val AutoSkipSegments = ProfileSetting(
        default = false,
        read = { dao, a -> dao.observeAutoSkipSegments(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setAutoSkipSegments(a.serverId, a.userId, v) },
    )

    val PictureInPicture = ProfileSetting(
        default = true,
        read = { dao, a -> dao.observePictureInPicture(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setPictureInPicture(a.serverId, a.userId, v) },
    )

    val SwipeGestures = ProfileSetting(
        default = true,
        read = { dao, a -> dao.observeSwipeGestures(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setSwipeGestures(a.serverId, a.userId, v) },
    )

    val BufferAheadSeconds = ProfileSetting(
        default = ProfileSettings.DEFAULT_BUFFER_AHEAD_SECONDS,
        accepts = { it in ProfileSettings.BUFFER_AHEAD_CHOICES },
        read = { dao, a -> dao.observeBufferAheadSeconds(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setBufferAheadSeconds(a.serverId, a.userId, v) },
    )

    val MaxStreamingBitrate = ProfileSetting(
        default = 0,
        accepts = { it in ProfileSettings.QUALITY_CHOICES },
        read = { dao, a -> dao.observeMaxStreamingBitrate(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setMaxStreamingBitrate(a.serverId, a.userId, v) },
    )

    val CaptionsFollowSystem = ProfileSetting(
        default = true,
        read = { dao, a -> dao.observeCaptionsFollowSystem(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setCaptionsFollowSystem(a.serverId, a.userId, v) },
    )

    val SubtitleSize = ProfileSetting(
        default = ProfileSettings.DEFAULT_SUBTITLE_SIZE,
        accepts = { it in ProfileSettings.SUBTITLE_SIZE_CHOICES },
        read = { dao, a -> dao.observeSubtitleSize(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setSubtitleSize(a.serverId, a.userId, v) },
    )

    val SubtitleTextColor = ProfileSetting(
        default = ProfileSettings.DEFAULT_SUBTITLE_TEXT_COLOR,
        accepts = { it in ProfileSettings.SUBTITLE_COLOR_CHOICES },
        read = { dao, a -> dao.observeSubtitleTextColor(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setSubtitleTextColor(a.serverId, a.userId, v) },
    )

    val SubtitleBackground = ProfileSetting(
        default = ProfileSettings.DEFAULT_SUBTITLE_BACKGROUND,
        accepts = { it in ProfileSettings.SUBTITLE_BACKGROUND_CHOICES },
        read = { dao, a -> dao.observeSubtitleBackground(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setSubtitleBackground(a.serverId, a.userId, v) },
    )

    val NotificationsEnabled = ProfileSetting(
        default = false,
        read = { dao, a -> dao.observeNotificationsEnabled(a.serverId, a.userId) },
        write = { dao, a, v ->
            dao.setNotificationsEnabled(a.serverId, a.userId, v)
            if (!v) dao.clearNotified(a.serverId, a.userId)
        },
    )

    val DownloadNotifications = ProfileSetting(
        default = true,
        read = { dao, a -> dao.observeDownloadNotifications(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setDownloadNotifications(a.serverId, a.userId, v) },
    )

    val NotificationScope = ProfileSetting(
        default = ProfileSettings.DEFAULT_NOTIFICATION_SCOPE,
        accepts = { it in ProfileSettings.NOTIFICATION_SCOPE_CHOICES },
        read = { dao, a -> dao.observeNotificationScope(a.serverId, a.userId) },
        write = { dao, a, v ->
            val before = dao.observeNotificationScope(a.serverId, a.userId).first() ?: ProfileSettings.DEFAULT_NOTIFICATION_SCOPE
            dao.setNotificationScope(a.serverId, a.userId, v)
            if (v != before) dao.clearNotified(a.serverId, a.userId)
        },
    )

    val KidsProfile = ProfileSetting(
        default = false,
        read = { dao, a -> dao.observeKidsProfile(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setKidsProfile(a.serverId, a.userId, v) },
    )

    /** Written by the three PIN switches, not as a whole. */
    val PinGateSwitches = ProfileSetting(
        default = PinGates(),
        read = { dao, a -> dao.observePinGates(a.serverId, a.userId).map { it.toGates() } },
    )

    val DownloadOverMobile = ProfileSetting(
        default = false,
        read = { dao, a -> dao.observeDownloadOverMobile(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setDownloadOverMobile(a.serverId, a.userId, v) },
    )

    val AutoCleanWatched = ProfileSetting(
        default = false,
        read = { dao, a -> dao.observeAutoCleanWatched(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setAutoCleanWatched(a.serverId, a.userId, v) },
    )

    val StorageLimitMb = ProfileSetting(
        default = ProfileSettings.NO_STORAGE_LIMIT,
        accepts = { it >= ProfileSettings.NO_STORAGE_LIMIT },
        read = { dao, a -> dao.observeStorageLimitMb(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setStorageLimitMb(a.serverId, a.userId, v) },
    )

    val ThemeId = ProfileSetting(
        default = ThemeIds.DEFAULT,
        accepts = { it in ThemeIds.all },
        read = { dao, a -> dao.observeThemeId(a.serverId, a.userId) },
        write = { dao, a, v -> dao.setThemeId(a.serverId, a.userId, v) },
    )

    internal val all: List<ProfileSetting<*>> = listOf(
        ShowPlaybackStats, BingeThreshold, HomeGenreRows, HomeHeroItems, ShowWatchedInRows, SkipBackSeconds, SkipForwardSeconds, AutoSkipSegments,
        PictureInPicture, SwipeGestures, BufferAheadSeconds, MaxStreamingBitrate, CaptionsFollowSystem, SubtitleSize, SubtitleTextColor,
        SubtitleBackground, NotificationsEnabled, DownloadNotifications, NotificationScope, KidsProfile, PinGateSwitches, DownloadOverMobile, AutoCleanWatched,
        StorageLimitMb, ThemeId,
    )
}
