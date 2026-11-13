package app.vibeyfin.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleFormatterTest {
    private val english: LocaleFormatter = platformLocaleFormatter("en")
    private val german: LocaleFormatter = platformLocaleFormatter("de")

    @Test
    fun integersAreGroupedPerLocale() {
        assertEquals("1,284", english.integer(1284))
        assertEquals("1.284", german.integer(1284))
        assertEquals("12", english.integer(12))
    }

    @Test
    fun decimalsKeepTheRequestedFractionDigits() {
        assertEquals("7.5", english.decimal(7.5, 1, 1))
        assertEquals("8.0", english.decimal(8.0, 1, 1))
        assertEquals("4", english.decimal(4.0, 0, 2))
        assertEquals("4.55", english.decimal(4.55, 0, 2))
        assertEquals("1.40", english.decimal(1.4, 2, 2))
        assertEquals("7,5", german.decimal(7.5, 1, 1))
        assertEquals("1200,5", german.decimal(1200.5, 0, 1))
    }

    @Test
    fun decimalsAreNeverGrouped() {
        assertEquals("1000.5", english.decimal(1000.5, 1, 1))
    }

    @Test
    fun monthNamesFollowTheLanguage() {
        assertEquals("December", english.monthName(12, abbreviated = false))
        assertEquals("Dec", english.monthName(12, abbreviated = true))
        assertEquals("May", english.monthName(5, abbreviated = true))
        assertEquals("Sep", english.monthName(9, abbreviated = true))
        assertEquals("Dezember", german.monthName(12, abbreviated = false))
        assertEquals("März", german.monthName(3, abbreviated = false))
    }

    @Test
    fun clockTimesFollowTheLanguage() {
        assertEquals("10:00 PM", english.time(22 * 60).withPlainSpaces())
        assertEquals("8:30 AM", english.time(8 * 60 + 30).withPlainSpaces())
        assertEquals("22:00", german.time(22 * 60))
        assertEquals("08:30", german.time(8 * 60 + 30))
    }

    private fun String.withPlainSpaces(): String = replace('\u202f', ' ').replace('\u00a0', ' ')
}
