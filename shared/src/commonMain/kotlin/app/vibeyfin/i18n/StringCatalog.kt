package app.vibeyfin.i18n

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.Volatile

private const val ENGLISH: String = "en"

private val placeholder: Regex = Regex("""\{\{(\w+)\}\}""")

private class ActiveLanguage(val tag: String, val table: Map<String, String>)

/**
 * The texts of the active language over English: a key the active language lacks, or leaves empty, is read from English, and a key
 * English lacks is empty text, never the key.
 */
class StringCatalog(private val source: StringSource) {
    private val english: Map<String, String> by lazy { parse(source.load(ENGLISH)) }

    @Volatile
    private var active: ActiveLanguage = ActiveLanguage(ENGLISH, emptyMap())

    private val activeTag: MutableStateFlow<String> = MutableStateFlow(ENGLISH)

    val language: StateFlow<String> get() = activeTag

    fun select(tag: String) {
        val table: Map<String, String> = when (tag) {
            ENGLISH -> emptyMap()
            PseudoLocale.TAG -> english.mapValues { PseudoLocale.apply(it.value) }
            else -> parse(source.load(tag))
        }
        active = ActiveLanguage(tag, table)
        activeTag.value = tag
    }

    fun text(key: String, args: Map<String, String> = emptyMap()): String = fill(template(active.table[key], english[key]), args)

    fun plural(key: String, count: Int, args: Map<String, String> = emptyMap()): String {
        val current: ActiveLanguage = active
        val template: String = template(pluralForm(current.table, current.tag, key, count), pluralForm(english, ENGLISH, key, count))
        return fill(template, mapOf("count" to count.toString()) + args)
    }

    private fun pluralForm(table: Map<String, String>, tag: String, key: String, count: Int): String? = table["${key}_${PluralRules.category(tag, count)}"]?.takeIf { it.isNotEmpty() } ?: table["${key}_other"]

    private fun template(translated: String?, fallback: String?): String = translated?.takeIf { it.isNotEmpty() } ?: fallback.orEmpty()

    private fun fill(template: String, args: Map<String, String>): String = placeholder.replace(template) { match -> args[match.groupValues[1]] ?: match.value }

    private fun parse(file: String?): Map<String, String> {
        if (file == null) return emptyMap()
        return try {
            Json.parseToJsonElement(file).jsonObject.mapValues { entry -> entry.value.jsonPrimitive.content }
        } catch (_: SerializationException) {
            emptyMap()
        } catch (_: IllegalArgumentException) {
            emptyMap()
        }
    }
}

object Texts {
    private var replacement: StringCatalog? = null
    private var readHook: (() -> Unit)? = null
    private val platformCatalog: StringCatalog by lazy { StringCatalog(platformStringSource()) }

    val catalog: StringCatalog get() = replacement ?: platformCatalog

    val language: StateFlow<String> get() = catalog.language

    /**
     * Called on every text and format lookup. A UI toolkit that tracks what a screen reads (Compose, SwiftUI) points it at one observable
     * language value, so a screen that reads [Strings] while drawing is drawn again when the language changes.
     */
    fun onRead(hook: (() -> Unit)?) {
        readHook = hook
    }

    internal fun noteRead() {
        readHook?.invoke()
    }

    fun select(tag: String) {
        catalog.select(tag)
    }

    /** For tests: reads from [catalog] until [reset]. */
    fun install(catalog: StringCatalog) {
        replacement = catalog
    }

    fun reset() {
        replacement = null
        platformCatalog.select(ENGLISH)
    }
}

internal fun text(key: String, vararg args: Pair<String, Any?>): String {
    Texts.noteRead()
    return Texts.catalog.text(key, args.associate { (name, value) -> name to value.toString() })
}

internal fun plural(key: String, count: Int, vararg args: Pair<String, Any?>): String {
    Texts.noteRead()
    return Texts.catalog.plural(key, count, args.associate { (name, value) -> name to value.toString() })
}
