package app.vibeyfin.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsSelectionTest {
    @Test
    fun `the first settable page is Quality for a normal profile`() {
        assertEquals(SettingsPageId.Quality, firstSettablePage(settingsPages(SettingsValues(hasProfile = true))))
    }

    @Test
    fun `a profile with no Playback items selects the next page that has something`() {
        val pages = settingsPages(SettingsValues(hasProfile = true)).let { all ->
            val quality = all.getValue(SettingsPageId.Quality)
            all + (SettingsPageId.Quality to quality.copy(sections = emptyList()))
        }
        assertEquals(SettingsPageId.AudioSubtitles, firstSettablePage(pages))
    }

    @Test
    fun `nothing selectable returns null`() {
        assertNull(firstSettablePage(settingsPages(SettingsValues(hasProfile = false))))
    }
}
