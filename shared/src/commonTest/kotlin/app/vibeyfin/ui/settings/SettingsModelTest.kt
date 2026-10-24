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
                    "Playback", null,
                    listOf(
                        ToggleItem(SettingKey.ShowHero, "Hero", checked = true),
                        ToggleItem(SettingKey.ShowStats, "Stats", checked = false, visible = false),
                    ),
                ),
                SettingsSection(
                    "Advanced", null,
                    listOf(InfoItem(SettingKey.About, "About", value = "1.0", visible = false)),
                ),
            ),
        )

        val visible = page.visibleOnly()

        assertEquals(1, visible.sections.size)
        assertEquals(listOf(SettingKey.ShowHero), visible.sections.single().items.map { it.key })
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
            Strings.playbackSection, Strings.settingsGroupKids, Strings.settingsGroupDevice,
            Strings.skippingTitle, Strings.qualityTitle, Strings.subtitlesTitle, Strings.kidsPinTitle,
            Strings.notificationsTitle, Strings.signOut, Strings.switchProfile, Strings.pinAskOpenProfile,
        )
        // Sentence case: no word after the first starts with a capital, bar the names the app uses.
        val names = setOf("Settings", "Jellyfin", "PIN", "VibeyFin", "Mbps", "OS", "Who's", "XL")
        for (title in titles) {
            val rest = title.split(' ').drop(1).filter { it !in names }
            assertTrue(rest.none { it.first().isUpperCase() }, "not sentence case: $title")
        }
    }

    private val styleKeys = setOf(
        SettingKey.CaptionsFollowSystem, SettingKey.SubtitleSize, SettingKey.SubtitleColor,
        SettingKey.SubtitleBackground, SettingKey.ResetSubtitles,
    )

    @Test
    fun `where the style cannot apply the subtitle controls show disabled with a note`() {
        val page = subtitlesPage(SettingsValues(hasProfile = true, subtitleStyleApplies = false)).visibleOnly()
        val items = page.sections.flatMap { it.items }
        assertEquals(styleKeys, items.filter { it.key in styleKeys }.map { it.key }.toSet())
        assertTrue(items.filter { it.key in styleKeys }.none { it.enabled })
        assertTrue(items.first { it.key == SettingKey.SubtitlePreview }.enabled)
        assertEquals(Strings.subtitleStyleIosNote, page.sections.first { s -> s.items.any { it.key == SettingKey.SubtitleSize } }.footer)
    }

    @Test
    fun `where the style applies nothing on the subtitles page is disabled and there is no note`() {
        val page = subtitlesPage(SettingsValues(hasProfile = true, captionsFollowSystem = false)).visibleOnly()
        assertTrue(page.sections.flatMap { it.items }.all { it.enabled })
        assertTrue(page.sections.none { it.footer == Strings.subtitleStyleIosNote })
    }
}
