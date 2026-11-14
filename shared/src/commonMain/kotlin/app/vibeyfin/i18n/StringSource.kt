package app.vibeyfin.i18n

interface StringSource {
    fun load(tag: String): String?

    fun loadLanguages(): String?
}

expect fun platformStringSource(): StringSource
