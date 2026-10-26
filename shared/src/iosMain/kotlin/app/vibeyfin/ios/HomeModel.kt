package app.vibeyfin.ios

import app.vibeyfin.ui.downloads.DownloadAction
import app.vibeyfin.ui.home.CategoryKind
import app.vibeyfin.ui.home.HomeUiState
import app.vibeyfin.ui.home.HomeViewModel

/** The Home screen's [HomeViewModel] as SwiftUI needs it. */
class HomeModel {
    private val host = ViewModelHost(HomeViewModel::class) { it.get<HomeViewModel>() }

    fun observe(onChange: (HomeUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onSwitchProfile() = host.viewModel.onSwitchProfile()

    fun onSwitchServer() = host.viewModel.onSwitchServer()

    fun onSignOut() = host.viewModel.onSignOut()

    /** A PIN was entered in the open PIN sheet. */
    fun onSubmitPin(pin: String) = host.viewModel.onSubmitPin(pin)

    /** "Forgot PIN?": the PIN sheet asks for the profile's Jellyfin password instead. */
    fun onForgotPin() = host.viewModel.onForgotPin()

    fun onDismissPin() = host.viewModel.onDismissPin()

    /** The Jellyfin password entered after "Forgot PIN?". */
    fun onSubmitPassword(password: String) = host.viewModel.onSubmitPassword(password)

    fun onRetryLibraries() = host.viewModel.onRetryLibraries()

    fun onHeroPlay() = host.viewModel.onHeroPlay()

    fun onHeroNavHandled() = host.viewModel.onHeroNavHandled()

    fun onHeroMyList() = host.viewModel.onHeroMyList()

    fun onRetryContinue() = host.viewModel.onRetryContinue()

    fun onRetryRow(id: String) = host.viewModel.onRetryRow(id)

    fun onMarkWatched(cardId: String) = host.viewModel.onMarkWatched(cardId)

    fun onDownload(cardId: String) = host.viewModel.onDownload(cardId)

    fun onDownloadAction(cardId: String, action: DownloadAction) = host.viewModel.onDownloadAction(cardId, action)

    fun onConfirmRemove() = host.viewModel.onConfirmRemove()

    fun onDismissRemoval() = host.viewModel.onDismissRemoval()

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
