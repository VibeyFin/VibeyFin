package app.vibeyfin.ios

import app.vibeyfin.domain.Download
import app.vibeyfin.ui.downloads.DownloadAction
import app.vibeyfin.ui.downloads.DownloadedShow
import app.vibeyfin.ui.downloads.DownloadsUiState
import app.vibeyfin.ui.downloads.DownloadsViewModel

/** The Downloads tab's [DownloadsViewModel] as SwiftUI needs it. */
class DownloadsModel {
    private val host = ViewModelHost(DownloadsViewModel::class) { it.get<DownloadsViewModel>() }

    fun observe(onChange: (DownloadsUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onAction(download: Download, action: DownloadAction) = host.viewModel.onAction(download, action)

    fun onRemoveShow(show: DownloadedShow) = host.viewModel.onRemoveShow(show)

    fun onResumeShow(show: DownloadedShow) = host.viewModel.onResumeShow(show)

    fun onRemoveAll() = host.viewModel.onRemoveAll()

    fun onConfirmRemove() = host.viewModel.onConfirmRemove()

    fun onDismissRemoval() = host.viewModel.onDismissRemoval()

    fun onDismissMessage() = host.viewModel.onDismissMessage()

    fun close() = host.close()
}
