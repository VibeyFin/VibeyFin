package app.vibeyfin.i18n

actual fun platformStringSource(): StringSource = ClasspathStringSource

private object ClasspathStringSource : StringSource {
    override fun load(layer: String, tag: String): String? = read("strings/$layer/$tag.json")

    override fun loadLanguages(): String? = read("languages.json")

    private fun read(name: String): String? = javaClass.classLoader?.getResourceAsStream(name)?.use { it.readBytes().decodeToString() }
}
