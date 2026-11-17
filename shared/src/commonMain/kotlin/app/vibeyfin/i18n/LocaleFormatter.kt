package app.vibeyfin.i18n

import kotlinx.coroutines.flow.MutableStateFlow

interface LocaleFormatter {
    fun integer(value: Long): String

    fun decimal(value: Double, minimumFractionDigits: Int, maximumFractionDigits: Int): String

    fun monthName(month: Int, abbreviated: Boolean): String

    fun time(minutesOfDay: Int): String
}

expect fun platformLocaleFormatter(tag: String): LocaleFormatter

object Formats {
    private val cached: MutableStateFlow<Pair<String, LocaleFormatter>?> = MutableStateFlow(null)

    val current: LocaleFormatter
        get() {
            Texts.noteRead()
            val tag: String = Texts.language.value
            cached.value?.takeIf { it.first == tag }?.let { return it.second }
            return platformLocaleFormatter(tag).also { cached.value = tag to it }
        }
}
