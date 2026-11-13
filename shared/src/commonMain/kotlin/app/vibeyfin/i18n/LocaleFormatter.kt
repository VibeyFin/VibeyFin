package app.vibeyfin.i18n

/** Numbers, month names and clock times as a language writes them. Rounding stays in the caller, so only the separators and names change. */
interface LocaleFormatter {
    fun integer(value: Long): String

    fun decimal(value: Double, minimumFractionDigits: Int, maximumFractionDigits: Int): String

    fun monthName(month: Int, abbreviated: Boolean): String

    fun time(minutesOfDay: Int): String
}

expect fun platformLocaleFormatter(tag: String): LocaleFormatter

object Formats {
    val current: LocaleFormatter by lazy { platformLocaleFormatter("en") }
}
