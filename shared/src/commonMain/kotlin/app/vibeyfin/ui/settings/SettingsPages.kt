package app.vibeyfin.ui.settings

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.domain.AccessDays
import app.vibeyfin.domain.Language
import app.vibeyfin.domain.LanguagePrefs
import app.vibeyfin.domain.SubtitleMode
import app.vibeyfin.domain.PinGates
import app.vibeyfin.domain.PolicySummary
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings

/** What the Kids and PIN page knows of the Jellyfin user's limits; they are read when the page opens. */
sealed interface PolicyState {
    data object Loading : PolicyState
    data class Failed(val error: ApiError) : PolicyState
    data class Loaded(val summary: PolicySummary) : PolicyState
}

/** Where the Audio and subtitles page stands with the Jellyfin account its values live on. */
sealed interface LanguageStatus {
    /** Being read; the rows show the last values known, inert. */
    data object Loading : LanguageStatus

    data object Ready : LanguageStatus

    /** Couldn't be read; the rows show the last values known, inert, and the footer says why. Opening the page again retries. */
    data class LoadFailed(val error: ApiError) : LanguageStatus

    /** The last change couldn't be saved and was put back; the rows still work. */
    data class SaveFailed(val error: ApiError) : LanguageStatus
}

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
    /** A kids profile: no Advanced settings, no Sign out, no Notifications. */
    val kidsProfile: Boolean = false,
    /** The profile's PIN and when it asks for it; nothing asks while there is no PIN. */
    val pinGates: PinGates = PinGates(),
    val policy: PolicyState = PolicyState.Loading,
    val notificationsEnabled: Boolean = false,
    val notificationScope: Int = ProfileSettings.DEFAULT_NOTIFICATION_SCOPE,
    /** The OS refused notification permission, so the page points to system settings. */
    val notificationsBlocked: Boolean = false,
    val showPlaybackStats: Boolean = false,
    val bingeThreshold: Int = ProfileSettings.DEFAULT_BINGE_THRESHOLD,
    val homeGenreRows: Int = ProfileSettings.HOME_GENRES_ALL,
    val showHomeHero: Boolean = true,
    val skipBackSeconds: Int = ProfileSettings.DEFAULT_SKIP_BACK_SECONDS,
    val skipForwardSeconds: Int = ProfileSettings.DEFAULT_SKIP_FORWARD_SECONDS,
    val autoSkipSegments: Boolean = false,
    val bufferAheadSeconds: Int = ProfileSettings.DEFAULT_BUFFER_AHEAD_SECONDS,
    val pictureInPicture: Boolean = true,
    /** The app uses only its downloads, even with a network (device-wide). */
    val offlineMode: Boolean = false,
    /** Downloads may use mobile data; off, they wait for Wi-Fi. */
    val downloadOverMobile: Boolean = false,
    /** The most the profile's downloads may take, in gigabytes; 0 is no limit. */
    val storageLimitGb: Int = 0,
    val maxStreamingBitrate: Int = 0,
    val captionsFollowSystem: Boolean = true,
    val subtitleSize: Int = ProfileSettings.DEFAULT_SUBTITLE_SIZE,
    val subtitleTextColor: Int = ProfileSettings.DEFAULT_SUBTITLE_TEXT_COLOR,
    val subtitleBackground: Int = ProfileSettings.DEFAULT_SUBTITLE_BACKGROUND,
    val profileName: String = "",
    val serverName: String = "",
    /** The signed-in server's addresses, the one that worked last first. */
    val serverAddresses: List<String> = emptyList(),
    /** The preferred languages as last read from or saved to the Jellyfin account; null before the first read. */
    val languagePrefs: LanguagePrefs? = null,
    /** The languages the server knows, sorted by name; empty until read, or when they can't be. */
    val languages: List<Language> = emptyList(),
    val languageStatus: LanguageStatus = LanguageStatus.Loading,
    /** What is typed in each page's search field. */
    val searches: Map<SettingsPageId, String> = emptyMap(),
)

