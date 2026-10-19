package com.liamgleeson.vibeyfin.ios

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.liamgleeson.vibeyfin.ui.addserver.AddServerUiState
import com.liamgleeson.vibeyfin.ui.addserver.AddServerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform

/**
 * The Add server screen's [AddServerViewModel] as SwiftUI needs it. A non-null [initialAddress] is
 * checked straight away, as when a server found on the network is tapped. See [ServersModel].
 */
class AddServerModel(initialAddress: String?) {
    private val store = ViewModelStore()
    private val viewModel: AddServerViewModel = ViewModelProvider.create(
        store,
        viewModelFactory {
            initializer { KoinPlatform.getKoin().get<AddServerViewModel> { parametersOf(initialAddress) } }
        },
    )[AddServerViewModel::class]
    private var observing: Job? = null

    /** Calls [onChange] on the main thread with the current state now and with every change after. */
    fun observe(onChange: (AddServerUiState) -> Unit) {
        observing?.cancel()
        observing = viewModel.viewModelScope.launch {
            viewModel.uiState.collect { onChange(it) }
        }
    }

    fun onAddressChange(address: String) = viewModel.onAddressChange(address)

    fun onConnect() = viewModel.onConnect()

    fun onConfirm() = viewModel.onConfirm()

    fun close() {
        observing?.cancel()
        store.clear()
    }
}
