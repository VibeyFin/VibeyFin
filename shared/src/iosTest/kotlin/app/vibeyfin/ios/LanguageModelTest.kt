package app.vibeyfin.ios

import app.vibeyfin.i18n.Texts
import app.vibeyfin.ui.common.Strings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageModelTest {
    @AfterTest
    fun tearDown() {
        Texts.reset()
    }

    @Test
    fun `the read hook runs on every text lookup and stops when removed`() {
        var reads = 0

        Texts.onRead { reads += 1 }
        Strings.cancel
        Texts.onRead(null)
        Strings.cancel

        assertEquals(1, reads)
    }
}