/** Every page the shared code builds, keyed by its id. */
fun settingsPages(values: SettingsValues): Map<SettingsPageId, SettingsPage> = listOf(
    rootPage(values),
    profilePage(values),
    qualityPage(values),
    audioSubtitlesPage(values),
    languagePage(values, SettingsPageId.AudioLanguage),
    languagePage(values, SettingsPageId.SubtitleLanguage),
    skippingPage(values),
    kidsAndPinPage(values),
    notificationsPage(values),
).associateBy { it.id }

fun rootPage(v: SettingsValues): SettingsPage {
    val profile = v.hasProfile
    val grownUp = !v.kidsProfile
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
                        SettingKey.AudioSubtitles, Strings.audioAndSubtitles,
                        help = v.languagePrefs?.let { languagesSummary(it, v.languages) },
                        target = SettingsPageId.AudioSubtitles, visible = profile,
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
                    LinkItem(
                        SettingKey.Notifications, Strings.notificationsTitle,
                        target = SettingsPageId.Notifications, visible = profile && grownUp,
                    ),
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
                    ToggleItem(SettingKey.OfflineMode, Strings.offlineModeTitle, Strings.offlineModeHelp, checked = v.offlineMode),
                ),
            ),
            SettingsSection(
                Strings.tabDownloads,
                footer = null,
                items = listOf(
                    ToggleItem(
                        SettingKey.DownloadOverMobile, Strings.downloadOverMobileTitle, Strings.downloadOverMobileHelp,
                        checked = v.downloadOverMobile, visible = profile,
                    ),
                    ChoiceItem(
                        SettingKey.StorageLimit, Strings.storageLimitTitle, Strings.storageLimitHelp,
                        value = v.storageLimitGb, options = storageLimitOptions(v.storageLimitGb), visible = profile,
                    ),
                ),
            ),
            SettingsSection(
                Strings.settingsGroupAdvanced,
                footer = null,
                items = listOf(
                    ToggleItem(SettingKey.ShowStats, Strings.playbackStatsTitle, checked = v.showPlaybackStats, visible = profile && grownUp),
                    InfoItem(SettingKey.About, Strings.settingsAbout, value = v.appVersion, visible = grownUp && v.appVersion.isNotEmpty()),
                ),
            ),
        ),
    )
}

/** A kids profile has no Sign out: only the password, which a child should not know, gets past its PIN. */
fun profilePage(v: SettingsValues): SettingsPage {
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
                items = listOf(ActionItem(SettingKey.SignOut, Strings.signOut, destructive = true, visible = profile && !v.kidsProfile)),
            ),
        ),
    )
}

/** Hidden for a kids profile, like its link on the root page. The scope shows only while notifications are on. */
fun notificationsPage(v: SettingsValues): SettingsPage {
    val visible = v.hasProfile && !v.kidsProfile
    return SettingsPage(
        SettingsPageId.Notifications,
        Strings.notificationsTitle,
        listOf(
            SettingsSection(
                title = null,
                footer = if (v.notificationsBlocked) Strings.notificationsBlocked else Strings.notificationsFooter,
                items = listOf(
                    ToggleItem(SettingKey.Notifications, Strings.notificationsToggle, checked = v.notificationsEnabled, visible = visible),
                ),
            ),
            SettingsSection(
                Strings.notificationsWhichGroup,
                footer = null,
                items = listOf(
                    ChoiceItem(
                        SettingKey.NotificationScope, Strings.notificationsWhichGroup, value = v.notificationScope,
                        options = ProfileSettings.NOTIFICATION_SCOPE_CHOICES.map { SettingOption(it, Strings.notificationsScopeLabel(it)) },
                        visible = visible && v.notificationsEnabled,
                    ),
                ),
            ),
        ),
    )
}

