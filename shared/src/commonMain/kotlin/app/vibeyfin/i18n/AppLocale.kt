package app.vibeyfin.i18n

import app.vibeyfin.data.platform.DeviceInfo
import app.vibeyfin.domain.DevicePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A language the app ships, as `languages.json` lists it: its tag and its own name. */
@Serializable
data class AppLanguage(val tag: String, val name: String)

fun shippedLanguages(source: StringSource): List<AppLanguage> {
    val file: String = source.loadLanguages() ?: return listOf(AppLanguage("en", "English"))
    return Json.decodeFromString<List<AppLanguage>>(file)
}

/** The shipped language for a choice and a device language: the choice, else the device's exact tag, else its base language, else English. */
fun resolveLanguage(choice: String?, deviceTag: String, shipped: List<AppLanguage>): String {
    fun shippedTag(tag: String): String? = shipped.firstOrNull { it.tag.equals(tag, ignoreCase = true) }?.tag
    val device: String = deviceTag.replace('_', '-')
    return choice?.let(::shippedTag) ?: shippedTag(device) ?: shippedTag(device.substringBefore('-')) ?: "en"
}

/**
 * The language the app shows: the saved choice (none is "Device language") over the device's. It writes the result to [Texts], so every
 * text and format follows, and exposes it as [language] for what has to draw again when it changes.
 */
class AppLocale(
    private val preferences: DevicePreferences,
    private val deviceInfo: DeviceInfo,
    val languages: List<AppLanguage>,
    private val select: (String) -> Unit = Texts::select,
    private val selected: StateFlow<String> = Texts.language,
) {
    private val deviceTag: MutableStateFlow<String> = MutableStateFlow(deviceInfo.languageTag)

    val language: StateFlow<String> get() = selected

    val choice: Flow<String?> get() = preferences.appLanguage

    init {
        // Read before the first frame, so a saved choice never shows English first.
        select(resolveLanguage(runBlocking { preferences.appLanguage.first() }, deviceTag.value, languages))
    }

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(preferences.appLanguage, deviceTag) { choice, device -> resolveLanguage(choice, device, languages) }
                .onEach(select)
                .collect {}
        }
    }

    /** The device's language changed (the OS language picker, or a new locale); the platform calls this. */
    fun deviceLanguageChanged() {
        deviceTag.value = deviceInfo.languageTag
    }

    suspend fun choose(tag: String?) {
        preferences.setAppLanguage(tag)
    }
}
