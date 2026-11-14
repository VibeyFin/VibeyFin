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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class AppLanguage(val tag: String, val name: String)

fun shippedLanguages(source: StringSource): List<AppLanguage> {
    val file: String = source.loadLanguages() ?: return listOf(AppLanguage(DEFAULT_LANGUAGE, "English"))
    return Json.decodeFromString<List<AppLanguage>>(file)
}

// The choice, else the device's exact tag, else its base language, else English.
fun resolveLanguage(choice: String?, deviceTag: String, shipped: List<AppLanguage>): String {
    val device: String = deviceTag.replace('_', '-')
    val shippedTag: (String) -> String? = { tag -> shipped.firstOrNull { it.tag.equals(tag, ignoreCase = true) }?.tag }

    return choice?.let(shippedTag)
        ?: shippedTag(device)
        ?: shippedTag(device.substringBefore('-'))
        ?: DEFAULT_LANGUAGE
}

class AppLocale(
    private val preferences: DevicePreferences,
    private val deviceInfo: DeviceInfo,
    val languages: List<AppLanguage>,
    private val select: (String) -> Unit = Texts::select,
    private val selected: StateFlow<String> = Texts.language,
    private val startupReadTimeout: Duration = 2.seconds,
) {
    private val deviceTag: MutableStateFlow<String> = MutableStateFlow(deviceInfo.languageTag)

    val language: StateFlow<String> get() = selected

    val choice: Flow<String?> get() = preferences.appLanguage

    init {
        // Read before the first frame, so a saved choice never shows English first.
        val saved: String? = runBlocking { withTimeoutOrNull(startupReadTimeout) { preferences.appLanguage.first() } }
        select(resolveLanguage(saved, deviceTag.value, languages))
    }

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(preferences.appLanguage, deviceTag) { choice, device -> resolveLanguage(choice, device, languages) }
                .onEach(select)
                .collect {}
        }
    }

    fun deviceLanguageChanged() {
        deviceTag.value = deviceInfo.languageTag
    }

    suspend fun choose(tag: String?) {
        preferences.setAppLanguage(tag)
    }
}
