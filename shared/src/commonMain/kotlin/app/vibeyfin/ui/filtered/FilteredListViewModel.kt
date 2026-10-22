package app.vibeyfin.ui.filtered

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.ui.children.ChildrenUiState
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.home.title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The page a category button or a dropdown entry opens: the movies and shows of one type, studio or genre, a
 * page at a time. It draws like a folder's list, so it shares [ChildrenUiState] with it.
 */
class FilteredListViewModel(
    private val filter: HomeFilter,
    private val library: LibraryRepository,
    connection: ConnectionManager,
) : ViewModel() {
    /** The name at the top of the page. */
    val title: String = filter.title

    private val content = MutableStateFlow(ChildrenUiState())

    val uiState: StateFlow<ChildrenUiState> = combine(content, connection.sessionEnded()) { content, exit ->
        content.copy(exit = exit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChildrenUiState())

    init {
        loadFirstPage()
    }

    /** Called as the end of the loaded items comes into view. */
    fun onLoadMore() {
        val current = content.value
        if (!current.hasMore || current.loading || current.loadingMore) return
        content.value = current.copy(loadingMore = true, loadMoreFailed = false)
        viewModelScope.launch {
            when (val result = library.filteredPage(filter, startIndex = current.items.size)) {
                is ApiResult.Success -> content.update {
                    val items = it.items + result.value.items
                    it.copy(
                        items = items,
                        hasMore = result.value.items.isNotEmpty() && items.size < result.value.total,
                        loadingMore = false,
                    )
                }
                is ApiResult.Failure -> content.update { it.copy(loadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    /** Tries again whatever failed: the first page, or the next one. */
    fun onRetry() {
        if (content.value.items.isEmpty()) loadFirstPage() else onLoadMore()
    }

    private fun loadFirstPage() {
        content.value = ChildrenUiState(loading = true)
        viewModelScope.launch {
            content.value = when (val result = library.filteredPage(filter, startIndex = 0)) {
                is ApiResult.Success -> ChildrenUiState(
                    items = result.value.items,
                    loading = false,
                    hasMore = result.value.hasMore,
                )
                is ApiResult.Failure -> ChildrenUiState(loading = false, message = LoadMessages.forError(result.error))
            }
        }
    }
}
