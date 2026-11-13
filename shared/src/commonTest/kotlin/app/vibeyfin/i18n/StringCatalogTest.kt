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
}

class FakeStringSource(private val files: Map<String, String>) : StringSource {
    override fun load(tag: String): String? = files[tag]
}
