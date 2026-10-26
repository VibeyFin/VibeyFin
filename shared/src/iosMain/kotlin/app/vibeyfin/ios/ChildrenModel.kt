package app.vibeyfin.ios

import app.vibeyfin.ui.children.ChildrenUiState
import app.vibeyfin.ui.children.ChildrenViewModel
import org.koin.core.parameter.parametersOf

/** What is inside the library, folder, series or season [parentId], as SwiftUI needs it. */
class ChildrenModel(parentId: String) {
    private val host = ViewModelHost(ChildrenViewModel::class) {
        it.get<ChildrenViewModel> { parametersOf(parentId) }
    }

    fun observe(onChange: (ChildrenUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onLoadMore() = host.viewModel.onLoadMore()

    fun onRetry() = host.viewModel.onRetry()

    // Downloading the season, when the list is a season's.

    fun onDownloadSeason() = host.viewModel.onDownloadSeason()

    fun onRemoveSeasonDownloads() = host.viewModel.onRemoveSeasonDownloads()

    fun onToggleUnwatchedOnly() = host.viewModel.onToggleUnwatchedOnly()

    fun onConfirmDownload() = host.viewModel.onConfirmDownload()

    fun onDismissDownloadPrompt() = host.viewModel.onDismissDownloadPrompt()

    fun onConfirmRemove() = host.viewModel.onConfirmRemove()

    fun onDismissRemoval() = host.viewModel.onDismissRemoval()

    fun onRetryDownload() = host.viewModel.onRetryDownload()

    fun onDismissDownloadMessage() = host.viewModel.onDismissDownloadMessage()

    fun close() = host.close()
}
