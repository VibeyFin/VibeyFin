package app.vibeyfin.ui.filtered

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.FilterOption
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.Decade
import app.vibeyfin.domain.PageSort
import app.vibeyfin.domain.offersCollections
import app.vibeyfin.domain.offersYearAndPeople
import app.vibeyfin.domain.TypeFilter
import app.vibeyfin.domain.offersTypeFilter
import app.vibeyfin.domain.defaultSort
import app.vibeyfin.ui.children.ChildrenUiState
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.home.title
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The buttons under a category page's title. */
enum class ListChipKind { Movies, Shows, Collections, Sort, Genre, Year, People, Clear }

/** One of them: [label] already says what is chosen ("Sort: Rating"), and [active] is true when it narrows or reorders the page. */
data class ListChip(val kind: ListChipKind, val label: String, val active: Boolean) {
    /** Sort and Genre open a list (and show an arrow); Movies and Shows switch on and off. */
    val opensList: Boolean get() = kind == ListChipKind.Sort || kind == ListChipKind.Genre || kind == ListChipKind.Year || kind == ListChipKind.People
}

/** The picker a chip opens: loading, its rows, or why they didn't load. [selectedId] is the row in use; an empty id is "All". */
data class ListSheet(
    val kind: ListChipKind,
    val title: String,
    val options: List<FilterOption> = emptyList(),
    val selectedId: String = "",
    val loading: Boolean = false,
    val message: String? = null,
    /** A picker with a search field (People): [query] is what was typed and [hint] the field's placeholder. */
    val searchable: Boolean = false,
    val query: String = "",
    val hint: String = "",
    /** What a searchable picker says while it has no rows: type to search, or nobody was found. */
    val emptyText: String? = null,
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
    val type: TypeFilter? = null,
    /** The Library page: only titles from this decade. */
    val decade: Decade? = null,
    /** The Library page: only titles with this person. */
    val person: FilterOption? = null,
)

