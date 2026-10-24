package app.vibeyfin.ui.settings

import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.ui.common.Strings

/**
 * Everything the Settings pages show, read from [app.vibeyfin.domain.DevicePreferences] and
 * [ProfileSettings]. [hasProfile] is false when nobody is signed in; the profile values are then
 * their defaults and the rows that show them are hidden.
 */
data class SettingsValues(
    val alwaysAskWhoIsWatching: Boolean = false,
    val appVersion: String = "",
    /** False where subtitles are burned into the picture (iOS), so the style cannot apply. */
    val subtitleStyleApplies: Boolean = true,
    val hasProfile: Boolean = false,
    val showPlaybackStats: Boolean = false,
    val bingeThreshold: Int = ProfileSettings.DEFAULT_BINGE_THRESHOLD,
    val homeGenreRows: Int = ProfileSettings.HOME_GENRES_ALL,
    val showHomeHero: Boolean = true,
    val skipBackSeconds: Int = ProfileSettings.DEFAULT_SKIP_BACK_SECONDS,
    val skipForwardSeconds: Int = ProfileSettings.DEFAULT_SKIP_FORWARD_SECONDS,
    val autoSkipSegments: Boolean = false,
    val bufferAheadSeconds: Int = ProfileSettings.DEFAULT_BUFFER_AHEAD_SECONDS,
    val maxStreamingBitrate: Int = 0,
    val captionsFollowSystem: Boolean = true,
    val subtitleSize: Int = 1,
    val subtitleTextColor: Int = 0,
    val subtitleBackground: Int = 1,
    val profileName: String = "",
    val serverName: String = "",
    /** The signed-in server's addresses, the one that worked last first. */
    val serverAddresses: List<String> = emptyList(),
)

/** Every page the shared code builds, keyed by its id. Pages built in later steps (Kids and PIN, Notifications) are not here yet. */
fun settingsPages(values: SettingsValues): Map<SettingsPageId, SettingsPage> = listOf(
    rootPage(values),
    profilePage(values),
    qualityPage(values),
    skippingPage(values),
    subtitlesPage(values),
).associateBy { it.id }

fun rootPage(v: SettingsValues): SettingsPage {
    val profile = v.hasProfile
    return SettingsPage(
        SettingsPageId.Root,
        Strings.settingsRootTitle,
        listOf(
            SettingsSection(
                title = null,
                footer = null,
                items = listOf(
                    LinkItem(
                        SettingKey.ProfileCard, Strings.profilePageTitle, Strings.settingsProfileCardHelp,
                        target = SettingsPageId.Profile, visible = profile,
                    ),
                ),
            ),
            SettingsSection(
                Strings.playbackSection,
                footer = null,
                items = listOf(
                    LinkItem(
                        SettingKey.Quality, Strings.qualityTitle,
                        target = SettingsPageId.Quality, value = Strings.qualityLabel(v.maxStreamingBitrate), visible = profile,
                    ),
                    LinkItem(
                        SettingKey.Subtitles, Strings.subtitlesTitle,
                        target = SettingsPageId.Subtitles,
                        value = if (v.captionsFollowSystem) Strings.subtitlesSystemValue else Strings.subtitlesCustomValue,
                        visible = profile,
                    ),
                    LinkItem(
                        SettingKey.Skipping, Strings.skippingTitle,
                        help = Strings.skippingSummary(v.skipBackSeconds, v.skipForwardSeconds, v.bingeThreshold),
                        target = SettingsPageId.Skipping, visible = profile,
                    ),
                ),
            ),
            SettingsSection(
                Strings.settingsGroupHome,
                footer = null,
                items = listOf(
                    ToggleItem(SettingKey.ShowHero, Strings.homeHeroTitle, checked = v.showHomeHero, visible = profile),
                    ChoiceItem(
                        SettingKey.HomeGenreRows, Strings.homeGenreRowsTitle,
                        value = v.homeGenreRows, options = genreRowOptions(), visible = profile,
                    ),
                ),
            ),
            SettingsSection(
                Strings.notificationsTitle,
                footer = null,
                items = listOf(
                    LinkItem(SettingKey.Notifications, Strings.notificationsTitle, target = SettingsPageId.Notifications, visible = profile),
                ),
            ),
            SettingsSection(
                Strings.settingsGroupKids,
                Strings.settingsKidsFooter,
                items = listOf(
                    LinkItem(SettingKey.KidsProfile, Strings.kidsProfileTitle, target = SettingsPageId.KidsAndPin, visible = profile),
                ),
            ),
            SettingsSection(
                Strings.settingsGroupDevice,
                footer = null,
                items = listOf(
                    ToggleItem(
                        SettingKey.AlwaysAsk, Strings.alwaysAskWhoIsWatchingTitle, Strings.settingsDeviceHelp,
                        checked = v.alwaysAskWhoIsWatching,
                    ),
                ),
            ),
            SettingsSection(
                Strings.settingsGroupAdvanced,
                footer = null,
                items = listOf(
                    ToggleItem(SettingKey.ShowStats, Strings.playbackStatsTitle, checked = v.showPlaybackStats, visible = profile),
                    InfoItem(SettingKey.About, Strings.settingsAbout, value = v.appVersion, visible = v.appVersion.isNotEmpty()),
                ),
            ),
        ),
    )
}

