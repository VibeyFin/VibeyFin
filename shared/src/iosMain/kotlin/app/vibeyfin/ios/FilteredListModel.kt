package app.vibeyfin.ios

import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.ui.filtered.FilteredListUiState
import app.vibeyfin.ui.filtered.FilteredListViewModel
import app.vibeyfin.ui.filtered.ListChipKind
import org.koin.core.parameter.parametersOf

/** The page a category button or a dropdown entry opens, as SwiftUI needs it. Its list state is a folder list's, with the buttons and picker above it. */
class FilteredListModel(filter: HomeFilter) {
    private val host = ViewModelHost(FilteredListViewModel::class) {
        it.get<FilteredListViewModel> { parametersOf(filter) }
    }

    /** The name at the top of the page. */
    val title: String get() = host.viewModel.title

    /** The state now, so the page's first frame already has its buttons and its loading grid. */
    val state: FilteredListUiState get() = host.viewModel.uiState.value

    fun observe(onChange: (FilteredListUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onLoadMore() = host.viewModel.onLoadMore()

    fun onRetry() = host.viewModel.onRetry()

    fun onChip(kind: ListChipKind) = host.viewModel.onChip(kind)

    fun onSheetQuery(text: String) = host.viewModel.onSheetQuery(text)

    fun onPick(id: String) = host.viewModel.onPick(id)

    fun onDismissSheet() = host.viewModel.onDismissSheet()

    fun onRetrySheet() = host.viewModel.onRetrySheet()

    fun close() = host.close()
}
