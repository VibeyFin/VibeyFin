package app.vibeyfin.i18n

import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

actual fun platformLocaleFormatter(tag: String): LocaleFormatter = JavaLocaleFormatter(Locale.forLanguageTag(tag))

private class JavaLocaleFormatter(private val locale: Locale) : LocaleFormatter {
    override fun integer(value: Long): String = NumberFormat.getIntegerInstance(locale).format(value)

    override fun decimal(value: Double, minimumFractionDigits: Int, maximumFractionDigits: Int): String {
        val format: NumberFormat = NumberFormat.getNumberInstance(locale)
        format.isGroupingUsed = false
        format.minimumFractionDigits = minimumFractionDigits
        format.maximumFractionDigits = maximumFractionDigits
        format.roundingMode = RoundingMode.HALF_UP
        return format.format(value)
    }

    override fun monthName(month: Int, abbreviated: Boolean): String = Month.of(month).getDisplayName(if (abbreviated) TextStyle.SHORT else TextStyle.FULL, locale)

    override fun time(minutesOfDay: Int): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(LocalTime.of(minutesOfDay / 60, minutesOfDay % 60))
}
