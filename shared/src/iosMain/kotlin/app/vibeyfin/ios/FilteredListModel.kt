package app.vibeyfin.ios

import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.ui.children.ChildrenUiState
import app.vibeyfin.ui.filtered.FilteredListViewModel
import org.koin.core.parameter.parametersOf

/** The page a category button or a dropdown entry opens, as SwiftUI needs it. Its state is a folder list's. */
class FilteredListModel(filter: HomeFilter) {
    private val host = ViewModelHost(FilteredListViewModel::class) {
        it.get<FilteredListViewModel> { parametersOf(filter) }
    }

    /** The name at the top of the page. */
    val title: String get() = host.viewModel.title

    fun observe(onChange: (ChildrenUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onLoadMore() = host.viewModel.onLoadMore()

    fun onRetry() = host.viewModel.onRetry()

    fun close() = host.close()
}