fun kidsAndPinPage(v: SettingsValues): SettingsPage {
    val profile = v.hasProfile
    val hasPin = v.pinGates.hasPin
    return SettingsPage(
        SettingsPageId.KidsAndPin,
        Strings.kidsPinTitle,
        listOf(
            SettingsSection(
                title = null,
                footer = null,
                items = listOf(
                    ToggleItem(SettingKey.KidsProfile, Strings.kidsProfileTitle, Strings.kidsProfileHelp, checked = v.kidsProfile, visible = profile),
                ),
            ),
            SettingsSection(
                Strings.pinGroup,
                Strings.pinFooter,
                items = listOf(
                    CustomItem(SettingKey.Pin, Strings.pinTitle, Strings.pinSetDescription, kind = CustomKind.PinDots, visible = profile && hasPin),
                    ActionItem(SettingKey.SetPin, Strings.pinSet, Strings.pinHelp, visible = profile && !hasPin),
                    ActionItem(SettingKey.ChangePin, Strings.pinChange, visible = profile && hasPin),
                    ActionItem(SettingKey.RemovePin, Strings.pinRemove, destructive = true, visible = profile && hasPin),
                ),
            ),
            SettingsSection(
                Strings.pinAskGroup,
                footer = null,
                items = listOf(
                    ToggleItem(SettingKey.PinOpenProfile, Strings.pinAskOpenProfile, checked = v.pinGates.openProfile, visible = profile && hasPin),
                    ToggleItem(SettingKey.PinOpenSettings, Strings.pinAskOpenSettings, checked = v.pinGates.openSettings, visible = profile && hasPin),
                    ToggleItem(SettingKey.PinSwitchProfile, Strings.pinAskSwitchProfile, checked = v.pinGates.switchProfile, visible = profile && hasPin),
                ),
            ),
            policySection(v.policy, profile),
        ),
    )
}

/** The limits Jellyfin enforces, read only, in one of: loading, failed, no limits, or the three rows. */
private fun policySection(policy: PolicyState, profile: Boolean): SettingsSection {
    fun status(title: String, help: String? = null) =
        listOf(InfoItem(SettingKey.PolicyStatus, title, help, value = "", visible = profile))
    val items = when (policy) {
        PolicyState.Loading -> status(Strings.serverPolicyLoading)
        is PolicyState.Failed -> status(Strings.serverPolicyFailed, LoadMessages.forError(policy.error))
        is PolicyState.Loaded -> {
            val p = policy.summary
            if (p.maxRatingLevel == null && p.blockedTags.isEmpty() && p.schedules.isEmpty()) {
                status(Strings.serverPolicyNone)
            } else {
                listOf(
                    InfoItem(
                        SettingKey.RatingLimit, Strings.serverPolicyRating,
                        value = p.maxRatingLevel?.let { p.ratingName ?: Strings.serverPolicyLevel(it) } ?: Strings.serverPolicyNoRating,
                        visible = profile,
                    ),
                    InfoItem(
                        SettingKey.BlockedTags, Strings.serverPolicyBlockedTags,
                        value = p.blockedTags.joinToString(", ").ifEmpty { Strings.serverPolicyNoTags }, visible = profile,
                    ),
                    InfoItem(
                        SettingKey.AllowedHours, Strings.serverPolicyHours,
                        value = p.schedules.joinToString(", ") { Strings.serverPolicyWindow(daysLabel(it.days), it.startMinutes, it.endMinutes) }
                            .ifEmpty { Strings.serverPolicyAnyTime },
                        visible = profile,
                    ),
                )
            }
        }
    }
    return SettingsSection(Strings.serverPolicyGroup, Strings.serverPolicyFooter, items)
}