/**
 * The page a category button or the Categories sheet opens: the movies and shows of one type or genre, a page at a
 * time. Sort and (except on a genre's own page) genre can be changed from the buttons above the list. New is what was
 * added to the server most recently.
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
    private var genres: List<FilterOption>? = null
    private var sheetJob: Job? = null
    private var pageJob: Job? = null
    private var peopleJob: Job? = null
    // The people the last search found, so a pick can name who was chosen.
    private var foundPeople: List<FilterOption> = emptyList()

    val uiState: StateFlow<FilteredListUiState> = combine(content, selection, sheet, connection.sessionEnded()) { content, selection, sheet, exit ->
        FilteredListUiState(content.copy(exit = exit), chipsFor(selection), sheet)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilteredListUiState())

    init {
        loadFirstPage()
    }

    /** Called as the end of the loaded items comes into view. */
    fun onLoadMore() {
        val current = content.value
        if (!current.hasMore || current.loading || current.loadingMore || current.refreshing) return
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
            // Movies and Shows switch on and off, and the genres they count are those of the kind chosen.
            ListChipKind.Movies, ListChipKind.Shows, ListChipKind.Collections -> {
                val tapped = when (kind) {
                    ListChipKind.Movies -> TypeFilter.Movies
                    ListChipKind.Shows -> TypeFilter.Shows
                    else -> TypeFilter.Collections
                }
                val type = if (current.type == tapped) null else tapped
                // Collections have no genre, year or person to narrow by here: choosing them drops those.
                selection.value = if (type == TypeFilter.Collections) current.copy(type = type, genre = null, decade = null, person = null) else current.copy(type = type)
                genres = null
                sheet.value = null
                sheetJob?.cancel()
                loadFirstPage()
            }
            ListChipKind.Sort -> sheet.value = ListSheet(
                kind = kind,
                title = Strings.sortTitle,
                options = PageSort.entries.map { FilterOption(it.name, sortLabel(it)) },
                selectedId = current.sort.name,
            )
            ListChipKind.Genre -> openGenres(current.genre?.id.orEmpty())
            ListChipKind.Year -> sheet.value = ListSheet(
                kind = kind,
                title = Strings.yearTitle,
                options = listOf(FilterOption("", Strings.allYears)) + Decade.entries.map { FilterOption(it.name, decadeLabel(it)) },
                selectedId = current.decade?.name.orEmpty(),
            )
            ListChipKind.People -> {
                peopleJob?.cancel()
                foundPeople = emptyList()
                sheet.value = ListSheet(
                    kind = kind,
                    title = Strings.peopleTitle,
                    selectedId = current.person?.id.orEmpty(),
                    searchable = true,
                    hint = Strings.peopleSearchHint,
                    emptyText = Strings.peoplePrompt,
                )
            }
            // Clear drops every filter and the order: the page is as it opened.
            ListChipKind.Clear -> {
                val reset = Selection(sort = filter.defaultSort)
                if (reset != current) {
                    selection.value = reset
                    genres = null
                    loadFirstPage()
                }
            }
        }
    }

    /** The text in a searchable picker changed (People): who matches is looked up a moment after the last key. */
    fun onSheetQuery(text: String) {
        val open = sheet.value?.takeIf { it.searchable } ?: return
        peopleJob?.cancel()
        val term = text.trim()
        if (term.isEmpty()) {
            foundPeople = emptyList()
            sheet.value = open.copy(query = text, options = emptyList(), loading = false, message = null, emptyText = Strings.peoplePrompt)
            return
        }
        sheet.value = open.copy(query = text, message = null)
        peopleJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val result = library.searchPeople(term)
            sheet.update { current ->
                current?.takeIf { it.searchable }?.let {
                    when (result) {
                        is ApiResult.Success -> {
                            foundPeople = result.value
                            it.copy(options = result.value, emptyText = Strings.noPeopleFound, message = null)
                        }
                        is ApiResult.Failure -> it.copy(options = emptyList(), message = LoadMessages.forError(result.error))
                    }
                }
            }
        }
    }

    /** A row of the open picker was chosen: the page reloads in that order or narrowed that way. */
    fun onPick(id: String) {
        val open = sheet.value ?: return
        sheet.value = null
        sheetJob?.cancel()
        peopleJob?.cancel()
        val current = selection.value
        val next = when (open.kind) {
            ListChipKind.Sort -> current.copy(sort = PageSort.entries.firstOrNull { it.name == id } ?: return)
            ListChipKind.Genre -> current.copy(genre = genres?.firstOrNull { it.id == id })
            ListChipKind.Year -> current.copy(decade = Decade.entries.firstOrNull { it.name == id })
            ListChipKind.People -> current.copy(person = if (id.isEmpty()) null else foundPeople.firstOrNull { it.id == id } ?: current.person)
            // These do not open a picker.
            ListChipKind.Movies, ListChipKind.Shows, ListChipKind.Collections, ListChipKind.Clear -> return
        }
        if (next == current) return
        selection.value = next
        loadFirstPage()
    }

    fun onDismissSheet() {
        sheetJob?.cancel()
        peopleJob?.cancel()
        sheet.value = null
    }

    /** "Try again" in the picker. */
    fun onRetrySheet() {
        val open = sheet.value ?: return
        sheet.value = open.copy(loading = true, message = null)
        onChip(open.kind)
    }

    /** The genre picker: "All genres" and the genres with how many of this page's kind of title each holds. */
    private fun openGenres(selectedId: String) {
        sheetJob?.cancel()
        fun rows(options: List<FilterOption>) = listOf(FilterOption("", Strings.allGenres)) + options
        genres?.let {
            sheet.value = ListSheet(ListChipKind.Genre, Strings.genreTitle, rows(it), selectedId)
            return
        }
        sheet.value = ListSheet(ListChipKind.Genre, Strings.genreTitle, loading = true, selectedId = selectedId)
        sheetJob = viewModelScope.launch {
            val result = library.genres(filter, selection.value.type)
            if (result is ApiResult.Success) genres = result.value
            sheet.update { open ->
                open?.takeIf { it.kind == ListChipKind.Genre }?.let {
                    when (result) {
                        is ApiResult.Success -> it.copy(options = rows(result.value), loading = false, message = null)
                        is ApiResult.Failure -> it.copy(loading = false, message = LoadMessages.forError(result.error))
                    }
                }
            }
        }
    }

    private fun chipsFor(selection: Selection): List<ListChip> = buildList {
        if (filter.offersTypeFilter) {
            add(ListChip(ListChipKind.Movies, Strings.filterMovies, active = selection.type == TypeFilter.Movies))
            add(ListChip(ListChipKind.Shows, Strings.filterShows, active = selection.type == TypeFilter.Shows))
            if (filter.offersCollections) add(ListChip(ListChipKind.Collections, Strings.searchCollections, active = selection.type == TypeFilter.Collections))
        }
        add(ListChip(ListChipKind.Sort, Strings.sortChip(sortLabel(selection.sort)), active = selection.sort != filter.defaultSort))
        val collections = selection.type == TypeFilter.Collections
        // A genre's own page is already one genre; collections have none to pick.
        if (filter !is HomeFilter.Genre && !collections) {
            add(ListChip(ListChipKind.Genre, selection.genre?.let { Strings.genreChip(it.name) } ?: Strings.genreButton, active = selection.genre != null))
        }
        if (filter.offersYearAndPeople && !collections) {
            add(ListChip(ListChipKind.Year, selection.decade?.let { Strings.yearChip(decadeLabel(it)) } ?: Strings.yearButton, active = selection.decade != null))
            add(ListChip(ListChipKind.People, selection.person?.let { Strings.peopleChip(it.name) } ?: Strings.peopleButton, active = selection.person != null))
        }
        // Once anything is narrowed or reordered, one button puts the page back as it opened.
        if (filter.offersYearAndPeople && selection != Selection(sort = filter.defaultSort)) {
            add(ListChip(ListChipKind.Clear, Strings.clearFilters, active = false))
        }
    }

    private fun decadeLabel(decade: Decade) = when (decade) {
        Decade.Twenties -> Strings.decade2020s
        Decade.Tens -> Strings.decade2010s
        Decade.Noughties -> Strings.decade2000s
        Decade.Nineties -> Strings.decade1990s
        Decade.Eighties -> Strings.decade1980s
        Decade.Older -> Strings.decadeOlder
    }

    private fun sortLabel(sort: PageSort) = when (sort) {
        PageSort.Name -> Strings.sortName
        PageSort.Added -> Strings.sortAdded
        PageSort.Rating -> Strings.sortRating
    }

    private suspend fun page(asked: Selection, startIndex: Int) =
        library.filteredPage(filter, asked.sort, asked.genre?.id, startIndex = startIndex, type = asked.type, decade = asked.decade, personId = asked.person?.id)

    private fun loadFirstPage() {
        // The list on screen stays (dimmed) until the new one is in, and so does the count: nothing above it changes size or jumps.
        content.update { current ->
            if (current.items.isNotEmpty() && current.message == null) {
                current.copy(refreshing = true, loadMoreFailed = false)
            } else {
                ChildrenUiState(loading = true, total = current.total)
            }
        }
        pageJob?.cancel()
        val asked = selection.value
        pageJob = viewModelScope.launch {
            val result = page(asked, startIndex = 0)
            if (selection.value != asked) return@launch
            content.value = when (result) {
                is ApiResult.Success -> ChildrenUiState(
                    items = result.value.items,
                    loading = false,
                    hasMore = result.value.hasMore,
                    total = result.value.total,
                )
                is ApiResult.Failure -> ChildrenUiState(loading = false, message = LoadMessages.forError(result.error))
            }
        }
    }
}

/** How long after the last key a People search starts. */
private const val SEARCH_DEBOUNCE_MS = 300L
