package app.vibeyfin.ios

import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.downloads.DownloadAction
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

    fun onDownload() = host.viewModel.onDownload()

    fun onDownloadAction(action: DownloadAction) = host.viewModel.onDownloadAction(action)

    fun onToggleUnwatchedOnly() = host.viewModel.onToggleUnwatchedOnly()

    fun onConfirmDownload() = host.viewModel.onConfirmDownload()

    fun onDismissDownloadPrompt() = host.viewModel.onDismissDownloadPrompt()

    fun onConfirmRemove() = host.viewModel.onConfirmRemove()

    fun onDismissRemoval() = host.viewModel.onDismissRemoval()

    fun close() = host.close()
}
