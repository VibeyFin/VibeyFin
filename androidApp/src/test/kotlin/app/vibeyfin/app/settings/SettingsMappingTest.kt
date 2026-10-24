package app.vibeyfin.app.settings

import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.settings.ChoiceItem
import app.vibeyfin.ui.settings.LinkItem
import app.vibeyfin.ui.settings.SettingKey
import app.vibeyfin.ui.settings.SettingOption
import app.vibeyfin.ui.settings.SettingsPage
import app.vibeyfin.ui.settings.SettingsPageId
import app.vibeyfin.ui.settings.SettingsSection
import app.vibeyfin.ui.settings.SettingsValues
import app.vibeyfin.ui.settings.settingsPages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SettingsMappingTest {
    private val pages = settingsPages(SettingsValues(hasProfile = true))

    private fun links(page: SettingsPage) = page.sections.flatMap { it.items }.filterIsInstance<LinkItem>().map { it.target }

    @Test
    fun `the root page keeps its groups and links to pages that exist`() {
        val root = pages.getValue(SettingsPageId.Root).drawn { it in pages }
        assertEquals(
            listOf(null, Strings.playbackSection, Strings.settingsGroupHome, Strings.settingsGroupDevice, Strings.settingsGroupAdvanced),
            root.sections.map { it.title },
        )
        assertTrue(links(root).all { it in pages })
        assertTrue(SettingsPageId.Subtitles in links(root))
        assertEquals(1, links(root).count { it == SettingsPageId.Skipping })
    }

    @Test
    fun `a link to a missing page is hidden and an emptied group goes with it`() {
        val root = pages.getValue(SettingsPageId.Root)
        val drawn = root.drawn { false }
        assertTrue(links(drawn).isEmpty())
        assertTrue(drawn.sections.all { it.items.isNotEmpty() })
    }

    @Test
    fun `without a profile the profile links are hidden`() {
        val root = settingsPages(SettingsValues(hasProfile = false)).getValue(SettingsPageId.Root).drawn { true }
        assertTrue(links(root).isEmpty())
    }

    @Test
    fun `row ids differ for the same key on two pages`() {
        val root = pages.getValue(SettingsPageId.Root).sections.flatMap { it.items }
        val quality = pages.getValue(SettingsPageId.Quality).sections.flatMap { it.items }
        val a = root.first { it.key == SettingKey.Quality }
        val b = quality.first { it.key == SettingKey.Quality }
        assertNotEquals(rowId(SettingsPageId.Root, a), rowId(SettingsPageId.Quality, b))
    }

    @Test
    fun `a choice names the option in use`() {
        val item = ChoiceItem(SettingKey.Quality, "Quality", value = 2, options = listOf(SettingOption(1, "One"), SettingOption(2, "Two")))
        assertEquals("Two", item.valueLabel())
        assertEquals(null, item.copy(value = 9).valueLabel())
    }

    @Test
    fun `the subtitle preview follows the page's own rows`() {
        val page = settingsPages(SettingsValues(hasProfile = true, captionsFollowSystem = false, subtitleSize = 3, subtitleBackground = 2)).getValue(SettingsPageId.Subtitles)
        val style = page.subtitlePreviewStyle()
        assertEquals(false, style.followSystem)
        assertEquals(0.09f, style.sizeFraction)
    }

    @Test
    fun `the Quality page is one titled section with its options inline and the footer`() {
        val page = pages.getValue(SettingsPageId.Quality).visibleOnly()
        val section = page.sections.single()
        assertEquals(Strings.qualityGroup, section.title)
        assertEquals(Strings.qualityFooter, section.footer)
        val choice = section.items.single() as ChoiceItem
        val checklist = choice.checklist()
        assertEquals(choice.options, checklist.map { it.first })
        assertEquals(1, checklist.count { it.second })
        assertEquals(choice.value, checklist.single { it.second }.first.value)
    }

    @Test
    fun `the checked option follows the chosen value`() {
        val page = settingsPages(SettingsValues(hasProfile = true, maxStreamingBitrate = ProfileSettings.QUALITY_CHOICES.last())).getValue(SettingsPageId.Quality)
        val checked = (page.sections.single().items.single() as ChoiceItem).checklist().single { it.second }
        assertEquals(ProfileSettings.QUALITY_CHOICES.last(), checked.first.value)
    }

    @Test
    fun `the subtitle preview is a section of its own above the toggle`() {
        val page = pages.getValue(SettingsPageId.Subtitles).visibleOnly()
        assertEquals(listOf(SettingKey.SubtitlePreview), page.sections[0].items.map { it.key })
        assertEquals(SettingKey.CaptionsFollowSystem, page.sections[1].items.first().key)
    }

    @Test
    fun `an unknown route id opens the root page`() {
        assertEquals(SettingsPageId.Root, pageIdOrRoot("Nope"))
        assertEquals(SettingsPageId.Skipping, pageIdOrRoot("Skipping"))
    }

    @Test
    fun `the selected page falls back to the first settable one when the saved id is not drawn`() {
        assertEquals(SettingsPageId.Quality, selectedPage(SettingsPageId.KidsAndPin, pages))
        assertEquals(SettingsPageId.Quality, selectedPage(null, pages))
        assertEquals(SettingsPageId.Quality, selectedPage(SettingsPageId.Root, pages))
        assertEquals(SettingsPageId.Subtitles, selectedPage(SettingsPageId.Subtitles, pages))
    }

    @Test
    fun `when two link rows target one page only the tapped one is selected`() {
        fun link(key: SettingKey) = LinkItem(key, key.name, target = SettingsPageId.Skipping)
        val custom = mapOf(
            SettingsPageId.Root to SettingsPage(
                SettingsPageId.Root, "Settings",
                listOf(SettingsSection(null, null, listOf(link(SettingKey.Skipping), link(SettingKey.Quality)))),
            ),
            SettingsPageId.Skipping to pages.getValue(SettingsPageId.Skipping),
        )
        val tapped = selectedLink(SettingKey.Quality, custom)
        assertEquals(SettingKey.Quality, tapped?.key)
        assertEquals(SettingsPageId.Skipping, tapped?.target)
        assertEquals(SettingKey.Skipping, selectedLink(SettingKey.Skipping, custom)?.key)
    }

    @Test
    fun `the default selects the first link row that targets the first settable page`() {
        val link = selectedLink(null, pages)
        assertEquals(SettingKey.Quality, link?.key)
        assertEquals(SettingsPageId.Quality, link?.target)
    }

    @Test
    fun `a saved key that is no longer drawn falls back to the default`() {
        assertEquals(SettingKey.Quality, selectedLink(SettingKey.KidsProfile, pages)?.key)
        assertEquals(SettingKey.Quality, selectedLink(SettingKey.AlwaysAsk, pages)?.key)
    }
}
