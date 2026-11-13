package app.vibeyfin.i18n

/** Reads the raw JSON of one language file (`en`, `de`, `pt-BR`); null when the file isn't there. */
interface StringSource {
    fun load(tag: String): String?

    /** The raw JSON of `languages.json`, the languages the app ships. */
    fun loadLanguages(): String?
}

/** Android reads the classpath, iOS the app bundle. */
expect fun platformStringSource(): StringSource
