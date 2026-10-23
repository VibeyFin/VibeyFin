package app.vibeyfin.ios

import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.tile.TileMenuUiState
import app.vibeyfin.ui.tile.TileMenuViewModel

/** The long-press menu of a poster, as SwiftUI needs it. */
class TileMenuModel {
    private val host = ViewModelHost(TileMenuViewModel::class) { it.get<TileMenuViewModel>() }

    fun observe(onChange: (TileMenuUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onOpen(item: MediaItem) = host.viewModel.onOpen(item)

    fun onClose() = host.viewModel.onClose()

    fun onToggleFavorite() = host.viewModel.onToggleFavorite()

    fun onDismissMessage() = host.viewModel.onDismissMessage()

    fun close() = host.close()
}
