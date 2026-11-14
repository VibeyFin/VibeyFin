package app.vibeyfin.i18n

/** CLDR plural categories; Kotlin/Native has no CLDR, so each shipped language is listed here with its own rule. */
object PluralRules {
    private val rules: Map<String, (Int) -> String> = mapOf(
        "en" to ::oneOrOther,
        "de" to ::oneOrOther,
    )

    fun supports(tag: String): Boolean = baseLanguage(tag) in rules

    fun category(tag: String, count: Int): String = (rules[baseLanguage(tag)] ?: ::oneOrOther)(count)

    private fun baseLanguage(tag: String): String = tag.substringBefore('-')

    private fun oneOrOther(count: Int): String = if (count == 1) "one" else "other"
}
