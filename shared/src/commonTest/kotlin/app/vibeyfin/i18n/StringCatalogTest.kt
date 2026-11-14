package app.vibeyfin.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

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

    private val catalog: StringCatalog = StringCatalog(FakeStringSource(mapOf("en" to englishFile)))

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
        assertEquals("", StringCatalog(FakeStringSource(emptyMap())).text("hello"))
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

    private val withGerman: StringCatalog = StringCatalog(FakeStringSource(mapOf("en" to englishFile.replace("\"hello\": \"Hello\",", "\"hello\": \"Hello\", \"blank\": \"Blank\", \"only_english\": \"Only English\","), "de" to germanFile)))

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
        val broken: StringCatalog = StringCatalog(FakeStringSource(mapOf("en" to englishFile, "de" to "{ not json")))
        broken.select("de")
        assertEquals("en", broken.language.value)
        assertEquals("Hello", broken.text("hello"))
    }

    @Test
    fun aMissingSingularFormReadsEnglishSingularNotTheLanguagePlural() {
        val onlyPlural: StringCatalog = StringCatalog(FakeStringSource(mapOf("en" to englishFile, "de" to """{ "titles_other": "{{count}} Titel" }""")))
        onlyPlural.select("de")
        assertEquals("1 title", onlyPlural.plural("titles", 1))
        assertEquals("2 Titel", onlyPlural.plural("titles", 2))
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
    override fun load(tag: String): String? = files[tag]

    override fun loadLanguages(): String? = languages
}
