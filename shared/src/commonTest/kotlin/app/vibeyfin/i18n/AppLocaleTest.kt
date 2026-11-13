package app.vibeyfin.i18n

import app.vibeyfin.testing.FakeDeviceInfo
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLocaleTest {
    private val shipped: List<AppLanguage> = listOf(AppLanguage("en", "English"), AppLanguage("de", "Deutsch"), AppLanguage("pt-BR", "Português (Brasil)"))
    private val app = TestApp()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @BeforeTest
    fun setUp() {
        TestLanguages.installGerman("try_again" to "Erneut versuchen")
    }

    @AfterTest
    fun tearDown() = runTest {
        scope.cancel()
        TestLanguages.reset()
        app.close()
    }

    private fun appLocale(deviceTag: String): AppLocale = AppLocale(app.preferences, FakeDeviceInfo(language = deviceTag.substringBefore('-'), languageTag = deviceTag), shipped)

    @Test
    fun `the device language is used while nothing is chosen`() {
        assertEquals("de", resolveLanguage(null, "de-AT", shipped))
        assertEquals("pt-BR", resolveLanguage(null, "pt-BR", shipped))
        assertEquals("pt-BR", resolveLanguage(null, "pt_br", shipped))
    }

    @Test
    fun `a device language that is not shipped shows English`() {
        assertEquals("en", resolveLanguage(null, "fr-FR", shipped))
        assertEquals("en", resolveLanguage(null, "", shipped))
    }

    @Test
    fun `a base language is used when only its regional variant ships`() {
        assertEquals("pt-BR", resolveLanguage("pt-BR", "en-US", shipped))
        assertEquals("en", resolveLanguage(null, "pt-PT", shipped))
    }

    @Test
    fun `a choice wins over the device language and one that no longer ships is ignored`() {
        assertEquals("en", resolveLanguage("en", "de-DE", shipped))
        assertEquals("de", resolveLanguage("fr", "de-DE", shipped))
    }

    @Test
    fun `the saved choice is applied before the first frame`() = runTest {
        app.preferences.setAppLanguage("de")

        val locale = appLocale("en-US")

        assertEquals("de", locale.language.value)
        assertEquals("Erneut versuchen", Strings.tryAgain)
    }

    @Test
    fun `choosing a language changes every text at once and Device language goes back`() = runTest {
        val locale = appLocale("en-US")
        locale.start(scope)
        assertEquals("Try again", Strings.tryAgain)

        locale.choose("de")
        locale.language.first { it == "de" }
        assertEquals("Erneut versuchen", Strings.tryAgain)

        locale.choose(null)
        locale.language.first { it == "en" }
        assertEquals("Try again", Strings.tryAgain)
    }

    @Test
    fun `a device language change is picked up`() = runTest {
        var deviceTag = "en-US"
        val device = object : app.vibeyfin.data.platform.DeviceInfo {
            override val deviceName: String = "Test"
            override val appVersion: String = "1"
            override val language: String get() = deviceTag.substringBefore('-')
            override val languageTag: String get() = deviceTag
        }
        val locale = AppLocale(app.preferences, device, shipped)
        locale.start(scope)

        deviceTag = "de-DE"
        locale.deviceLanguageChanged()

        assertEquals("de", locale.language.first { it == "de" })
    }

    @Test
    fun `the languages file lists what ships`() {
        val languages = shippedLanguages(FakeStringSource(emptyMap(), languages = """[{"tag":"en","name":"English"},{"tag":"de","name":"Deutsch"}]"""))

        assertEquals(listOf(AppLanguage("en", "English"), AppLanguage("de", "Deutsch")), languages)
        assertEquals(listOf(AppLanguage("en", "English")), shippedLanguages(FakeStringSource(emptyMap())))
    }
}
