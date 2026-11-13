package app.vibeyfin.i18n

import app.vibeyfin.ui.common.Strings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PseudoLocaleTest {
    @AfterTest
    fun tearDown() {
        Texts.reset()
    }

    @Test
    fun `text is accented and grown and bracketed with placeholders untouched`() {
        val pseudo: String = PseudoLocale.apply("Hello {{name}}")

        assertTrue(pseudo.startsWith("[Héllö {{name}} ~"), pseudo)
        assertTrue(pseudo.endsWith("]"), pseudo)
        assertTrue(pseudo.length > "Hello {{name}}".length * 13 / 10)
    }

    @Test
    fun `empty text stays empty`() {
        assertEquals("", PseudoLocale.apply(""))
    }

    @Test
    fun `selecting the pseudo language changes every text and plural`() {
        Texts.select(PseudoLocale.TAG)

        assertTrue(Strings.cancel.startsWith("[Çáñçél"), Strings.cancel)
        assertTrue(Strings.removeConfirmTitle(count = 2, title = "X").startsWith("["), Strings.removeConfirmTitle(count = 2, title = "X"))
    }
}
