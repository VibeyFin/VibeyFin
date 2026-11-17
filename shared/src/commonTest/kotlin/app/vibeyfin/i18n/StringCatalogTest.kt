package app.vibeyfin.i18n

import app.vibeyfin.ui.common.Strings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringCatalogTest {
    private val englishFile: String = """
        {
          "hello": "Hello",
          "greeting": "Hi {{name}}, you have {{unread}} messages",
          "titles_one": "{{count}} title",
          "titles_other": "{{count}} titles",
          "only_other_other": "{{count}} things"
        }
    """.trimIndent()

    private val catalog: StringCatalog = StringCatalog(FakeStringSource(mapOf("common/en" to englishFile)), listOf("common"))

    @Test
    fun returnsPlainText() {
        assertEquals("Hello", catalog.text("hello"))
    }

    @Test
    fun fillsPlaceholdersInOnePass() {
        assertEquals("Hi {{unread}}, you have 3 messages", catalog.text("greeting", mapOf("name" to "{{unread}}", "unread" to "3")))
    }

    @Test
    fun picksPluralFormAndFillsCount() {
        assertEquals("1 title", catalog.plural("titles", 1))
        assertEquals("0 titles", catalog.plural("titles", 0))
        assertEquals("2 titles", catalog.plural("titles", 2))
    }

    @Test
    fun pluralFallsBackToOtherWhenTheFormIsMissing() {
        assertEquals("1 things", catalog.plural("only_other", 1))
    }

    @Test
    fun missingKeyIsEmptyNeverTheKey() {
        assertEquals("", catalog.text("nope"))
        assertEquals("", catalog.plural("nope", 2))
    }

    @Test
    fun missingEnglishFileIsEmptyNeverTheKey() {
        assertEquals("", StringCatalog(FakeStringSource(emptyMap()), listOf("common")).text("hello"))
    }

    private val germanFile: String = """
        {
          "hello": "Hallo",
          "greeting": "Hallo {{name}}, du hast {{unread}} Nachrichten",
          "titles_one": "{{count}} Titel",
          "titles_other": "{{count}} Titel",
          "blank": ""
        }
    """.trimIndent()

    private val withGerman: StringCatalog = StringCatalog(FakeStringSource(mapOf("common/en" to englishFile.replace("\"hello\": \"Hello\",", "\"hello\": \"Hello\", \"blank\": \"Blank\", \"only_english\": \"Only English\","), "common/de" to germanFile)), listOf("common"))

    @Test
    fun theSelectedLanguageSuppliesTheText() {
        withGerman.select("de")
        assertEquals("Hallo", withGerman.text("hello"))
        assertEquals("Hallo Ann, du hast 4 Nachrichten", withGerman.text("greeting", mapOf("name" to "Ann", "unread" to "4")))
        assertEquals("2 Titel", withGerman.plural("titles", 2))
        assertEquals("de", withGerman.language.value)
    }

    @Test
    fun aKeyTheLanguageLacksOrLeavesEmptyReadsEnglish() {
        withGerman.select("de")
        assertEquals("Only English", withGerman.text("only_english"))
        assertEquals("Blank", withGerman.text("blank"))
    }

    @Test
    fun aLanguageWithoutAFileReadsEnglish() {
        withGerman.select("fr")
        assertEquals("en", withGerman.language.value)
        assertEquals("Hello", withGerman.text("hello"))
        assertEquals("2 titles", withGerman.plural("titles", 2))
    }

    @Test
    fun aBrokenLanguageFileReadsEnglish() {
        val broken: StringCatalog = StringCatalog(FakeStringSource(mapOf("common/en" to englishFile, "common/de" to "{ not json")), listOf("common"))
        broken.select("de")
        assertEquals("en", broken.language.value)
        assertEquals("Hello", broken.text("hello"))
    }

    @Test
    fun aMissingSingularFormReadsEnglishSingularNotTheLanguagePlural() {
        val onlyPlural: StringCatalog = StringCatalog(FakeStringSource(mapOf("common/en" to englishFile, "common/de" to """{ "titles_other": "{{count}} Titel" }""")), listOf("common"))
        onlyPlural.select("de")
        assertEquals("1 title", onlyPlural.plural("titles", 1))
        assertEquals("2 Titel", onlyPlural.plural("titles", 2))
    }

    private val layered: Map<String, String> = mapOf(
        "common/en" to """{ "hello": "Hello", "shared": "Shared", "gone": "Common only", "titles_one": "{{count}} title", "titles_other": "{{count}} titles" }""",
        "mobile/en" to """{ "hello": "Hello phone", "swipe": "Swipe {{name}}", "clips_one": "{{count}} clip", "clips_other": "{{count}} clips" }""",
        "common/de" to """{ "hello": "Hallo", "shared": "Geteilt", "titles_one": "{{count}} Titel", "titles_other": "{{count}} Titel" }""",
        "mobile/de" to """{ "swipe": "Wische {{name}}", "clips_one": "{{count}} Clip", "clips_other": "{{count}} Clips" }""",
    )

    private val layeredCatalog: StringCatalog = StringCatalog(FakeStringSource(layered), listOf("common", "mobile"))

    @Test
    fun aLaterLayerOverridesAnEarlierOne() {
        assertEquals("Hello phone", layeredCatalog.text("hello"))
    }

    @Test
    fun aKeyOnlyInAnEarlierLayerIsStillFound() {
        assertEquals("Common only", layeredCatalog.text("gone"))
        assertEquals("Shared", layeredCatalog.text("shared"))
    }

    @Test
    fun theLayerOrderDecidesTheWinner() {
        assertEquals("Hello", StringCatalog(FakeStringSource(layered), listOf("mobile", "common")).text("hello"))
    }

    @Test
    fun aLayerTheClientDoesNotLoadIsNotShown() {
        assertEquals("", StringCatalog(FakeStringSource(layered), listOf("common")).text("swipe"))
    }

    @Test
    fun aMissingKeyInTheActiveLanguageReadsEnglishAcrossLayers() {
        layeredCatalog.select("de")
        assertEquals("Wische Ann", layeredCatalog.text("swipe", mapOf("name" to "Ann")))
        assertEquals("Geteilt", layeredCatalog.text("shared"))
        assertEquals("Common only", layeredCatalog.text("gone"))
        assertEquals("Hallo", layeredCatalog.text("hello"))
    }

    @Test
    fun pluralFormsAndPlaceholdersWorkAcrossLayers() {
        assertEquals("1 clip", layeredCatalog.plural("clips", 1))
        assertEquals("3 titles", layeredCatalog.plural("titles", 3))
        assertEquals("Swipe Ann", layeredCatalog.text("swipe", mapOf("name" to "Ann")))
        layeredCatalog.select("de")
        assertEquals("2 Clips", layeredCatalog.plural("clips", 2))
        assertEquals("1 Titel", layeredCatalog.plural("titles", 1))
    }

    @Test
    fun anUnknownLayerLoadsAsEmpty() {
        val catalog: StringCatalog = StringCatalog(FakeStringSource(layered), listOf("common", "tv"))
        assertEquals("Hello", catalog.text("hello"))
        assertEquals("", catalog.text("swipe"))
    }

    @Test
    fun theLanguageSwitchFindsNoFileWhenNoLayerHasIt() {
        layeredCatalog.select("fr")
        assertEquals("en", layeredCatalog.language.value)
    }

    @Test
    fun aClientThatLoadsOnlyCommonDoesNotShowMobileText() {
        Texts.useLayers(listOf(StringLayers.COMMON))
        assertEquals("", Strings.downloadedTitle("Heat"))
        assertTrue(Strings.tryAgain.isNotEmpty())
        Texts.reset()
        assertEquals("Downloaded Heat", Strings.downloadedTitle("Heat"))
    }

    @Test
    fun resetClearsTheReadHook() {
        var reads: Int = 0
        Texts.onRead { reads++ }
        Texts.reset()
        text("hello")
        assertEquals(0, reads)
    }

    @Test
    fun switchingBackToEnglishRestoresEnglish() {
        withGerman.select("de")
        withGerman.select("en")
        assertEquals("Hello", withGerman.text("hello"))
    }
}

class FakeStringSource(private val files: Map<String, String>, private val languages: String? = null) : StringSource {
    override fun load(layer: String, tag: String): String? = files["$layer/$tag"]

    override fun loadLanguages(): String? = languages
}
