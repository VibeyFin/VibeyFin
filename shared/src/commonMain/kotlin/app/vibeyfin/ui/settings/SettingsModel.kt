package app.vibeyfin.ui.settings

/** The pages of the Settings tree. */
enum class SettingsPageId { Root, Profile, Quality, Skipping, Subtitles, KidsAndPin, Notifications }

/** One entry per setting, so each platform can tell its rows apart. */
enum class SettingKey {
    ProfileCard,
    AlwaysAsk, ShowStats, About,
    BingeThreshold, HomeGenreRows, ShowHero,
    SkipBack, SkipForward, AutoSkip, BufferAhead,
    Quality, Subtitles, Skipping,
    CaptionsFollowSystem, SubtitleSize, SubtitleColor, SubtitleBackground, SubtitlePreview, ResetSubtitles,
    SwitchProfile, SignOut, ServerName, ServerAddress, ServerAlsoAt,
    KidsProfile, Pin, ChangePin, RemovePin, PinOpenProfile, PinOpenSettings, PinSwitchProfile,
    SetPin, PolicyStatus,
    RatingLimit, BlockedTags, AllowedHours,
    Notifications, NotificationScope,
    PictureInPicture,
}

data class SettingsPage(val id: SettingsPageId, val title: String, val sections: List<SettingsSection>) {
    /** The page as it should be drawn: hidden items, and sections left empty by that, are gone. */
    fun visibleOnly(): SettingsPage = copy(
        sections = sections.mapNotNull { section ->
            section.copy(items = section.items.filter { it.visible }).takeIf { it.items.isNotEmpty() }
        },
    )
}

data class SettingsSection(val title: String?, val footer: String?, val items: List<SettingItem>)

data class SettingOption(val value: Int, val label: String)

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