private fun daysLabel(days: AccessDays) = when (days) {
    AccessDays.Everyday -> Strings.serverPolicyEveryday
    AccessDays.Weekday -> Strings.serverPolicyWeekdays
    AccessDays.Weekend -> Strings.serverPolicyWeekends
    AccessDays.Sunday -> Strings.serverPolicySundays
    AccessDays.Monday -> Strings.serverPolicyMondays
    AccessDays.Tuesday -> Strings.serverPolicyTuesdays
    AccessDays.Wednesday -> Strings.serverPolicyWednesdays
    AccessDays.Thursday -> Strings.serverPolicyThursdays
    AccessDays.Friday -> Strings.serverPolicyFridays
    AccessDays.Saturday -> Strings.serverPolicySaturdays
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

/**
 * The preferred audio and subtitle languages, kept on the Jellyfin account, then how subtitles look. Until the languages are read
 * their rows show the defaults (or the last values known) and can't be changed; the footer says why. The style is the profile's
 * own, so it works either way.
 */
fun audioSubtitlesPage(v: SettingsValues): SettingsPage {
    val profile = v.hasProfile
    val prefs = v.languagePrefs ?: LanguagePrefs()
    val enabled = v.languageStatus is LanguageStatus.Ready || v.languageStatus is LanguageStatus.SaveFailed
    val status = when (val status = v.languageStatus) {
        LanguageStatus.Loading -> Strings.languagesLoading
        LanguageStatus.Ready -> null
        is LanguageStatus.LoadFailed ->
            if (status.error == ApiError.Network) LoadMessages.LANGUAGES_UNREACHABLE else LoadMessages.forError(status.error)
        is LanguageStatus.SaveFailed ->
            if (status.error == ApiError.Network) LoadMessages.LANGUAGES_SAVE_FAILED else LoadMessages.forError(status.error)
    }
    return SettingsPage(
        SettingsPageId.AudioSubtitles,
        Strings.audioAndSubtitles,
        listOf(
            SettingsSection(
                Strings.audio,
                Strings.audioLanguageFooter,
                items = listOf(
                    LinkItem(
                        SettingKey.AudioLanguage, Strings.audioLanguageTitle,
                        target = SettingsPageId.AudioLanguage, value = languageName(prefs.audioLanguage, v.languages),
                        visible = profile, enabled = enabled,
                    ),
                ),
            ),
            SettingsSection(
                Strings.subtitles,
                Strings.subtitleModeFooter(prefs.subtitleMode),
                items = listOf(
                    LinkItem(
                        SettingKey.SubtitleLanguage, Strings.subtitleLanguageTitle,
                        target = SettingsPageId.SubtitleLanguage, value = languageName(prefs.subtitleLanguage, v.languages),
                        visible = profile, enabled = enabled,
                    ),
                    ChoiceItem(
                        SettingKey.SubtitleMode, Strings.subtitleModeTitle, value = prefs.subtitleMode.ordinal,
                        options = SubtitleMode.entries.map { SettingOption(it.ordinal, Strings.subtitleModeLabel(it)) },
                        visible = profile, enabled = enabled,
                    ),
                ),
            ),
            SettingsSection(
                title = null,
                footer = status ?: Strings.languagesFooter,
                items = listOf(
                    ToggleItem(
                        SettingKey.RememberTracks, Strings.rememberTracksTitle, Strings.rememberTracksHelp,
                        checked = prefs.rememberTracks, visible = profile, enabled = enabled,
                    ),
                ),
            ),
        ) + subtitleStyleSections(v),
    )
}

/**
 * The checklist of languages for [id] ([SettingsPageId.AudioLanguage] or [SettingsPageId.SubtitleLanguage]), filtered by what is
 * typed in its search. Each option carries its language's code, which is what a pick sends back.
 */
fun languagePage(v: SettingsValues, id: SettingsPageId): SettingsPage {
    val audio = id == SettingsPageId.AudioLanguage
    val prefs = v.languagePrefs ?: LanguagePrefs()
    val choices = languageChoices(v.languages, prefs)
    val query = v.searches[id].orEmpty().trim()
    val current = choiceValue(if (audio) prefs.audioLanguage else prefs.subtitleLanguage, choices)
    val options = (listOf(SettingOption(0, Strings.anyLanguage, code = "")) + choices.mapIndexed { i, it -> SettingOption(i + 1, it.name, it.code) })
        .filter { option ->
            query.isEmpty() || option.label.contains(query, ignoreCase = true) ||
                option.code.equals(query, ignoreCase = true)
        }
    val visible = v.hasProfile
    val key = if (audio) SettingKey.AudioLanguage else SettingKey.SubtitleLanguage
    return SettingsPage(
        id,
        if (audio) Strings.audioLanguageTitle else Strings.subtitleLanguageTitle,
        listOf(
            SettingsSection(
                title = null,
                footer = if (options.isEmpty()) null else (if (audio) Strings.anyLanguageAudioHelp else Strings.anyLanguageSubtitleHelp) + " " + Strings.languageListFooter,
                items = listOf(
                    ChoiceItem(key, Strings.languageListGroup, value = current, options = options, asPage = true, visible = visible && options.isNotEmpty()),
                    InfoItem(SettingKey.NoLanguageMatch, Strings.noLanguageMatches, value = "", visible = visible && options.isEmpty()),
                ),
            ),
        ),
        search = v.searches[id].orEmpty(),
        searchPrompt = Strings.searchLanguages,
        sheet = true,
    )
}

/**
 * Every language a choice can name: the server's, then any code the account holds that the server doesn't list (set by another
 * client), so it isn't lost. Position i here is option value i + 1, only to tell the chosen row; 0 is "Any language".
 */
fun languageChoices(languages: List<Language>, prefs: LanguagePrefs): List<Language> {
    val unknown = listOfNotNull(prefs.audioLanguage, prefs.subtitleLanguage)
        .filter { code -> languages.none { it.matches(code) } }
        .distinct()
        .map { Language(it, it) }
    return languages + unknown
}

private fun choiceValue(code: String?, choices: List<Language>): Int =
    code?.let { c -> choices.indexOfFirst { it.matches(c) }.takeIf { it >= 0 }?.plus(1) } ?: 0

private fun languageName(code: String?, languages: List<Language>): String =
    code?.let { c -> languages.firstOrNull { it.matches(c) }?.name ?: c } ?: Strings.anyLanguage

private fun languagesSummary(prefs: LanguagePrefs, languages: List<Language>) = Strings.languagesSummary(
    prefs.audioLanguage?.let { languageName(it, languages) },
    prefs.subtitleLanguage?.let { languageName(it, languages) },
    prefs.subtitleMode,
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
                title = null,
                Strings.pictureInPictureHelp,
                items = listOf(
                    ToggleItem(SettingKey.PictureInPicture, Strings.pictureInPicture, checked = v.pictureInPicture, visible = profile),
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

/** How subtitles look, at the end of the Audio and subtitles page. */
private fun subtitleStyleSections(v: SettingsValues): List<SettingsSection> {
    val profile = v.hasProfile
    val applies = v.subtitleStyleApplies
    // Where the style cannot apply the rows stay visible, dimmed, so the page says why instead of hiding it.
    val custom = profile && (!v.captionsFollowSystem || !applies)
    return listOf(
        // The preview is a card of its own under the group's title, not a row of the toggle's card.
        SettingsSection(
            Strings.subtitleStyleGroup,
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
            title = null,
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
    )
}

/** "Auto" for 0, else "20 s", as the mockup shows buffer lengths. */
private fun bufferLabel(seconds: Int) = if (seconds == 0) Strings.bufferAheadAuto else Strings.skipSecondsShort(seconds)

/** The sizes the downloads' limit is offered in, gigabytes; 0 is no limit. A size set some other way stays on the list. */
private fun storageLimitOptions(current: Int) = (listOf(0, 5, 10, 20, 50, 100) + current).distinct().sorted().map { gigabytes ->
    SettingOption(gigabytes, if (gigabytes == 0) Strings.storageLimitNone else Strings.storageLimitGb(gigabytes))
}

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
