package app.vibeyfin.testing

import app.vibeyfin.i18n.StringCatalog
import app.vibeyfin.i18n.StringLayers
import app.vibeyfin.i18n.StringSource
import app.vibeyfin.i18n.Texts
import app.vibeyfin.i18n.platformStringSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Gives the tests a German over the real English. Call [reset] in `@AfterTest`: the language is global and the other tests pin English. */
object TestLanguages {
    fun installGerman(vararg texts: Pair<String, String>) {
        val real: StringSource = platformStringSource()
        val german: String = JsonObject(texts.associate { (key, value) -> key to JsonPrimitive(value) }).toString()
        Texts.install(
            StringCatalog(
                object : StringSource {
                    override fun load(layer: String, tag: String): String? = if (tag == "de") german.takeIf { layer == StringLayers.COMMON } else real.load(layer, tag)

                    override fun loadLanguages(): String? = real.loadLanguages()
                },
                StringLayers.mobileClient,
            ),
        )
    }

    fun reset() {
        Texts.reset()
    }
}
