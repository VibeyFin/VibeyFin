package app.vibeyfin.ui.settings

import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.ui.common.Strings

fun rootPage(v: SettingsValues): SettingsPage = SettingsPage(
    SettingsPageId.Root,
    Strings.settingsRootTitle,
    listOf(
        profileCardSection(v),
        playbackSection(v),
        homeSection(v),
        SettingsSection(
            Strings.notificationsTitle,
            footer = null,
            items = listOf(
                LinkItem(SettingKey.Notifications, Strings.notificationsTitle, target = SettingsPageId.Notifications, visible = v.hasProfile && !v.kidsProfile),
            ),
        ),
        SettingsSection(
            Strings.requestsSection,
            Strings.seerrRowHelp,
            items = listOf(
                LinkItem(
                    SettingKey.Seerr,
                    Strings.seerr,
                    target = SettingsPageId.Seerr,
                    value = v.seerrStatus,
                    visible = v.hasProfile && !v.kidsProfile && v.seerrStatus != null,
                ),
            ),
        ),
        SettingsSection(
            Strings.settingsGroupKids,
            Strings.settingsKidsFooter,
            items = listOf(
                LinkItem(SettingKey.KidsProfile, Strings.kidsProfileTitle, target = SettingsPageId.KidsAndPin, visible = v.hasProfile),
            ),
        ),
        deviceSection(v),
        downloadsSection(v),
        advancedSection(v),
    ),
)

private fun profileCardSection(v: SettingsValues) = SettingsSection(
    title = null,
    footer = null,
    items = listOf(
        LinkItem(
            SettingKey.ProfileCard,
            Strings.profilePageTitle,
            Strings.settingsProfileCardHelp,
            target = SettingsPageId.Profile,
            visible = v.hasProfile,
        ),
    ),
)

private fun playbackSection(v: SettingsValues) = SettingsSection(
    Strings.playbackSection,
    footer = null,
    items = listOf(
        LinkItem(
            SettingKey.Quality,
            Strings.qualityTitle,
            target = SettingsPageId.Quality,
            value = Strings.qualityLabel(v.maxStreamingBitrate),
            visible = v.hasProfile,
        ),
        LinkItem(
            SettingKey.AudioSubtitles,
            Strings.audioAndSubtitles,
            help = v.languagePrefs?.let { languagesSummary(it, v.languages) },
            target = SettingsPageId.AudioSubtitles,
            visible = v.hasProfile,
        ),
        LinkItem(
            SettingKey.Skipping,
            Strings.skippingTitle,
            help = Strings.skippingSummary(v.skipBackSeconds, v.skipForwardSeconds, v.bingeThreshold),
            target = SettingsPageId.Skipping,
            visible = v.hasProfile,
        ),
    ),
)

private fun homeSection(v: SettingsValues) = SettingsSection(
    Strings.settingsGroupHome,
    footer = null,
    items = listOf(
        ChoiceItem(
            SettingKey.HomeHeroItems,
            Strings.homeHeroItemsTitle,
            value = v.homeHeroItems,
            options = heroItemOptions(),
            visible = v.hasProfile,
        ),
        ChoiceItem(
            SettingKey.HomeGenreRows,
            Strings.homeGenreRowsTitle,
            value = v.homeGenreRows,
            options = genreRowOptions(),
            visible = v.hasProfile,
        ),
        ToggleItem(
            SettingKey.ShowWatchedInRows,
            Strings.showWatchedInRowsTitle,
            Strings.showWatchedInRowsHelp,
            checked = v.showWatchedInRows,
            visible = v.hasProfile,
        ),
    ),
)

private fun deviceSection(v: SettingsValues) = SettingsSection(
    Strings.settingsGroupDevice,
    footer = null,
    items = listOf(
        ToggleItem(
            SettingKey.AlwaysAsk,
            Strings.alwaysAskWhoIsWatchingTitle,
            Strings.settingsDeviceHelp,
            checked = v.alwaysAskWhoIsWatching,
        ),
        ToggleItem(SettingKey.OfflineMode, Strings.offlineModeTitle, Strings.offlineModeHelp, checked = v.offlineMode),
        languageItem(v),
    ),
)

private fun languageItem(v: SettingsValues): ChoiceItem {
    val options: List<SettingOption> = listOf(SettingOption(DEVICE_LANGUAGE, Strings.languageDeviceDefault)) +
        v.appLanguages.mapIndexed { index, language -> SettingOption(index + 1, language.name, language.tag) }
    val selected: Int = options.firstOrNull { it.code != null && it.code == v.appLanguage }?.value ?: DEVICE_LANGUAGE
    return ChoiceItem(
        SettingKey.AppLanguage,
        Strings.languageTitle,
        Strings.languageHelp,
        value = selected,
        options = options,
        visible = v.appLanguages.size > 1,
    )
}

internal const val DEVICE_LANGUAGE: Int = 0

private fun downloadsSection(v: SettingsValues) = SettingsSection(
    Strings.tabDownloads,
    footer = null,
    items = listOf(
        ToggleItem(
            SettingKey.DownloadOverMobile,
            Strings.downloadOverMobileTitle,
            Strings.downloadOverMobileHelp,
            checked = v.downloadOverMobile,
            visible = v.hasProfile,
        ),
        ChoiceItem(
            SettingKey.StorageLimit,
            Strings.storageLimitTitle,
            Strings.storageLimitHelp,
            value = v.storageLimitGb,
            options = storageLimitOptions(v.storageLimitGb),
            visible = v.hasProfile,
        ),
        ActionItem(SettingKey.PurgeDownloads, Strings.purgeDownloadsTitle, Strings.purgeDownloadsHelp, destructive = true, visible = v.hasProfile),
    ),
)

private fun advancedSection(v: SettingsValues): SettingsSection {
    val grownUp = !v.kidsProfile

    return SettingsSection(
        Strings.settingsGroupAdvanced,
        footer = null,
        items = listOf(
            ToggleItem(SettingKey.ShowStats, Strings.playbackStatsTitle, checked = v.showPlaybackStats, visible = v.hasProfile && grownUp),
            InfoItem(SettingKey.About, Strings.settingsAbout, value = v.appVersion, visible = grownUp && v.appVersion.isNotEmpty()),
        ),
    )
}

// The sizes the downloads' limit is offered in, gigabytes; 0 is no limit. A size set some other way stays on the list.
private fun storageLimitOptions(current: Int) = (listOf(0, 5, 10, 20, 50, 100) + current).distinct().sorted().map { gigabytes ->
    SettingOption(gigabytes, if (gigabytes == 0) Strings.storageLimitNone else Strings.storageLimitGb(gigabytes))
}

private fun heroItemOptions() = ProfileSettings.HOME_HERO_CHOICES.map { value ->
    SettingOption(value, if (value == 0) Strings.homeHeroItemsOff else Strings.homeHeroItemsCount(value))
}

private fun genreRowOptions() = ProfileSettings.HOME_GENRE_CHOICES.map { value ->
    val label = when (value) {
        ProfileSettings.HOME_GENRES_ALL -> Strings.homeGenreRowsAll
        ProfileSettings.HOME_GENRES_NONE -> Strings.homeGenreRowsNone
        else -> Strings.homeGenreRowsTop(value)
    }
    SettingOption(value, label)
}
