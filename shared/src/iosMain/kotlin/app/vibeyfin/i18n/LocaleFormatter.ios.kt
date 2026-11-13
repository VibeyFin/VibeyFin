package app.vibeyfin.i18n

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneForSecondsFromGMT

actual fun platformLocaleFormatter(tag: String): LocaleFormatter = AppleLocaleFormatter(NSLocale(tag))

private class AppleLocaleFormatter(private val locale: NSLocale) : LocaleFormatter {
    override fun integer(value: Long): String = format(value.toDouble(), 0, 0, grouped = true)

    override fun decimal(value: Double, minimumFractionDigits: Int, maximumFractionDigits: Int): String = format(value, minimumFractionDigits, maximumFractionDigits, grouped = false)

    private fun format(value: Double, minimumFractionDigits: Int, maximumFractionDigits: Int, grouped: Boolean): String {
        val formatter: NSNumberFormatter = NSNumberFormatter()
        formatter.locale = locale
        formatter.numberStyle = NSNumberFormatterDecimalStyle
        formatter.usesGroupingSeparator = grouped
        formatter.minimumFractionDigits = minimumFractionDigits.toULong()
        formatter.maximumFractionDigits = maximumFractionDigits.toULong()
        formatter.roundingMode = NSNumberFormatterRoundHalfUp
        return formatter.stringFromNumber(NSNumber(value)).orEmpty()
    }

    override fun monthName(month: Int, abbreviated: Boolean): String {
        val formatter: NSDateFormatter = NSDateFormatter()
        formatter.locale = locale
        val symbols: List<*> = if (abbreviated) formatter.shortMonthSymbols else formatter.monthSymbols
        return symbols[month - 1] as String
    }

    override fun time(minutesOfDay: Int): String {
        val formatter: NSDateFormatter = NSDateFormatter()
        formatter.locale = locale
        formatter.timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
        formatter.dateStyle = NSDateFormatterNoStyle
        formatter.timeStyle = NSDateFormatterShortStyle
        return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(minutesOfDay * SECONDS_PER_MINUTE))
    }
}

private const val SECONDS_PER_MINUTE: Double = 60.0
