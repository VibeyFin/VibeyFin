package app.vibeyfin.ui.filtered

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.FilterOption
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.PageSort
import app.vibeyfin.domain.defaultSort
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.children.ChildrenUiState
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.home.title
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The buttons under a category page's title. */
enum class ListChipKind { Sort, Year, Genre }

/** One of them: [label] already says what is chosen ("Sort: Rating"), and [active] is true when it narrows or reorders the page. */
data class ListChip(val kind: ListChipKind, val label: String, val active: Boolean)

/** The picker a chip opens: loading, its rows, or why they didn't load. [selectedId] is the row in use; an empty id is "All". */
data class ListSheet(
    val kind: ListChipKind,
    val title: String,
    val options: List<FilterOption> = emptyList(),
    val selectedId: String = "",
    val loading: Boolean = false,
    val message: String? = null,
)

/** A category page: the list (a folder's list state), the buttons above it and the picker one of them opened. */
data class FilteredListUiState(
    val list: ChildrenUiState = ChildrenUiState(),
    val chips: List<ListChip> = emptyList(),
    val sheet: ListSheet? = null,
)

/** What the page is ordered and narrowed by; the page starts at the defaults. */
private data class Selection(
    val sort: PageSort,
    val genre: FilterOption? = null,
    val year: Int? = null,
)

/**
 * The page a category button or the Categories sheet opens: the movies and shows of one type or genre, a page at a
 * time. Sort, release year and (except on a genre's own page) genre can be changed from the buttons above the list;
 * a page ordered by year names each year above its titles.
 */
