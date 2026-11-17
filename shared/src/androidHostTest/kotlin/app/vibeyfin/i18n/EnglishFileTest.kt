package app.vibeyfin.i18n

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class EnglishFileTest {
    private val callPattern: Regex = Regex("""\b(text|plural)\("([a-z0-9_]+)"""")
    private val argumentPattern: Regex = Regex(""""(\w+)"\s+to\s""")
    private val placeholderPattern: Regex = Regex("""\{\{(\w+)\}\}""")
    private val pluralSuffixes: Set<String> = setOf("_one", "_other")

    private val layers: List<File> = File("strings").listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }

    private val englishByLayer: Map<String, Map<String, String>> = layers.associate { layer ->
        layer.name to Json.parseToJsonElement(File(layer, "en.json").readText()).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }

    private val english: Map<String, String> = englishByLayer.values.fold(emptyMap()) { table, layer -> table + layer }

    private val calls: List<Call> = File("src").walkTopDown()
        .filter { it.extension == "kt" && "Test" !in it.path && "/i18n/" !in it.path }
        .flatMap { file -> file.readLines().flatMap(::callsOnLine) }
        .toList()

    private fun callsOnLine(line: String): List<Call> = callPattern.findAll(line).map { start ->
        Call(start.groupValues[1], start.groupValues[2], argumentPattern.findAll(line).map { it.groupValues[1] }.toSet())
    }.toList()

    private data class Call(val kind: String, val key: String, val arguments: Set<String>)

    private fun keyOf(call: Call, form: String): String = if (call.kind == "plural") "${call.key}_$form" else call.key

    private fun baseKey(key: String): String = pluralSuffixes.firstOrNull { key.endsWith(it) }?.let { key.removeSuffix(it) } ?: key

    @Test
    fun everyKeyUsedInCodeExistsInEnglish() {
        val missing: List<String> = calls.filter { keyOf(it, "other") !in english }.map { it.key }.distinct()
        assertEquals(emptyList(), missing)
    }

    @Test
    fun everyLayerHasAnEnglishFileWithKeys() {
        assertEquals(emptyList(), englishByLayer.filterValues { it.isEmpty() }.keys.toList())
    }

    @Test
    fun everyEnglishKeyIsUsedInCode() {
        val used: Set<String> = calls.map { it.key }.toSet()
        val unused: List<String> = englishByLayer.flatMap { (layer, texts) -> texts.keys.filter { baseKey(it) !in used && it !in used }.map { "$layer/$it" } }
        assertEquals(emptyList(), unused)
    }

    @Test
    fun noTextKeyEndsLikeAPluralForm() {
        val clashing: List<String> = calls.filter { it.kind == "text" && baseKey(it.key) != it.key }.map { it.key }.distinct()
        assertEquals(emptyList(), clashing)
    }

    @Test
    fun aKeyInTwoLayersIsAnOverrideWithDifferentText() {
        val copies: List<String> = englishByLayer.entries.toList().let { entries ->
            entries.flatMapIndexed { index, (layer, texts) ->
                entries.drop(index + 1).flatMap { (otherLayer, other) ->
                    texts.keys.intersect(other.keys).filter { texts.getValue(it) == other.getValue(it) }.map { "$it in $layer and $otherLayer" }
                }
            }
        }
        assertEquals(emptyList(), copies)
    }

    @Test
    fun pluralKeysHaveBothForms() {
        val incomplete: List<String> = calls.filter { it.kind == "plural" }.map { it.key }.distinct()
            .filter { "${it}_one" !in english || "${it}_other" !in english }
        assertEquals(emptyList(), incomplete)
    }

    @Test
    fun everyPlaceholderIsPassed() {
        val mismatches: List<String> = calls.flatMap { call ->
            val forms: List<String> = if (call.kind == "plural") listOf("one", "other") else listOf("")
            forms.mapNotNull { form ->
                val key: String = if (form.isEmpty()) call.key else "${call.key}_$form"
                val implicit: Set<String> = if (call.kind == "plural") setOf("count") else emptySet()
                val needed: Set<String> = placeholderPattern.findAll(english[key].orEmpty()).map { it.groupValues[1] }.toSet() - implicit
                if (call.arguments.containsAll(needed)) null else "$key needs $needed but the line passes ${call.arguments}"
            }
        }.distinct()
        assertEquals(emptyList(), mismatches)
    }

    @Test
    fun noMessagePropertyHoldsALiteral() {
        val literalProperty: Regex = Regex("""^\s+(private )?(const )?val \w+(: String)? = ".*"""")
        val files: List<String> = listOf("Strings", "LoadMessages", "SignInMessages", "SeerrMessages")
        val offenders: List<String> = File("src/commonMain").walkTopDown()
            .filter { it.nameWithoutExtension in files }
            .flatMap { file -> file.readLines().filter { literalProperty.containsMatchIn(it) && "appName" !in it }.map { "${file.name}: ${it.trim()}" } }
            .toList()
        assertEquals(emptyList(), offenders)
    }
}