/** [kidsProfile] hides Sign out; the flag itself arrives with the kids setting. */
fun profilePage(v: SettingsValues, kidsProfile: Boolean = false): SettingsPage {
    val profile = v.hasProfile
    return SettingsPage(
        SettingsPageId.Profile,
        Strings.profilePageTitle,
        listOf(
            SettingsSection(
                title = null,
                footer = null,
                items = listOf(
                    CustomItem(
                        SettingKey.ProfileCard, v.profileName, v.serverAddresses.firstOrNull(),
                        kind = CustomKind.ProfileHeader, visible = profile,
                    ),
                ),
            ),
            SettingsSection(
                Strings.profileAccountGroup,
                footer = null,
                items = listOf(ActionItem(SettingKey.SwitchProfile, Strings.switchProfile, Strings.switchProfileHelp, visible = profile)),
            ),
            SettingsSection(
                Strings.profileServerGroup,
                Strings.profileServerFooter,
                items = listOf(
                    InfoItem(SettingKey.ServerName, Strings.profileServerName, value = v.serverName, visible = profile),
                    InfoItem(
                        SettingKey.ServerAddress, Strings.profileServerAddress,
                        value = v.serverAddresses.firstOrNull().orEmpty(), visible = profile && v.serverAddresses.isNotEmpty(),
                    ),
                    InfoItem(
                        SettingKey.ServerAlsoAt, Strings.profileServerAlsoAt,
                        value = v.serverAddresses.drop(1).joinToString(", "), visible = profile && v.serverAddresses.size > 1,
                    ),
                ),
            ),
            SettingsSection(
                title = null,
                footer = Strings.signOutFooter,
                items = listOf(ActionItem(SettingKey.SignOut, Strings.signOut, destructive = true, visible = profile && !kidsProfile)),
            ),
        ),
    )
}

fun qualityPage(v: SettingsValues) = SettingsPage(
    SettingsPageId.Quality,
    Strings.qualityTitle,
    listOf(
        SettingsSection(
            Strings.qualityGroup,
            Strings.qualityFooter,
            items = listOf(
                ChoiceItem(
                    SettingKey.Quality, Strings.qualityGroup,
                    value = v.maxStreamingBitrate,
                    options = ProfileSettings.QUALITY_CHOICES.map { SettingOption(it, Strings.qualityLabel(it)) },
                    asPage = true,
                    visible = v.hasProfile,
                ),
            ),
        ),
    ),
)

fun skippingPage(v: SettingsValues): SettingsPage {
    val profile = v.hasProfile
    return SettingsPage(
        SettingsPageId.Skipping,
        Strings.skippingTitle,
        listOf(
            SettingsSection(
                Strings.skippingGroup,
                Strings.skippingFooter,
                items = listOf(
                    ChoiceItem(
                        SettingKey.SkipBack, Strings.skipBack, value = v.skipBackSeconds,
                        options = ProfileSettings.SKIP_BACK_CHOICES.map { SettingOption(it, Strings.skipSecondsShort(it)) },
                        visible = profile,
                    ),
                    ChoiceItem(
                        SettingKey.SkipForward, Strings.skipForward, value = v.skipForwardSeconds,
                        options = ProfileSettings.SKIP_FORWARD_CHOICES.map { SettingOption(it, Strings.skipSecondsShort(it)) },
                        visible = profile,
                    ),
                    ToggleItem(
                        SettingKey.AutoSkip, Strings.autoSkipSegments, Strings.autoSkipHelp,
                        checked = v.autoSkipSegments, visible = profile,
                    ),
                ),
            ),
            SettingsSection(
                Strings.autoplayGroup,
                footer = null,
                items = listOf(
                    ChoiceItem(
                        SettingKey.BingeThreshold, Strings.stillWatchingAfterTitle, Strings.stillWatchingAfterHelp,
                        value = v.bingeThreshold,
                        options = listOf(SettingOption(0, Strings.stillWatchingOff)) +
                            (2..5).map { SettingOption(it, Strings.stillWatchingAfter(it)) },
                        visible = profile,
                    ),
                ),
            ),
            SettingsSection(
                Strings.bufferingGroup,
                Strings.bufferAheadHelp,
                items = listOf(
                    ChoiceItem(
                        SettingKey.BufferAhead, Strings.bufferAhead, value = v.bufferAheadSeconds,
                        options = ProfileSettings.BUFFER_AHEAD_CHOICES.map { SettingOption(it, bufferLabel(it)) },
                        visible = profile,
                    ),
                ),
            ),
        ),
    )
}

