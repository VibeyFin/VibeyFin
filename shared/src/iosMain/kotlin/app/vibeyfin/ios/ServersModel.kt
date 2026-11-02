package app.vibeyfin.ios

import app.vibeyfin.ui.servers.ServersUiState
import app.vibeyfin.ui.servers.ServersViewModel

/** The Servers screen's [ServersViewModel] as SwiftUI needs it: typed callbacks instead of flows. */
class ServersModel {
    private val host = ViewModelHost(ServersViewModel::class) { it.get<ServersViewModel>() }

    fun observe(onChange: (ServersUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun searchAgain() = host.viewModel.onSearchAgain()

    fun removeServer(serverId: String) = host.viewModel.onRemoveServer(serverId)

    fun confirmRemove() = host.viewModel.onConfirmRemove()

    fun dismissRemove() = host.viewModel.onDismissRemove()

    fun close() = host.close()
}
