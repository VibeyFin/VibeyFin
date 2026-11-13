package app.vibeyfin.i18n

/** CLDR plural categories; Kotlin/Native has no CLDR, so a language that needs more than one/other gets its own branch here. */
object PluralRules {
    fun category(tag: String, count: Int): String = when (tag.substringBefore('-')) {
        "de", "en" -> oneOrOther(count)
        else -> oneOrOther(count)
    }

    private fun oneOrOther(count: Int): String = if (count == 1) "one" else "other"
}
