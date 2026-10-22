package app.vibeyfin.ui.children

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.home.HomeExit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildrenUiState(
    val items: List<MediaItem> = emptyList(),
    /** The first page is on its way. */
    val loading: Boolean = true,
    /** Why the first page didn't load, with what to try. */
    val message: String? = null,
    /** The list is longer than what is loaded; ask for the next page with `onLoadMore`. */
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    /** The next page didn't load; the items so far stay, and `onRetry` asks again. */
    val loadMoreFailed: Boolean = false,
    /** On a season's list: the show it belongs to, so the page can lead back to it. Null for anything else. */
    val series: SeriesLink? = null,
    /** Set when the session ended and the screen must be left. */
    val exit: HomeExit? = null,
) {
    val isEmpty: Boolean get() = !loading && message == null && items.isEmpty()
}

/** The show a season belongs to. */
data class SeriesLink(val id: String, val name: String)

/** What is inside one library, folder, series or season, a page at a time. */
class ChildrenViewModel(
    private val parentId: String,
    private val library: LibraryRepository,
    connection: ConnectionManager,
) : ViewModel() {
    private val content = MutableStateFlow(ChildrenUiState())

    val uiState: StateFlow<ChildrenUiState> = combine(content, connection.sessionEnded()) { content, exit ->
        content.copy(exit = exit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChildrenUiState())

    init {
        loadFirstPage()
        findSeries()
    }

    /** A season's list offers a way to its show; for any other folder the lookup finds nothing and the page is as it was. */
    private fun findSeries() {
        viewModelScope.launch {
            val parent = (library.item(parentId) as? ApiResult.Success)?.value ?: return@launch
            val seriesId = parent.seriesId ?: return@launch
            if (parent.kind != ItemKind.Season) return@launch
            val link = SeriesLink(seriesId, parent.seriesName ?: parent.title)
            content.update { it.copy(series = link) }
        }
    }

    /** Called as the end of the loaded items comes into view. */
    fun onLoadMore() {
        val current = content.value
        if (!current.hasMore || current.loading || current.loadingMore) return
        content.value = current.copy(loadingMore = true, loadMoreFailed = false)
        viewModelScope.launch {
            when (val result = library.children(parentId, startIndex = current.items.size)) {
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
        val series = content.value.series
        content.value = ChildrenUiState(loading = true, series = series)
        viewModelScope.launch {
            content.value = when (val result = library.children(parentId, startIndex = 0)) {
                is ApiResult.Success -> ChildrenUiState(
                    items = result.value.items,
                    loading = false,
                    hasMore = result.value.hasMore,
                    series = content.value.series,
                )
                is ApiResult.Failure -> ChildrenUiState(loading = false, message = LoadMessages.forError(result.error), series = content.value.series)
            }
        }
    }
}
