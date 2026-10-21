package app.vibeyfin.ios

import app.vibeyfin.ui.addserver.AddServerUiState
import app.vibeyfin.ui.addserver.AddServerViewModel
import org.koin.core.parameter.parametersOf

/**
 * The Add server screen's [AddServerViewModel] as SwiftUI needs it. A non-null [initialAddress] is
 * checked straight away, as when a server found on the network is tapped.
 */
class AddServerModel(initialAddress: String?) {
    private val host = ViewModelHost(AddServerViewModel::class) {
        it.get<AddServerViewModel> { parametersOf(initialAddress) }
    }

    fun observe(onChange: (AddServerUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onAddressChange(address: String) = host.viewModel.onAddressChange(address)

    fun onConnect() = host.viewModel.onConnect()

    fun onConfirm() = host.viewModel.onConfirm()

    fun close() = host.close()
}
