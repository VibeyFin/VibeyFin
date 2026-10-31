package app.vibeyfin.ios

import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.ui.seerr.MyRequestsUiState
import app.vibeyfin.ui.seerr.MyRequestsViewModel
import app.vibeyfin.ui.seerr.RequestFilter
import app.vibeyfin.ui.seerr.SeerrHomeUiState
import app.vibeyfin.ui.seerr.SeerrHomeViewModel
import app.vibeyfin.ui.seerr.SeerrListKind
import app.vibeyfin.ui.seerr.SeerrListUiState
import app.vibeyfin.ui.seerr.SeerrListViewModel
import org.koin.core.parameter.parametersOf

/** Seerr's rows on Home (My requests and the discover rows), as SwiftUI needs them. */
class SeerrHomeModel {
    private val host = ViewModelHost(SeerrHomeViewModel::class) { it.get<SeerrHomeViewModel>() }

    fun observe(onChange: (SeerrHomeUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onRefresh() = host.viewModel.onRefresh()

    fun onForeground() = host.viewModel.onForeground()

    fun close() = host.close()
}

/** A discover list's See all page. */
class SeerrListModel(kind: SeerrListKind) {
    private val host = ViewModelHost(SeerrListViewModel::class) { it.get<SeerrListViewModel> { parametersOf(kind) } }

    fun observe(onChange: (SeerrListUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onRetry() = host.viewModel.onRetry()

    fun onLoadMore() = host.viewModel.onLoadMore()

    fun onTypeFilter(type: SeerrMediaType?) = host.viewModel.onTypeFilter(type)

    fun close() = host.close()
}

/** The My requests page. */
class MyRequestsModel {
    private val host = ViewModelHost(MyRequestsViewModel::class) { it.get<MyRequestsViewModel>() }

    fun observe(onChange: (MyRequestsUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onFilter(filter: RequestFilter) = host.viewModel.onFilter(filter)

    fun onLoadMore() = host.viewModel.onLoadMore()

    fun onRetry() = host.viewModel.onRetry()

    fun close() = host.close()
}
