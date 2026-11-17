package app.vibeyfin.i18n

/** A debug-only language made from English: accented, about a third longer and in brackets, so clipped and hard-coded text stands out. */
object PseudoLocale {
    const val TAG: String = "en-XA"

    val language: AppLanguage = AppLanguage(TAG, "Pseudo (debug)")

    private const val GROWTH_PERCENT: Int = 35

    private val placeholder: Regex = Regex("""\{\{\w+\}\}""")

    private val accents: Map<Char, Char> = mapOf(
        'a' to 'á', 'e' to 'é', 'i' to 'í', 'o' to 'ö', 'u' to 'ü', 'c' to 'ç', 'n' to 'ñ', 'y' to 'ý',
        'A' to 'Å', 'E' to 'É', 'I' to 'Î', 'O' to 'Ö', 'U' to 'Û', 'C' to 'Ç', 'N' to 'Ñ', 'Y' to 'Ý',
    )

    fun apply(text: String): String {
        if (text.isEmpty()) return text
        val accented: String = accent(text)
        val padding: String = "~".repeat(maxOf(1, text.length * GROWTH_PERCENT / 100))
        return "[$accented $padding]"
    }

    private fun accent(text: String): String {
        val builder = StringBuilder()
        var from = 0
        placeholder.findAll(text).forEach { match ->
            builder.append(text.substring(from, match.range.first).map { accents[it] ?: it }.joinToString(""))
            builder.append(match.value)
            from = match.range.last + 1
        }
        builder.append(text.substring(from).map { accents[it] ?: it }.joinToString(""))
        return builder.toString()
    }
}
