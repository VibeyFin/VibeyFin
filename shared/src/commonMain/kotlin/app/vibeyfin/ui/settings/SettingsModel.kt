package app.vibeyfin.ui.settings

/** The pages of the Settings tree. [Seerr] and [QuickConnect] are drawn by each app's own screen, since they have a text field; their shared pages are empty. */
enum class SettingsPageId { Root, Profile, Quality, AudioSubtitles, Skipping, KidsAndPin, Notifications, AudioLanguage, SubtitleLanguage, Seerr, QuickConnect }

/** One entry per setting, so each platform can tell its rows apart. */
enum class SettingKey {
    ProfileCard,
    AlwaysAsk,
    AppLanguage,
    ShowStats,
    About,
    BingeThreshold,
    HomeGenreRows,
    HomeHeroItems,
    ShowWatchedInRows,
    SkipBack,
    SkipForward,
    AutoSkip,
    BufferAhead,
    Quality,
    Skipping,
    CaptionsFollowSystem,
    SubtitleSize,
    SubtitleColor,
    SubtitleBackground,
    SubtitlePreview,
    ResetSubtitles,
    SwitchProfile,
    SignOut,
    ServerName,
    ServerAddress,
    ServerAlsoAt,
    KidsProfile,
    Pin,
    ChangePin,
    RemovePin,
    PinOpenProfile,
    PinOpenSettings,
    PinSwitchProfile,
    SetPin,
    PolicyStatus,
    RatingLimit,
    BlockedTags,
    AllowedHours,
    Notifications,
    NotificationScope,
    PictureInPicture,
    SwipeGestures,
    DimWhenPaused,
    DownloadNotifications,
    OfflineMode,
    DownloadOverMobile,
    StorageLimit,
    PurgeDownloads,
    AudioSubtitles,
    AudioLanguage,
    SubtitleLanguage,
    SubtitleMode,
    RememberTracks,
    NoLanguageMatch,
    Seerr,
    QuickConnect,
}

/**
 * One page. A page with a [search] (empty when nothing is typed) draws a search field above its sections; what is typed goes to
 * [SettingsViewModel.onSearch] and the page comes back filtered. Null is a page without one.
 */
data class SettingsPage(
    val id: SettingsPageId,
    val title: String,
    val sections: List<SettingsSection>,
    val search: String? = null,
    /** The search field's placeholder, when [search] is set. */
    val searchPrompt: String? = null,
    /**
     * The page opens as a picker sheet over the page that links to it, like a filter's list, rather than as a page of its own:
     * its first [ChoiceItem] is the list, and picking from it closes the sheet.
     */
    val sheet: Boolean = false,
) {
    /** The page as it should be drawn: hidden items, and sections left empty by that, are gone. */
    fun visibleOnly(): SettingsPage = copy(
        sections = sections.mapNotNull { section ->
            section.copy(items = section.items.filter { it.visible }).takeIf { it.items.isNotEmpty() }
        },
    )
}

data class SettingsSection(val title: String?, val footer: String?, val items: List<SettingItem>)

/** [code] keys an option of a language list by the language itself, not its place in the list; "" is no preference. */
data class SettingOption(val value: Int, val label: String, val code: String? = null)

/** What a row is called for custom views the platforms draw themselves. */
enum class CustomKind { SubtitlePreview, PinDots, ProfileHeader }

/** One row. `visible` is resolved by the builder, because Swift cannot call predicates. */
sealed interface SettingItem {
    val key: SettingKey
    val title: String
    val help: String?
    val visible: Boolean

    /** False draws the row dimmed and inert; it still shows its value. */
    val enabled: Boolean
}

data class ToggleItem(
    override val key: SettingKey,
    override val title: String,
    override val help: String? = null,
    val checked: Boolean,
    override val visible: Boolean = true,
    override val enabled: Boolean = true,
) : SettingItem

data class ChoiceItem(
    override val key: SettingKey,
    override val title: String,
    override val help: String? = null,
    val value: Int,
    val options: List<SettingOption>,
    /** Draw as a checklist page instead of a menu; for choices whose options need their own explanation. */
    val asPage: Boolean = false,
    override val visible: Boolean = true,
    override val enabled: Boolean = true,
) : SettingItem

data class LinkItem(
    override val key: SettingKey,
    override val title: String,
    override val help: String? = null,
    val target: SettingsPageId,
    val value: String? = null,
    override val visible: Boolean = true,
    override val enabled: Boolean = true,
) : SettingItem

data class ActionItem(
    override val key: SettingKey,
    override val title: String,
    override val help: String? = null,
    val destructive: Boolean = false,
    override val visible: Boolean = true,
    override val enabled: Boolean = true,
) : SettingItem

data class InfoItem(
    override val key: SettingKey,
    override val title: String,
    override val help: String? = null,
    val value: String,
    override val visible: Boolean = true,
    override val enabled: Boolean = true,
) : SettingItem

data class CustomItem(
    override val key: SettingKey,
    override val title: String,
    override val help: String? = null,
    val kind: CustomKind,
    override val visible: Boolean = true,
    override val enabled: Boolean = true,
) : SettingItem
