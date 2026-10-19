package com.liamgleeson.vibeyfin.ios

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.liamgleeson.vibeyfin.ui.servers.ServersUiState
import com.liamgleeson.vibeyfin.ui.servers.ServersViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * The Servers screen's [ServersViewModel] as SwiftUI needs it: typed callbacks instead of flows,
 * and a [close] that cancels the screen's work. Owns its own store so the ViewModel lives exactly
 * as long as the Swift object that holds this.
 */
class ServersModel {
    private val store = ViewModelStore()
    private val viewModel: ServersViewModel = ViewModelProvider.create(
        store,
        viewModelFactory { initializer { KoinPlatform.getKoin().get<ServersViewModel>() } },
    )[ServersViewModel::class]
    private var observing: Job? = null

    /** Calls [onChange] on the main thread with the current state now and with every change after. */
    fun observe(onChange: (ServersUiState) -> Unit) {
        observing?.cancel()
        observing = viewModel.viewModelScope.launch {
            viewModel.uiState.collect { onChange(it) }
        }
    }

    fun searchAgain() = viewModel.onSearchAgain()

    fun close() {
        observing?.cancel()
        store.clear()
    }
}