class FilteredListViewModel(
    private val filter: HomeFilter,
    private val library: LibraryRepository,
    connection: ConnectionManager,
) : ViewModel() {
    /** The name at the top of the page. */
    val title: String = filter.title

    private val content = MutableStateFlow(ChildrenUiState())
    private val selection = MutableStateFlow(Selection(sort = filter.defaultSort))
    private val sheet = MutableStateFlow<ListSheet?>(null)
    private var years: List<Int>? = null
    private var genres: List<FilterOption>? = null
    private var sheetJob: Job? = null
    private var pageJob: Job? = null

    val uiState: StateFlow<FilteredListUiState> = combine(content, selection, sheet, connection.sessionEnded()) { content, selection, sheet, exit ->
        FilteredListUiState(content.copy(exit = exit), chipsFor(selection), sheet)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilteredListUiState())

    init {
        loadFirstPage()
    }

    /** Called as the end of the loaded items comes into view. */
    fun onLoadMore() {
        val current = content.value
        if (!current.hasMore || current.loading || current.loadingMore) return
        content.value = current.copy(loadingMore = true, loadMoreFailed = false)
        val asked = selection.value
        viewModelScope.launch {
            val result = page(asked, startIndex = current.items.size)
            // The page was reordered or narrowed while this was out: the new first page answers instead.
            if (selection.value != asked) return@launch
            when (result) {
                is ApiResult.Success -> content.update {
                    val items = it.items + result.value.items
                    it.copy(
                        items = items,
                        headings = headingsFor(items, asked.sort),
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

    /** A button above the list was tapped: its picker opens. */
    fun onChip(kind: ListChipKind) {
        val current = selection.value
        when (kind) {
            ListChipKind.Sort -> sheet.value = ListSheet(
                kind = kind,
                title = Strings.sortTitle,
                options = PageSort.entries.map { FilterOption(it.name, sortLabel(it)) },
                selectedId = current.sort.name,
            )
            ListChipKind.Year -> openLoaded(kind, Strings.yearTitle, years != null, current.year?.toString().orEmpty(), Strings.allYears) {
                when (val result = library.years()) {
                    is ApiResult.Success -> {
                        years = result.value
                        ApiResult.Success(result.value.map { FilterOption(it.toString(), it.toString()) })
                    }
                    is ApiResult.Failure -> result
                }
            }
            ListChipKind.Genre -> openLoaded(kind, Strings.genreTitle, genres != null, current.genre?.id.orEmpty(), Strings.allGenres) {
                library.genres().also { if (it is ApiResult.Success) genres = it.value }
            }
        }
    }

    /** A row of the open picker was chosen: the page reloads in that order or narrowed that way. */
    fun onPick(id: String) {
        val open = sheet.value ?: return
        sheet.value = null
        sheetJob?.cancel()
        val current = selection.value
        val next = when (open.kind) {
            ListChipKind.Sort -> current.copy(sort = PageSort.entries.firstOrNull { it.name == id } ?: return)
            ListChipKind.Year -> current.copy(year = id.toIntOrNull())
            ListChipKind.Genre -> current.copy(genre = genres?.firstOrNull { it.id == id })
        }
        if (next == current) return
        selection.value = next
        loadFirstPage()
    }

    fun onDismissSheet() {
        sheetJob?.cancel()
        sheet.value = null
    }

    /** "Try again" in the picker. */
    fun onRetrySheet() {
        val open = sheet.value ?: return
        sheet.value = open.copy(loading = true, message = null)
        onChip(open.kind)
    }

    private fun openLoaded(
        kind: ListChipKind,
        title: String,
        cached: Boolean,
        selectedId: String,
        allLabel: String,
        load: suspend () -> ApiResult<List<FilterOption>>,
    ) {
        sheetJob?.cancel()
        fun rows(options: List<FilterOption>) = listOf(FilterOption("", allLabel)) + options
        if (cached) {
            sheet.value = ListSheet(kind, title, rows(optionsFor(kind)), selectedId)
            return
        }
        sheet.value = ListSheet(kind, title, loading = true, selectedId = selectedId)
        sheetJob = viewModelScope.launch {
            val result = load()
            sheet.update { open ->
                open?.takeIf { it.kind == kind }?.let {
                    when (result) {
                        is ApiResult.Success -> it.copy(options = rows(result.value), loading = false, message = null)
                        is ApiResult.Failure -> it.copy(loading = false, message = LoadMessages.forError(result.error))
                    }
                }
            }
        }
    }

    private fun optionsFor(kind: ListChipKind): List<FilterOption> = when (kind) {
        ListChipKind.Year -> years.orEmpty().map { FilterOption(it.toString(), it.toString()) }
        ListChipKind.Genre -> genres.orEmpty()
        ListChipKind.Sort -> emptyList()
    }

    private fun chipsFor(selection: Selection): List<ListChip> = buildList {
        add(ListChip(ListChipKind.Sort, Strings.sortChip(sortLabel(selection.sort)), active = selection.sort != filter.defaultSort))
        add(ListChip(ListChipKind.Year, selection.year?.let { Strings.yearChip(it.toString()) } ?: Strings.yearButton, active = selection.year != null))
        // A genre's own page is already one genre.
        if (filter !is HomeFilter.Genre) {
            add(ListChip(ListChipKind.Genre, selection.genre?.let { Strings.genreChip(it.name) } ?: Strings.genreButton, active = selection.genre != null))
        }
    }

    private fun sortLabel(sort: PageSort) = when (sort) {
        PageSort.Name -> Strings.sortName
        PageSort.Year -> Strings.sortYear
        PageSort.Rating -> Strings.sortRating
    }

    private suspend fun page(asked: Selection, startIndex: Int) =
        library.filteredPage(filter, asked.sort, asked.genre?.id, asked.year, startIndex = startIndex)

    private fun loadFirstPage() {
        content.value = ChildrenUiState(loading = true)
        pageJob?.cancel()
        val asked = selection.value
        pageJob = viewModelScope.launch {
            val result = page(asked, startIndex = 0)
            if (selection.value != asked) return@launch
            content.value = when (result) {
                is ApiResult.Success -> ChildrenUiState(
                    items = result.value.items,
                    headings = headingsFor(result.value.items, asked.sort),
                    loading = false,
                    hasMore = result.value.hasMore,
                )
                is ApiResult.Failure -> ChildrenUiState(loading = false, message = LoadMessages.forError(result.error))
            }
        }
    }

    /** A page ordered by release year names each year above its titles; the other orders are a plain list. */
    private fun headingsFor(items: List<MediaItem>, sort: PageSort): Map<String, String> {
        if (sort != PageSort.Year) return emptyMap()
        val headings = linkedMapOf<String, String>()
        var shown: String? = null
        for (item in items) {
            val label = item.year?.toString() ?: Strings.unknownYear
            if (label != shown) headings[item.id] = label
            shown = label
        }
        return headings
    }
}
