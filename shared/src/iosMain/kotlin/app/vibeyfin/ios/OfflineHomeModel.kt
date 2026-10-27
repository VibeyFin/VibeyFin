package app.vibeyfin.ios

import app.vibeyfin.ui.home.OfflineHomeUiState
import app.vibeyfin.ui.home.OfflineHomeViewModel

/** Home with no server: [OfflineHomeViewModel] as SwiftUI needs it. */
class OfflineHomeModel {
    private val host = ViewModelHost(OfflineHomeViewModel::class) { it.get<OfflineHomeViewModel>() }

    fun observe(onChange: (OfflineHomeUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun close() = host.close()
}
