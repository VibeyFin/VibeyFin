package app.vibeyfin.ios

import app.vibeyfin.ui.search.SearchFilter
import app.vibeyfin.ui.search.SearchUiState
import app.vibeyfin.ui.search.SearchViewModel

/** The Search tab, as SwiftUI needs it. */
class SearchModel {
    private val host = ViewModelHost(SearchViewModel::class) {
        it.get<SearchViewModel>()
    }

    fun observe(onChange: (SearchUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onQueryChange(text: String) = host.viewModel.onQueryChange(text)

    fun onFilter(filter: SearchFilter) = host.viewModel.onFilter(filter)

    fun onClear() = host.viewModel.onClear()

    fun onRetry() = host.viewModel.onRetry()

    fun close() = host.close()
}
