package app.vibeyfin.ui.settings

import app.vibeyfin.ui.common.Strings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsModelTest {
    @Test
    fun `a page keeps only the items that are visible`() {
        val page = SettingsPage(
            SettingsPageId.Root,
            "Settings",
            listOf(
                SettingsSection(
                    "Playback",
                    null,
                    listOf(
                        ToggleItem(SettingKey.AutoSkip, "Auto skip", checked = true),
                        ToggleItem(SettingKey.ShowStats, "Stats", checked = false, visible = false),
                    ),
                ),
                SettingsSection(
                    "Advanced",
                    null,
                    listOf(InfoItem(SettingKey.About, "About", value = "1.0", visible = false)),
                ),
            ),
        )

        val visible = page.visibleOnly()

        assertEquals(1, visible.sections.size)
        assertEquals(listOf(SettingKey.AutoSkip), visible.sections.single().items.map { it.key })
    }

    @Test
    fun `strings for the settings pages exist and use sentence case`() {
        assertEquals("Auto skip intros and recaps", Strings.autoSkipSegments)
        assertEquals("Maximum", Strings.qualityLabel(0))
        assertEquals("4K · 40 Mbps", Strings.qualityLabel(40_000_000))
        assertEquals("1080p · 10 Mbps", Strings.qualityLabel(10_000_000))
        assertEquals("720p · 4 Mbps", Strings.qualityLabel(4_000_000))
        assertEquals("480p · 1.5 Mbps", Strings.qualityLabel(1_500_000))
        assertEquals("10 s · 10 s · Still watching after 3", Strings.skippingSummary(10, 10, 3))
        val titles = listOf(
            Strings.settingsRootTitle, Strings.settingsProfileCardHelp, Strings.settingsGroupHome, Strings.settingsGroupKids,
            Strings.settingsGroupDevice, Strings.settingsGroupAdvanced, Strings.settingsKidsFooter, Strings.settingsDeviceHelp,
            Strings.settingsAbout, Strings.playbackSection, Strings.profilePageTitle, Strings.profileAccountGroup,
            Strings.switchProfileHelp, Strings.profileServerGroup, Strings.profileServerName, Strings.profileServerAddress,
            Strings.profileServerAlsoAt, Strings.profileServerFooter, Strings.signOut, Strings.signOutFooter,
            Strings.signOutConfirmTitle, Strings.signOutConfirmMessage, Strings.switchProfile,
            Strings.qualityTitle, Strings.qualityGroup, Strings.qualityFooter,
            Strings.skippingTitle, Strings.skippingGroup, Strings.skippingFooter, Strings.autoSkipHelp, Strings.autoplayGroup,
            Strings.stillWatchingAfterTitle, Strings.stillWatchingAfterHelp, Strings.bufferingGroup,
            Strings.captionsFollowSystemTitle, Strings.captionsFollowSystemHelp,
            Strings.captionsFollowSystemFooter, Strings.subtitleStyleGroup, Strings.subtitleStyleIosNote, Strings.subtitleColorLightBlue,
            Strings.subtitleReset, Strings.subtitlePreviewSample, Strings.kidsPinTitle, Strings.kidsProfileTitle,
            Strings.kidsProfileHelp, Strings.pinGroup, Strings.pinHelp, Strings.pinFooter, Strings.pinSet, Strings.pinChange,
            Strings.pinRemove, Strings.pinAskGroup, Strings.pinAskOpenProfile, Strings.pinAskOpenSettings, Strings.pinAskSwitchProfile,
            Strings.serverPolicyGroup, Strings.serverPolicyRating, Strings.serverPolicyBlockedTags, Strings.serverPolicyHours,
            Strings.serverPolicyAnyTime, Strings.serverPolicyFooter, Strings.notificationsTitle, Strings.notificationsToggle,
            Strings.notificationsWhichGroup,
        )
        // Sentence case: a word starts with a capital only at the start of a sentence, or when it is a name the app uses.
        val names = setOf("Settings", "Jellyfin", "PIN", "VibeyFin", "Mbps", "OS", "Who\u2019s", "Who's", "XL", "Kids", "Skip", "Advanced", "Home")
        for (title in titles) {
            val words = title.split(' ')
            val offenders = words.withIndex().drop(1).filter { (i, word) ->
                val bare = word.trim('\u201C', '\u201D', '"', '.', '?', ',', ';')
                bare.isNotEmpty() && bare.first().isUpperCase() && bare !in names && !words[i - 1].endsWith(".") && !words[i - 1].endsWith("?")
            }
            assertTrue(offenders.isEmpty(), "not sentence case: $title")
        }
    }

    private val styleKeys = setOf(
        SettingKey.CaptionsFollowSystem,
        SettingKey.SubtitleSize,
        SettingKey.SubtitleColor,
        SettingKey.SubtitleBackground,
        SettingKey.ResetSubtitles,
    )

    @Test
    fun `where the style cannot apply the subtitle controls show disabled with a note`() {
        val page = audioSubtitlesPage(SettingsValues(hasProfile = true, subtitleStyleApplies = false)).visibleOnly()
        val items = page.sections.flatMap { it.items }
        assertEquals(styleKeys, items.filter { it.key in styleKeys }.map { it.key }.toSet())
        assertTrue(items.filter { it.key in styleKeys }.none { it.enabled })
        assertTrue(items.first { it.key == SettingKey.SubtitlePreview }.enabled)
        assertEquals(Strings.subtitleStyleIosNote, page.sections.first { s -> s.items.any { it.key == SettingKey.SubtitleSize } }.footer)
    }

    @Test
    fun `where the style applies none of its rows is disabled and there is no note`() {
        val page = audioSubtitlesPage(SettingsValues(hasProfile = true, captionsFollowSystem = false)).visibleOnly()
        assertTrue(page.sections.flatMap { it.items }.filter { it.key in styleKeys || it.key == SettingKey.SubtitlePreview }.all { it.enabled })
        assertTrue(page.sections.none { it.footer == Strings.subtitleStyleIosNote })
    }
}
