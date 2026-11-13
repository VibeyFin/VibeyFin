package app.vibeyfin.i18n

actual fun platformStringSource(): StringSource = ClasspathStringSource

private object ClasspathStringSource : StringSource {
    override fun load(tag: String): String? = javaClass.classLoader?.getResourceAsStream("strings/$tag.json")?.use { it.readBytes().decodeToString() }
}
