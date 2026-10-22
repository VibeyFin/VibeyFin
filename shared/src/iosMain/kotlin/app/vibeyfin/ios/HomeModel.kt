package app.vibeyfin.ios

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

    fun onRetryLibraries() = host.viewModel.onRetryLibraries()

    fun onRetryContinue() = host.viewModel.onRetryContinue()

    fun onMarkWatched(cardId: String) = host.viewModel.onMarkWatched(cardId)

    fun onRetryAction() = host.viewModel.onRetryAction()

    fun onDismissMessage() = host.viewModel.onDismissMessage()

    fun onRefresh() = host.viewModel.onRefresh()

    fun onForeground() = host.viewModel.onForeground()

    fun onCategory(kind: CategoryKind) = host.viewModel.onCategory(kind)

    fun onPickOption(id: String) = host.viewModel.onPickOption(id)

    fun onListOpened() = host.viewModel.onListOpened()

    fun onDismissDropdown() = host.viewModel.onDismissDropdown()

    fun onRetryDropdown() = host.viewModel.onRetryDropdown()

    fun close() = host.close()
}
