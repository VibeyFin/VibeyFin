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
            Strings.settingsGroupPlayback, Strings.settingsGroupKids, Strings.settingsGroupDevice,
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
}
