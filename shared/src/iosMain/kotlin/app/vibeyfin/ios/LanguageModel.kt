package app.vibeyfin.ios

import app.vibeyfin.di.ApplicationScope
import app.vibeyfin.i18n.AppLocale
import app.vibeyfin.i18n.Texts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

class LanguageModel {
    private val locale: AppLocale = KoinPlatform.getKoin().get<AppLocale>()
    private val scope: CoroutineScope = KoinPlatform.getKoin().get<CoroutineScope>(ApplicationScope)
    private var observing: Job? = null

    /** Calls [onChange] on the main thread with the current language tag now and with every change after. */
    fun observe(onChange: (String) -> Unit) {
        observing?.cancel()
        observing = scope.launch(Dispatchers.Main) { locale.language.collect { onChange(it) } }
    }

    fun onRead(hook: () -> Unit) {
        Texts.onRead(hook)
    }

    fun deviceLanguageChanged() {
        locale.deviceLanguageChanged()
    }

    fun close() {
        observing?.cancel()
        Texts.onRead(null)
    }
}
