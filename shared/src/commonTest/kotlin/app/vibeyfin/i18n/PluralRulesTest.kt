package app.vibeyfin.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class PluralRulesTest {
    @Test
    fun englishAndGermanSeparateOneFromOther() {
        for (tag in listOf("en", "de")) {
            assertEquals("one", PluralRules.category(tag, 1), tag)
            for (count in listOf(0, 2, 3, 5, 11, 21, 100, 1_000_000)) {
                assertEquals("other", PluralRules.category(tag, count), "$tag $count")
            }
        }
    }

    @Test
    fun regionTagsUseTheirBaseLanguage() {
        assertEquals("one", PluralRules.category("de-AT", 1))
        assertEquals("one", PluralRules.category("en-GB", 1))
    }

    @Test
    fun onlyListedLanguagesAreSupported() {
        assertEquals(listOf(true, true, true, false), listOf("en", "de", "de-AT", "xx").map(PluralRules::supports))
    }

    @Test
    fun anUnknownLanguageFallsBackToEnglishRules() {
        assertEquals("one", PluralRules.category("xx", 1))
        assertEquals("other", PluralRules.category("xx", 2))
    }
}
