package app.vibeyfin.ios

import app.vibeyfin.ui.common.PinSheetControls
import app.vibeyfin.ui.downloads.DownloadAction
import app.vibeyfin.ui.downloads.RemovalControls
import app.vibeyfin.ui.home.CategoryKind
import app.vibeyfin.ui.home.HomeUiState
import app.vibeyfin.ui.home.HomeViewModel

/** The Home screen's [HomeViewModel] as SwiftUI needs it. */
class HomeModel :
    PinSheetModel,
    RemovalModel {
    private val host = ViewModelHost(HomeViewModel::class) { it.get<HomeViewModel>() }

    override val pinSheet: PinSheetControls get() = host.viewModel
    override val removal: RemovalControls get() = host.viewModel

    fun observe(onChange: (HomeUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onSwitchProfile() = host.viewModel.onSwitchProfile()

    fun onSwitchServer() = host.viewModel.onSwitchServer()

    fun onSignOut() = host.viewModel.onSignOut()

    fun onHeroPlay(itemId: String) = host.viewModel.onHeroPlay(itemId)

    fun onHeroNavHandled() = host.viewModel.onHeroNavHandled()

    fun onHeroMyList(itemId: String) = host.viewModel.onHeroMyList(itemId)

    fun onRetryContinue() = host.viewModel.onRetryContinue()

    fun onRetryRow(id: String) = host.viewModel.onRetryRow(id)

    fun onMarkWatched(cardId: String) = host.viewModel.onMarkWatched(cardId)

    fun onRemoveFromContinueWatching(cardId: String) = host.viewModel.onRemoveFromContinueWatching(cardId)

    fun onDownload(cardId: String) = host.viewModel.onDownload(cardId)

    fun onDownloadAction(cardId: String, action: DownloadAction) = host.viewModel.onDownloadAction(cardId, action)

    fun onRetryAction() = host.viewModel.onRetryAction()

    fun onDismissMessage() = host.viewModel.onDismissMessage()

    fun onRefresh() = host.viewModel.onRefresh()

    fun onForeground() = host.viewModel.onForeground()

    fun onCategory(kind: CategoryKind) = host.viewModel.onCategory(kind)

    fun onPickOption(id: String) = host.viewModel.onPickOption(id)

    fun onListOpened() = host.viewModel.onListOpened()

    fun onDismissSheet() = host.viewModel.onDismissSheet()

    fun onRetrySheet() = host.viewModel.onRetrySheet()

    fun close() = host.close()
}
