package app.vibeyfin.i18n

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val placeholder: Regex = Regex("""\{\{(\w+)\}\}""")

/** The texts of one language file, with English as the fallback. A missing key is empty text, never the key. */
class StringCatalog(private val source: StringSource, private val language: String = ENGLISH) {
    private val english: Map<String, String> by lazy { parse(source.load(ENGLISH)) }

    fun text(key: String, args: Map<String, String> = emptyMap()): String {
        val template: String = english[key] ?: return ""
        return placeholder.replace(template) { match -> args[match.groupValues[1]] ?: match.value }
    }

    fun plural(key: String, count: Int, args: Map<String, String> = emptyMap()): String {
        val form: String = PluralRules.category(language, count)
        val formKey: String = if ("${key}_$form" in english) "${key}_$form" else "${key}_other"
        return text(formKey, mapOf("count" to count.toString()) + args)
    }

    private fun parse(file: String?): Map<String, String> {
        val root: JsonObject = file?.let { Json.parseToJsonElement(it).jsonObject } ?: return emptyMap()
        return root.mapValues { entry -> entry.value.jsonPrimitive.content }
    }
}

private const val ENGLISH: String = "en"

object Texts {
    val catalog: StringCatalog by lazy { StringCatalog(platformStringSource()) }
}

internal fun text(key: String, vararg args: Pair<String, Any?>): String = Texts.catalog.text(key, args.associate { (name, value) -> name to value.toString() })

internal fun plural(key: String, count: Int, vararg args: Pair<String, Any?>): String = Texts.catalog.plural(key, count, args.associate { (name, value) -> name to value.toString() })
