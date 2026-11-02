package app.vibeyfin.ios

import app.vibeyfin.ui.servers.ServerDetailsUiState
import app.vibeyfin.ui.servers.ServerDetailsViewModel
import org.koin.core.parameter.parametersOf

/** The Server details screen's [ServerDetailsViewModel] as SwiftUI needs it. */
class ServerDetailsModel(serverId: String) {
    private val host = ViewModelHost(ServerDetailsViewModel::class) {
        it.get<ServerDetailsViewModel> { parametersOf(serverId) }
    }

    fun observe(onChange: (ServerDetailsUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun removeAddress(address: String) = host.viewModel.onRemoveAddress(address)

    fun removeServer() = host.viewModel.onRemoveServer()

    fun confirmRemove() = host.viewModel.onConfirmRemove()

    fun dismissRemove() = host.viewModel.onDismissRemove()

    fun close() = host.close()
}
