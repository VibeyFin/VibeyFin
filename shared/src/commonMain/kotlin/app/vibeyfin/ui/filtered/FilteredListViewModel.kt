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
enum class ListChipKind { Sort, Genre }

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
    /** Every release year in the library, newest first, for the strip above a list ordered by year; empty for other orders. */
    val years: List<String> = emptyList(),
    /** The year the user tapped in the strip, until the list has scrolled to it; the screen then calls [FilteredListViewModel.onJumped]. */
    val jumpTo: String? = null,
)

/** What the page is ordered and narrowed by; the page starts at the defaults. */
private data class Selection(
    val sort: PageSort,
    val genre: FilterOption? = null,
)

/**
 * The page a category button or the Categories sheet opens: the movies and shows of one type or genre, a page at a
 * time. Sort and (except on a genre's own page) genre can be changed from the buttons above the list;
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
    private val allYears = MutableStateFlow<List<String>>(emptyList())
    private val jumpTo = MutableStateFlow<String?>(null)
    // The years of each selection (by genre id, empty for none), found once: the page's own items decide, so a genre has its own.
    private val yearsBySelection = mutableMapOf<String, List<String>>()
    private var yearsJob: Job? = null
    private var jumpJob: Job? = null
    private var genres: List<FilterOption>? = null
    private var sheetJob: Job? = null
    private var pageJob: Job? = null

    private val screen = combine(content, selection, sheet, connection.sessionEnded()) { content, selection, sheet, exit ->
        FilteredListUiState(list = content.copy(exit = exit), chips = chipsFor(selection), sheet = sheet)
    }

    val uiState: StateFlow<FilteredListUiState> = combine(screen, selection, allYears, jumpTo) { screen, selection, years, jump ->
        // The strip of every year belongs to a list ordered by year.
        screen.copy(years = if (selection.sort == PageSort.Year) years else emptyList(), jumpTo = jump)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilteredListUiState())

    init {
        loadFirstPage()
        if (selection.value.sort == PageSort.Year) loadYears()
    }

    /** Called as the end of the loaded items comes into view. */
    fun onLoadMore() {
        if (!canLoadMore()) return
        viewModelScope.launch { loadNextPage() }
    }

    private fun canLoadMore(): Boolean {
        val current = content.value
        return current.hasMore && !current.loading && !current.loadingMore
    }

    /** Asks for the page after the loaded ones. True when it arrived, false when it failed or the page was changed meanwhile. */
    private suspend fun loadNextPage(): Boolean {
        val current = content.value
        content.value = current.copy(loadingMore = true, loadMoreFailed = false)
        val asked = selection.value
        val result = page(asked, startIndex = current.items.size)
        // The page was reordered or narrowed while this was out: the new first page answers instead.
        if (selection.value != asked) return false
        return when (result) {
            is ApiResult.Success -> {
                content.update {
                    val items = it.items + result.value.items
                    it.copy(
                        items = items,
                        headings = headingsFor(items, asked.sort),
                        hasMore = result.value.items.isNotEmpty() && items.size < result.value.total,
                        loadingMore = false,
                    )
                }
                true
            }
            is ApiResult.Failure -> {
                content.update { it.copy(loadingMore = false, loadMoreFailed = true) }
                false
            }
        }
    }

    /**
     * A year in the strip was tapped. If it is loaded the screen scrolls to it; if not, pages are loaded one after
     * another until it arrives (or the list ends or a page fails), and then the screen scrolls.
     */
    fun onYear(year: String) {
        jumpJob?.cancel()
        jumpTo.value = year
        jumpJob = viewModelScope.launch {
            while (year !in content.value.headings.values && canLoadMore()) {
                if (!loadNextPage()) break
            }
            // Nowhere to go: the year isn't on the list (a failure shows its own retry).
            if (year !in content.value.headings.values) jumpTo.value = null
        }
    }

    /** The screen has scrolled to the year asked for. */
    fun onJumped() {
        jumpTo.value = null
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
            ListChipKind.Genre -> openLoaded(kind, Strings.genreTitle, genres != null, current.genre?.id.orEmpty(), Strings.allGenres) {
                library.genres(filter).also { if (it is ApiResult.Success) genres = it.value }
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
            ListChipKind.Genre -> current.copy(genre = genres?.firstOrNull { it.id == id })
        }
        if (next == current) return
        selection.value = next
        jumpJob?.cancel()
        jumpTo.value = null
        if (next.sort == PageSort.Year) loadYears()
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

    /** Finds every year of what the page now lists (its genre, if one is chosen); until then the strip shows the loaded years. */
    private fun loadYears() {
        yearsJob?.cancel()
        val asked = selection.value
        val key = asked.genre?.id.orEmpty()
        yearsBySelection[key]?.let {
            allYears.value = it
            return
        }
        allYears.value = emptyList()
        yearsJob = viewModelScope.launch {
            val result = library.yearsOf(filter, asked.genre?.id)
            if (selection.value.genre?.id.orEmpty() != key) return@launch
            when (result) {
                is ApiResult.Success -> {
                    val years = result.value.map { it.toString() }
                    yearsBySelection[key] = years
                    allYears.value = years
                }
                // The strip lists the years on the loaded pages.
                is ApiResult.Failure -> Unit
            }
        }
    }

    private fun optionsFor(kind: ListChipKind): List<FilterOption> = when (kind) {
        ListChipKind.Genre -> genres.orEmpty()
        ListChipKind.Sort -> emptyList()
    }

    private fun chipsFor(selection: Selection): List<ListChip> = buildList {
        add(ListChip(ListChipKind.Sort, Strings.sortChip(sortLabel(selection.sort)), active = selection.sort != filter.defaultSort))
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
        library.filteredPage(filter, asked.sort, asked.genre?.id, startIndex = startIndex)

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
