package com.liamgleeson.vibeyfin.ios

import com.liamgleeson.vibeyfin.ui.servers.ServersUiState
import com.liamgleeson.vibeyfin.ui.servers.ServersViewModel

/** The Servers screen's [ServersViewModel] as SwiftUI needs it: typed callbacks instead of flows. */
class ServersModel {
    private val host = ViewModelHost(ServersViewModel::class) { it.get<ServersViewModel>() }

    fun observe(onChange: (ServersUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun searchAgain() = host.viewModel.onSearchAgain()

    fun close() = host.close()
}
