package app.vibeyfin.i18n

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationFilesTest {
    private val placeholderPattern: Regex = Regex("""\{\{(\w+)\}\}""")
    private val pluralSuffix: Regex = Regex("_(one|other)$")

    private val layers: List<String> = File("strings").listFiles { file -> file.isDirectory }.orEmpty().map { it.name }.sorted()

    private fun load(layer: String, tag: String): Map<String, String> = Json.parseToJsonElement(File("strings/$layer/$tag.json").readText())
        .jsonObject.mapValues { it.value.jsonPrimitive.content }

    private fun load(tag: String): Map<String, String> = layers.fold(emptyMap()) { table, layer -> table + load(layer, tag) }

    private val english: Map<String, String> = load(DEFAULT_LANGUAGE)
    private val translations: Map<String, Map<String, String>> = Json.decodeFromString<List<AppLanguage>>(File("languages.json").readText())
        .map { it.tag }
        .filter { it != DEFAULT_LANGUAGE }
        .associateWith(::load)

    private fun placeholders(text: String): Set<String> = placeholderPattern.findAll(text).map { it.groupValues[1] }.toSet()

    private fun pluralForms(texts: Map<String, String>): Map<String, Set<String>> = texts.keys
        .filter { pluralSuffix.containsMatchIn(it) }
        .groupBy({ pluralSuffix.replace(it, "") }, { pluralSuffix.find(it)!!.groupValues[1] })
        .mapValues { it.value.toSet() }

    private fun forEachTranslation(check: (tag: String, texts: Map<String, String>) -> Unit) = translations.forEach { (tag, texts) -> check(tag, texts) }

    @Test
    fun everyLayerHasAFileForEveryShippedLanguage() {
        val missing: List<String> = layers.flatMap { layer -> (translations.keys + DEFAULT_LANGUAGE).filter { !File("strings/$layer/$it.json").exists() }.map { "$layer/$it" } }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun everyShippedLanguageHasAPluralRule() {
        assertEquals(emptyList(), translations.keys.filterNot(PluralRules::supports))
    }

    @Test
    fun everyShippedLanguageHasExactlyTheKeysEnglishHas() = forEachTranslation { tag, texts ->
        assertEquals(emptyList(), (english.keys - texts.keys).toList(), "$tag lacks keys")
        assertEquals(emptyList(), (texts.keys - english.keys).toList(), "$tag has extra keys")
    }

    @Test
    fun everyTextKeepsThePlaceholdersOfItsEnglishText() = forEachTranslation { tag, texts ->
        val changed: List<String> = english.keys.filter { placeholders(english.getValue(it)) != placeholders(texts.getValue(it)) }
        assertEquals(emptyList(), changed, "$tag changes placeholders")
    }

    @Test
    fun noTextIsEmptyWhereEnglishHasText() = forEachTranslation { tag, texts ->
        assertEquals(emptyList(), english.keys.filter { english.getValue(it).isNotEmpty() && texts.getValue(it).isEmpty() }, "$tag has empty texts")
    }

    @Test
    fun everyLanguageHasTheSamePluralFormsAsEnglish() = forEachTranslation { tag, texts ->
        assertEquals(pluralForms(english), pluralForms(texts), "$tag plural forms differ")
    }
}
