package app.vibeyfin.ios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.mp.KoinPlatform
import kotlin.reflect.KClass

/**
 * Holds one shared ViewModel for a SwiftUI screen. It owns the ViewModel's store, so the
 * ViewModel lives exactly as long as the Swift object that holds the facade and [close] cancels its
 * work. Swift never sees this class; each screen has a small typed facade around it.
 */
internal class ViewModelHost<VM : ViewModel>(kClass: KClass<VM>, create: (Koin) -> VM) {
    private val store = ViewModelStore()
    val viewModel: VM = ViewModelProvider.create(
        store,
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
                create(KoinPlatform.getKoin()) as T
        },
    )[kClass]
    private var observing: Job? = null

    /** Calls [onChange] on the main thread with the current state now and with every change after. */
    fun <S> observe(state: StateFlow<S>, onChange: (S) -> Unit) {
        observing?.cancel()
        observing = viewModel.viewModelScope.launch { state.collect { onChange(it) } }
    }

    fun close() {
        observing?.cancel()
        store.clear()
    }
}
