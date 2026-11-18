package app.vibeyfin.i18n

interface StringSource {
    fun load(layer: String, tag: String): String?

    fun loadLanguages(): String?
}

/** Layers in override order: a later layer wins a duplicate key. Each client names the layers it loads. */
object StringLayers {
    const val COMMON: String = "common"
    const val MOBILE: String = "mobile"

    val mobileClient: List<String> = listOf(COMMON, MOBILE)
}

expect fun platformStringSource(): StringSource
