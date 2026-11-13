package app.vibeyfin.app.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.vibeyfin.i18n.Texts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Makes every composable that reads [app.vibeyfin.ui.common.Strings] draw again when the app language changes, without recreating the activity. */
object LanguageRefresh {
    private var currentLanguage: String by mutableStateOf(Texts.language.value)

    fun start(scope: CoroutineScope) {
        Texts.onRead { currentLanguage }
        scope.launch {
            Texts.language.collect { currentLanguage = it }
        }
    }
}