fun subtitlesPage(v: SettingsValues): SettingsPage {
    val profile = v.hasProfile
    val applies = v.subtitleStyleApplies
    // Where the style cannot apply the rows stay visible, dimmed, so the page says why instead of hiding it.
    val custom = profile && (!v.captionsFollowSystem || !applies)
    return SettingsPage(
        SettingsPageId.Subtitles,
        Strings.subtitlesTitle,
        listOf(
            // The preview is a card of its own above the groups, not a row of the toggle's card.
            SettingsSection(
                title = null,
                footer = null,
                items = listOf(
                    CustomItem(SettingKey.SubtitlePreview, Strings.subtitlePreviewSample, kind = CustomKind.SubtitlePreview, visible = profile),
                ),
            ),
            SettingsSection(
                title = null,
                footer = Strings.captionsFollowSystemFooter,
                items = listOf(
                    ToggleItem(
                        SettingKey.CaptionsFollowSystem, Strings.captionsFollowSystemTitle, Strings.captionsFollowSystemHelp,
                        checked = v.captionsFollowSystem, visible = profile, enabled = applies,
                    ),
                ),
            ),
            SettingsSection(
                Strings.subtitleStyleGroup,
                footer = if (applies) null else Strings.subtitleStyleIosNote,
                items = listOf(
                    ChoiceItem(
                        SettingKey.SubtitleSize, Strings.subtitleSizeTitle, value = v.subtitleSize,
                        options = options(
                            ProfileSettings.SUBTITLE_SIZE_CHOICES,
                            Strings.subtitleSizeSmall, Strings.subtitleSizeMedium, Strings.subtitleSizeLarge, Strings.subtitleSizeExtraLarge,
                        ),
                        visible = custom, enabled = applies,
                    ),
                    ChoiceItem(
                        SettingKey.SubtitleColor, Strings.subtitleColorTitle, value = v.subtitleTextColor,
                        options = options(
                            ProfileSettings.SUBTITLE_COLOR_CHOICES,
                            Strings.subtitleColorWhite, Strings.subtitleColorYellow, Strings.subtitleColorLightBlue,
                        ),
                        visible = custom, enabled = applies,
                    ),
                    ChoiceItem(
                        SettingKey.SubtitleBackground, Strings.subtitleBackgroundTitle, value = v.subtitleBackground,
                        options = options(
                            ProfileSettings.SUBTITLE_BACKGROUND_CHOICES,
                            Strings.subtitleBackgroundNone, Strings.subtitleBackgroundShadow, Strings.subtitleBackgroundBox,
                        ),
                        visible = custom, enabled = applies,
                    ),
                ),
            ),
            SettingsSection(
                title = null,
                footer = null,
                items = listOf(ActionItem(SettingKey.ResetSubtitles, Strings.subtitleReset, visible = custom, enabled = applies)),
            ),
        ),
    )
}

/** "Auto" for 0, else "20 s", as the mockup shows buffer lengths. */
private fun bufferLabel(seconds: Int) = if (seconds == 0) Strings.bufferAheadAuto else Strings.skipSecondsShort(seconds)

private fun genreRowOptions() = ProfileSettings.HOME_GENRE_CHOICES.map { value ->
    val label = when (value) {
        ProfileSettings.HOME_GENRES_ALL -> Strings.homeGenreRowsAll
        ProfileSettings.HOME_GENRES_NONE -> Strings.homeGenreRowsNone
        else -> Strings.homeGenreRowsTop(value)
    }
    SettingOption(value, label)
}

/** Pairs each choice with its label, in order. */
internal fun options(choices: List<Int>, vararg labels: String): List<SettingOption> {
    require(choices.size == labels.size) { "${choices.size} choices but ${labels.size} labels" }
    return choices.zip(labels) { value, label -> SettingOption(value, label) }
}
