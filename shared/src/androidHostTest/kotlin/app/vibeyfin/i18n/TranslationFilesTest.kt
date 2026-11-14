package app.vibeyfin.i18n

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class GermanFileTest {
    private val placeholderPattern: Regex = Regex("""\{\{(\w+)\}\}""")

    private fun load(tag: String): Map<String, String> = Json.parseToJsonElement(File("strings/$tag.json").readText())
        .jsonObject.mapValues { it.value.jsonPrimitive.content }

    private val english: Map<String, String> = load("en")
    private val german: Map<String, String> = load("de")

    private fun placeholders(text: String): Set<String> = placeholderPattern.findAll(text).map { it.groupValues[1] }.toSet()

    @Test
    fun germanHasExactlyTheKeysEnglishHas() {
        assertEquals(emptyList(), (english.keys - german.keys).toList())
        assertEquals(emptyList(), (german.keys - english.keys).toList())
    }

    @Test
    fun everyGermanTextKeepsThePlaceholdersOfItsEnglishText() {
        val changed: List<String> = english.keys.filter { placeholders(english.getValue(it)) != placeholders(german.getValue(it)) }
        assertEquals(emptyList(), changed)
    }

    @Test
    fun noGermanTextIsEmptyWhereEnglishHasText() {
        assertEquals(emptyList(), english.keys.filter { english.getValue(it).isNotEmpty() && german.getValue(it).isEmpty() })
    }
}
