package app.vibeyfin.ui.filtered

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.valueOrNull
import app.vibeyfin.domain.AddedWithin
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.Decade
import app.vibeyfin.domain.FavoriteChange
import app.vibeyfin.domain.FilterOption
import app.vibeyfin.domain.FilterSet
import app.vibeyfin.domain.FilterValues
import app.vibeyfin.domain.GenreMatch
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.NamedValue
import app.vibeyfin.domain.PageSort
import app.vibeyfin.domain.SortOrder
import app.vibeyfin.domain.TypeFilter
import app.vibeyfin.domain.VideoQuality
import app.vibeyfin.domain.WatchChanges
import app.vibeyfin.domain.WatchedFilter
import app.vibeyfin.domain.YearRange
import app.vibeyfin.domain.defaultSort
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.domain.offersCollections
import app.vibeyfin.domain.offersPeople
import app.vibeyfin.domain.offersTypeFilter
import app.vibeyfin.domain.orderOn
import app.vibeyfin.domain.sortOn
import app.vibeyfin.i18n.Formats
import app.vibeyfin.i18n.Texts
import app.vibeyfin.ui.children.ChildrenUiState
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.home.title
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The buttons under a category page's title. */
enum class ListChipKind { Movies, Shows, Collections, Filters, Sort, Order, Genre, Year, People, Clear }

/** One of them: [label] already says what is chosen ("Sort: Rating"), and [active] is true when it narrows or reorders the page. */
data class ListChip(val kind: ListChipKind, val label: String, val active: Boolean) {
    /** These open a list (and show an arrow); Movies, Shows, Collections and Order switch on and off. */
    val opensList: Boolean
        get() = kind == ListChipKind.Sort || kind == ListChipKind.Genre || kind == ListChipKind.Year ||
            kind == ListChipKind.People || kind == ListChipKind.Filters
}

/** Which list a sheet shows: a chip's own, the Filters list, or one of the filters it opens. */
enum class ListSheetKind {
    Sort,
    Genre,
    Year,
    People,
    Filters,
    Watched,
    YearFrom,
    YearTo,
    CommunityRating,
    CriticRating,
    AgeRating,
    Studio,
    Tag,
    Library,
    Runtime,
    Added,
    Quality,
    AudioLanguage,
    SubtitleLanguage,
}

/** One row of a sheet: [detail] is small text at its end (a count, or what a filter is set to); [selected] rows are in use. */
data class ListSheetRow(val id: String, val label: String, val detail: String? = null, val selected: Boolean = false)

/** The picker a chip opens: loading, its rows, or why they didn't load. In a [multiSelect] sheet a tap adds or removes a row and the sheet stays open. */
data class ListSheet(
    val kind: ListSheetKind,
    val title: String,
    val rows: List<ListSheetRow> = emptyList(),
    val multiSelect: Boolean = false,
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

/** The sheet that is open: what it shows, its loading state, and whether a pick goes back to the Filters list. */
private data class OpenSheet(
    val kind: ListSheetKind,
    val loading: Boolean = false,
    val failure: ApiError? = null,
    val query: String = "",
    /** A People search has answered [query]. */
    val answered: Boolean = false,
    val fromFilters: Boolean = false,
)

/** What the Filters list and its sheets pick from; read once per type and library. */
private data class FilterChoices(
    val values: FilterValues,
    val studios: List<FilterOption>,
    val libraries: List<FilterOption>,
)

private val MULTI_SELECT = setOf(ListSheetKind.Genre, ListSheetKind.Studio, ListSheetKind.Tag, ListSheetKind.AudioLanguage, ListSheetKind.SubtitleLanguage)

// The sheets whose rows come from the server's filter values.
private val NEEDS_CHOICES = setOf(
    ListSheetKind.Filters, ListSheetKind.Year, ListSheetKind.YearFrom, ListSheetKind.YearTo, ListSheetKind.AgeRating,
    ListSheetKind.Studio, ListSheetKind.Tag, ListSheetKind.Library, ListSheetKind.AudioLanguage, ListSheetKind.SubtitleLanguage,
)

private const val FAVOURITES_ROW = "Favourites"
private const val GENRE_MATCH_ROW = "GenreMatch"

/**
 * The page a category button, the Categories sheet or the Library tab opens: the movies and shows of one type or genre,
 * a page at a time, ordered and narrowed by one [FilterSet] that the buttons above the list change. Filters combine.
 */
class FilteredListViewModel(
    private val filter: HomeFilter,
    private val library: LibraryRepository,
    connection: ConnectionManager,
    changes: WatchChanges,
) : ViewModel() {
    /** The name at the top of the page. */
    val title: String
        get() = filter.title

    private val content = MutableStateFlow(ChildrenUiState())
    private val listFailure = MutableStateFlow<ApiError?>(null)
    private val filters = MutableStateFlow(FilterSet())
    private val openSheet = MutableStateFlow<OpenSheet?>(null)
    private var genres: List<FilterOption>? = null
    private var choices: FilterChoices? = null
    private var nextIndex = 0
    private var sheetJob: Job? = null
    private var pageJob: Job? = null
    private var peopleJob: Job? = null

    // The people the last search found, so a pick can name who was chosen.
    private var foundPeople: List<FilterOption> = emptyList()

    // Taken off My List here: a page the server answered before the change must not bring them back.
    private val removedFromList = mutableSetOf<String>()

    // The text is made here, from what failed and what is chosen, so it follows a change of language.
    private val listState: Flow<ChildrenUiState> = combine(content, filters, listFailure, Texts.language) { content, filters, failure, _ ->
        content.copy(message = failure?.let { LoadMessages.forError(it) }, emptyText = listEmptyText(filters))
    }

    val uiState: StateFlow<FilteredListUiState> = combine(listState, filters, openSheet, connection.sessionEnded(), Texts.language) { list, filters, open, exit, _ ->
        FilteredListUiState(list.copy(exit = exit), chipsFor(filters), open?.let { sheetFor(it, filters) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilteredListUiState())

    init {
        loadFirstPage()
        if (filter == HomeFilter.MyList) viewModelScope.launch { changes.favorites.collect(::onFavoriteChanged) }
    }

    /** Called as the end of the loaded items comes into view. */
    fun onLoadMore() {
        val current = content.value
        if (!current.hasMore || current.busy) return
        content.value = current.copy(loadingMore = true, loadMoreFailed = false)
        val asked = filters.value
        viewModelScope.launch {
            val result = library.filteredPage(filter, asked, startIndex = nextIndex)
            // The page was reordered or narrowed while this was out: the new first page answers instead.
            if (filters.value != asked) return@launch
            when (result) {
                is ApiResult.Success -> {
                    nextIndex = result.value.nextIndex
                    content.update {
                        // A random order is shuffled again for every page, so a title can come twice.
                        val items = (it.items + result.value.items.withoutRemoved()).distinctBy { item -> item.id }
                        it.copy(items = items, hasMore = result.value.hasMore, loadingMore = false, total = result.value.total ?: it.total)
                    }
                }

                is ApiResult.Failure -> content.update { it.copy(loadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    /** Tries again whatever failed: the first page, or the next one. */
    fun onRetry() {
        if (content.value.items.isEmpty()) loadFirstPage() else onLoadMore()
    }

    /** A button above the list was tapped: it switches, or its picker opens. */
    fun onChip(kind: ListChipKind) {
        val current = filters.value
        when (kind) {
            ListChipKind.Movies -> apply(withType(current, TypeFilter.Movies))

            ListChipKind.Shows -> apply(withType(current, TypeFilter.Shows))

            ListChipKind.Collections -> apply(withType(current, TypeFilter.Collections))

            ListChipKind.Order -> apply(current.copy(order = if (current.orderOn(filter) == current.sortOn(filter).naturalOrder) current.orderOn(filter).flipped() else null))

            // Clear drops every filter and the order: the page is as it opened.
            ListChipKind.Clear -> apply(FilterSet())

            ListChipKind.Sort -> open(ListSheetKind.Sort)

            ListChipKind.Genre -> open(ListSheetKind.Genre)

            ListChipKind.Year -> open(ListSheetKind.Year)

            ListChipKind.People -> open(ListSheetKind.People)

            ListChipKind.Filters -> open(ListSheetKind.Filters)
        }
    }

    /** The text in a searchable picker changed (People): who matches is looked up a moment after the last key. */
    fun onSheetQuery(text: String) {
        val open = openSheet.value?.takeIf { it.kind == ListSheetKind.People } ?: return
        val term = text.trim()
        peopleJob?.cancel()
        if (term.isEmpty()) {
            foundPeople = emptyList()
            openSheet.value = open.copy(query = text, loading = false, failure = null)
            return
        }
        openSheet.value = open.copy(query = text, failure = null, answered = false)
        peopleJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val result = library.searchPeople(term)
            foundPeople = result.valueOrNull().orEmpty()
            openSheet.update { current ->
                current?.takeIf { it.kind == ListSheetKind.People }?.copy(answered = true, failure = (result as? ApiResult.Failure)?.error)
            }
        }
    }

    /** A row of the open picker was chosen. */
    fun onPick(id: String) {
        val open = openSheet.value ?: return
        when (open.kind) {
            ListSheetKind.Filters -> pickFilter(id)

            in MULTI_SELECT -> pickMany(open, id)

            else -> {
                val next = picked(open.kind, id, filters.value) ?: return
                closeOrReturn(open)
                apply(next)
            }
        }
    }

    fun onDismissSheet() {
        sheetJob?.cancel()
        peopleJob?.cancel()
        openSheet.value = null
    }

    /** "Try again" in the picker. */
    fun onRetrySheet() {
        val open = openSheet.value ?: return
        open(open.kind, fromFilters = open.fromFilters)
    }

    private fun open(kind: ListSheetKind, fromFilters: Boolean = false) {
        sheetJob?.cancel()
        peopleJob?.cancel()
        if (kind == ListSheetKind.People) foundPeople = emptyList()
        val needsGenres = kind == ListSheetKind.Genre && genres == null
        val needsChoices = kind in NEEDS_CHOICES && choices == null
        openSheet.value = OpenSheet(kind, loading = needsGenres || needsChoices, fromFilters = fromFilters)
        if (needsGenres) sheetJob = viewModelScope.launch { finishLoading(kind, loadGenres()) }
        if (needsChoices) sheetJob = viewModelScope.launch { finishLoading(kind, loadChoices()) }
    }

    private fun finishLoading(kind: ListSheetKind, result: ApiResult<*>) {
        openSheet.update { open ->
            open?.takeIf { it.kind == kind }?.copy(loading = false, failure = (result as? ApiResult.Failure)?.error)
        }
    }

    private suspend fun loadGenres(): ApiResult<List<FilterOption>> {
        val result = library.genres(filter, filters.value.type)
        if (result is ApiResult.Success) genres = result.value
        return result
    }

    private suspend fun loadChoices(): ApiResult<FilterChoices> = coroutineScope {
        val asked = filters.value
        val values = async { library.filterValues(filter, asked) }
        val studios = async { library.studios(filter, asked) }
        val libraries = async { library.videoLibraries() }
        val loaded = listOf(values.await(), studios.await(), libraries.await())
        val failure = loaded.filterIsInstance<ApiResult.Failure>().firstOrNull()
        val result = failure ?: ApiResult.Success(
            FilterChoices(
                values = values.await().valueOrNull() ?: FilterValues(),
                studios = studios.await().valueOrNull().orEmpty(),
                libraries = libraries.await().valueOrNull().orEmpty(),
            ),
        )

        if (result is ApiResult.Success) choices = result.value
        result
    }

    // A row of the Filters list: two switch where they are, the others open their own sheet.
    private fun pickFilter(id: String) {
        val current = filters.value
        when (id) {
            FAVOURITES_ROW -> apply(current.copy(favouritesOnly = !current.favouritesOnly))
            GENRE_MATCH_ROW -> apply(current.copy(genreMatch = if (current.genreMatch == GenreMatch.All) GenreMatch.Any else GenreMatch.All))
            else -> ListSheetKind.entries.firstOrNull { it.name == id }?.let { open(it, fromFilters = true) }
        }
    }

    // In a sheet of several values a row is added or taken away and the sheet stays; the first row clears them all.
    private fun pickMany(open: OpenSheet, id: String) {
        val current = filters.value
        if (id.isEmpty()) {
            closeOrReturn(open)
            apply(cleared(open.kind, current))
            return
        }
        apply(toggled(open.kind, id, current) ?: return)
    }

    private fun closeOrReturn(open: OpenSheet) {
        if (open.fromFilters) open(ListSheetKind.Filters) else onDismissSheet()
    }

    private fun cleared(kind: ListSheetKind, set: FilterSet): FilterSet = when (kind) {
        ListSheetKind.Genre -> set.copy(genres = emptyList(), genreMatch = GenreMatch.Any)
        ListSheetKind.Studio -> set.copy(studios = emptyList())
        ListSheetKind.Tag -> set.copy(tags = emptyList())
        ListSheetKind.AudioLanguage -> set.copy(audioLanguages = emptyList())
        ListSheetKind.SubtitleLanguage -> set.copy(subtitleLanguages = emptyList())
        else -> set
    }

    private fun toggled(kind: ListSheetKind, id: String, set: FilterSet): FilterSet? {
        val loaded = choices

        return when (kind) {
            ListSheetKind.Genre -> genres?.named(id)?.let { set.copy(genres = set.genres.toggle(it)) }
            ListSheetKind.Studio -> loaded?.studios?.named(id)?.let { set.copy(studios = set.studios.toggle(it)) }
            ListSheetKind.Tag -> set.copy(tags = if (id in set.tags) set.tags - id else set.tags + id)
            ListSheetKind.AudioLanguage -> loaded?.values?.audioLanguages?.named(id)?.let { set.copy(audioLanguages = set.audioLanguages.toggle(it)) }
            ListSheetKind.SubtitleLanguage -> loaded?.values?.subtitleLanguages?.named(id)?.let { set.copy(subtitleLanguages = set.subtitleLanguages.toggle(it)) }
            else -> null
        }
    }

    // The set after a pick in a sheet of one value; an empty id is its first row (Any, All years), which clears it.
    private fun picked(kind: ListSheetKind, id: String, set: FilterSet): FilterSet? {
        val years = choices?.values?.years.orEmpty()
        val clear = id.isEmpty()

        return when (kind) {
            ListSheetKind.Sort -> PageSort.entries.firstOrNull { it.name == id }?.let { set.copy(sort = it.takeIf { sort -> sort != filter.defaultSort }, order = null) }

            ListSheetKind.Year, ListSheetKind.YearFrom, ListSheetKind.YearTo -> set.copy(years = pickedYears(kind, id, set.years, years))

            ListSheetKind.People -> set.copy(person = if (clear) null else foundPeople.named(id) ?: set.person)

            ListSheetKind.Watched -> set.copy(watched = WatchedFilter.entries.firstOrNull { it.name == id })

            ListSheetKind.CommunityRating -> set.copy(minCommunityRating = id.toIntOrNull())

            ListSheetKind.CriticRating -> set.copy(minCriticRating = id.toIntOrNull())

            ListSheetKind.AgeRating -> set.copy(maxAgeRating = id.takeUnless { clear })

            ListSheetKind.Library -> set.copy(library = choices?.libraries?.named(id))

            ListSheetKind.Runtime -> set.copy(runtime = RUNTIME_LIMITS.firstOrNull { runtimeId(it) == id })

            ListSheetKind.Added -> set.copy(addedWithin = AddedWithin.entries.firstOrNull { it.name == id })

            ListSheetKind.Quality -> set.copy(quality = VideoQuality.entries.firstOrNull { it.name == id })

            ListSheetKind.Filters, ListSheetKind.Genre, ListSheetKind.Studio, ListSheetKind.Tag,
            ListSheetKind.AudioLanguage, ListSheetKind.SubtitleLanguage,
            -> null
        }
    }

    private fun pickedYears(kind: ListSheetKind, id: String, range: YearRange?, years: List<Int>): YearRange? = when (kind) {
        ListSheetKind.YearFrom -> bounded(from = id.toIntOrNull(), to = range?.to, years)
        ListSheetKind.YearTo -> bounded(from = range?.from, to = id.toIntOrNull(), years)
        else -> if (id.isEmpty()) null else Decade.entries.firstOrNull { it.name == id }?.years ?: id.toIntOrNull()?.let { YearRange(it, it) }
    }

    // A range with one bound left open runs to the oldest or newest year there is; with both open there is no range.
    private fun bounded(from: Int?, to: Int?, years: List<Int>): YearRange? {
        if (from == null && to == null) return null
        val low = from ?: years.minOrNull() ?: to ?: return null
        val high = to ?: years.maxOrNull() ?: low

        return YearRange(minOf(low, high), maxOf(low, high))
    }

    private fun withType(set: FilterSet, tapped: TypeFilter): FilterSet {
        val type = if (set.type == tapped) null else tapped

        return when (type) {
            // Collections have no genre, year or the like to narrow by: choosing them keeps only the order.
            TypeFilter.Collections -> FilterSet(sort = set.sort, order = set.order, type = type)

            // Shows carry no picture or sound of their own to filter by.
            TypeFilter.Shows -> set.copy(type = type, quality = null, audioLanguages = emptyList(), subtitleLanguages = emptyList())

            else -> set.copy(type = type)
        }
    }

    private fun apply(next: FilterSet) {
        val current = filters.value
        if (next == current) return
        if (next.type != current.type || next.library != current.library) {
            genres = null
            choices = null
        }
        filters.value = next
        loadFirstPage()
    }

    private fun chipsFor(set: FilterSet): List<ListChip> = buildList {
        val collections = set.type == TypeFilter.Collections
        val sort = set.sortOn(filter)
        val count = set.filtersSheetCount()

        if (filter.offersTypeFilter) {
            add(ListChip(ListChipKind.Movies, Strings.filterMovies, active = set.type == TypeFilter.Movies))
            add(ListChip(ListChipKind.Shows, Strings.filterShows, active = set.type == TypeFilter.Shows))
            if (filter.offersCollections) add(ListChip(ListChipKind.Collections, Strings.searchCollections, active = set.type == TypeFilter.Collections))
        }
        if (!collections) add(ListChip(ListChipKind.Filters, if (count > 0) Strings.filtersChip(count) else Strings.filtersButton, active = count > 0))
        add(ListChip(ListChipKind.Sort, Strings.sortChip(sortLabel(sort)), active = set.sort != null))
        if (sort != PageSort.Random) add(ListChip(ListChipKind.Order, orderLabel(sort, set.orderOn(filter)), active = set.order != null))
        // A genre's own page is already one genre; collections have none to pick.
        if (filter !is HomeFilter.Genre && !collections) add(ListChip(ListChipKind.Genre, genreChipLabel(set), active = set.genres.isNotEmpty()))
        if (!collections) add(ListChip(ListChipKind.Year, set.years?.let { Strings.yearChip(yearLabel(it)) } ?: Strings.yearButton, active = set.years != null))
        if (filter.offersPeople && !collections) {
            add(ListChip(ListChipKind.People, set.person?.let { Strings.peopleChip(it.name) } ?: Strings.peopleButton, active = set.person != null))
        }
        // Once anything is narrowed or reordered, one button puts the page back as it opened.
        if (set != FilterSet()) add(ListChip(ListChipKind.Clear, Strings.clearFilters, active = false))
    }

    private fun genreChipLabel(set: FilterSet): String = when (set.genres.size) {
        0 -> Strings.genreButton
        1 -> Strings.genreChip(set.genres.single().name)
        else -> Strings.genresChip(set.genres.size)
    }

    private fun sheetFor(open: OpenSheet, set: FilterSet): ListSheet {
        val base = ListSheet(
            open.kind,
            sheetTitle(open.kind),
            multiSelect = open.kind in MULTI_SELECT,
            loading = open.loading,
            message = open.failure?.let { LoadMessages.forError(it) },
        )
        if (open.loading || open.failure != null) return base
        if (open.kind == ListSheetKind.People) return peopleSheet(base, open, set)

        return base.copy(rows = rowsFor(open.kind, set))
    }

    private fun peopleSheet(base: ListSheet, open: OpenSheet, set: FilterSet): ListSheet = base.copy(
        rows = foundPeople.map { ListSheetRow(it.id, it.name, selected = it.id == set.person?.id) },
        searchable = true,
        query = open.query,
        hint = Strings.peopleSearchHint,
        emptyText = when {
            open.query.isBlank() -> Strings.peoplePrompt
            open.answered -> Strings.noPeopleFound
            else -> null
        },
    )

    private fun sheetTitle(kind: ListSheetKind): String = when (kind) {
        ListSheetKind.Sort -> Strings.sortTitle
        ListSheetKind.Genre -> Strings.genreTitle
        ListSheetKind.Year -> Strings.yearTitle
        ListSheetKind.People -> Strings.peopleTitle
        ListSheetKind.Filters -> Strings.filtersTitle
        ListSheetKind.Watched -> Strings.watchedTitle
        ListSheetKind.YearFrom -> Strings.yearFromTitle
        ListSheetKind.YearTo -> Strings.yearToTitle
        ListSheetKind.CommunityRating -> Strings.communityRatingTitle
        ListSheetKind.CriticRating -> Strings.criticRatingTitle
        ListSheetKind.AgeRating -> Strings.ageRatingTitle
        ListSheetKind.Studio -> Strings.studioTitle
        ListSheetKind.Tag -> Strings.tagTitle
        ListSheetKind.Library -> Strings.libraryTitle
        ListSheetKind.Runtime -> Strings.runtimeTitle
        ListSheetKind.Added -> Strings.addedTitle
        ListSheetKind.Quality -> Strings.resolutionTitle
        ListSheetKind.AudioLanguage -> Strings.audioLanguageTitle
        ListSheetKind.SubtitleLanguage -> Strings.subtitleLanguageTitle
    }

    private fun rowsFor(kind: ListSheetKind, set: FilterSet): List<ListSheetRow> {
        val loaded = choices
        val values = loaded?.values ?: FilterValues()

        return when (kind) {
            ListSheetKind.Sort -> PageSort.entries.map { ListSheetRow(it.name, sortLabel(it), selected = it == set.sortOn(filter)) }

            ListSheetKind.Genre -> anyRow(Strings.allGenres, set.genres.isEmpty()) + genres.orEmpty().map { it.row(selected = set.genres.any { genre -> genre.id == it.id }) }

            ListSheetKind.Year, ListSheetKind.YearFrom, ListSheetKind.YearTo -> yearRows(kind, set.years, values.years)

            ListSheetKind.Filters -> filterRows(set, loaded)

            ListSheetKind.Watched -> anyRow(Strings.filterAny, set.watched == null) + WatchedFilter.entries.map { ListSheetRow(it.name, watchedLabel(it), selected = set.watched == it) }

            ListSheetKind.CommunityRating -> anyRow(Strings.filterAny, set.minCommunityRating == null) +
                COMMUNITY_RATINGS.map { ListSheetRow(it.toString(), Strings.ratingAtLeast(it), selected = set.minCommunityRating == it) }

            ListSheetKind.CriticRating -> anyRow(Strings.filterAny, set.minCriticRating == null) +
                CRITIC_RATINGS.map { ListSheetRow(it.toString(), Strings.criticRatingAtLeast(it), selected = set.minCriticRating == it) }

            ListSheetKind.AgeRating -> anyRow(Strings.filterAny, set.maxAgeRating == null) + values.ageRatings.map { ListSheetRow(it, Strings.ageRatingUpTo(it), selected = set.maxAgeRating == it) }

            ListSheetKind.Studio -> anyRow(Strings.filterAny, set.studios.isEmpty()) + loaded?.studios.orEmpty().map { it.row(selected = set.studios.any { studio -> studio.id == it.id }) }

            ListSheetKind.Tag -> anyRow(Strings.filterAny, set.tags.isEmpty()) + values.tags.map { ListSheetRow(it, it, selected = it in set.tags) }

            ListSheetKind.Library -> anyRow(Strings.allLibraries, set.library == null) + loaded?.libraries.orEmpty().map { it.row(selected = it.id == set.library?.id) }

            ListSheetKind.Runtime -> anyRow(Strings.filterAny, set.runtime == null) + RUNTIME_LIMITS.map { ListSheetRow(runtimeId(it), runtimeLabel(it), selected = set.runtime == it) }

            ListSheetKind.Added -> anyRow(Strings.filterAny, set.addedWithin == null) + AddedWithin.entries.map { ListSheetRow(it.name, addedLabel(it), selected = set.addedWithin == it) }

            ListSheetKind.Quality -> anyRow(Strings.filterAny, set.quality == null) + VideoQuality.entries.map { ListSheetRow(it.name, qualityLabel(it), selected = set.quality == it) }

            ListSheetKind.AudioLanguage -> anyRow(Strings.filterAny, set.audioLanguages.isEmpty()) + values.audioLanguages.map { it.row(selected = set.audioLanguages.any { language -> language.id == it.id }) }

            ListSheetKind.SubtitleLanguage -> anyRow(Strings.filterAny, set.subtitleLanguages.isEmpty()) +
                values.subtitleLanguages.map { it.row(selected = set.subtitleLanguages.any { language -> language.id == it.id }) }

            ListSheetKind.People -> emptyList()
        }
    }

    private fun yearRows(kind: ListSheetKind, range: YearRange?, years: List<Int>): List<ListSheetRow> = when (kind) {
        ListSheetKind.YearFrom -> anyRow(Strings.filterAny, range == null) + years.map { ListSheetRow(it.toString(), it.toString(), selected = range?.from == it) }

        ListSheetKind.YearTo -> anyRow(Strings.filterAny, range == null) + years.map { ListSheetRow(it.toString(), it.toString(), selected = range?.to == it) }

        else -> anyRow(Strings.allYears, range == null) +
            Decade.entries.map { ListSheetRow(it.name, decadeLabel(it), selected = range == it.years) } +
            years.map { ListSheetRow(it.toString(), it.toString(), selected = range == YearRange(it, it)) }
    }

    // The Filters list: each filter this page can use, with what it is set to. A filter with nothing to pick is left out.
    private fun filterRows(set: FilterSet, loaded: FilterChoices?): List<ListSheetRow> = buildList {
        val values = loaded?.values ?: FilterValues()
        val shows = filter == HomeFilter.Shows || set.type == TypeFilter.Shows

        fun row(kind: ListSheetKind, detail: String?) = add(ListSheetRow(kind.name, sheetTitle(kind), detail ?: Strings.filterAny, selected = detail != null))

        row(ListSheetKind.Watched, set.watched?.let(::watchedLabel))
        if (filter != HomeFilter.MyList) add(ListSheetRow(FAVOURITES_ROW, Strings.onlyMyList, if (set.favouritesOnly) Strings.filterOn else Strings.filterOff, selected = set.favouritesOnly))
        if (set.genres.size > 1) add(ListSheetRow(GENRE_MATCH_ROW, Strings.genreMatchTitle, genreMatchLabel(set.genreMatch), selected = set.genreMatch == GenreMatch.All))
        if (values.years.isNotEmpty()) {
            row(ListSheetKind.YearFrom, set.years?.from?.toString())
            row(ListSheetKind.YearTo, set.years?.to?.toString())
        }
        row(ListSheetKind.CommunityRating, set.minCommunityRating?.let(Strings::ratingAtLeast))
        row(ListSheetKind.CriticRating, set.minCriticRating?.let(Strings::criticRatingAtLeast))
        if (values.ageRatings.isNotEmpty()) row(ListSheetKind.AgeRating, set.maxAgeRating?.let(Strings::ageRatingUpTo))
        if (loaded?.studios.orEmpty().isNotEmpty()) row(ListSheetKind.Studio, manyLabel(set.studios.map { it.name }))
        if (values.tags.isNotEmpty()) row(ListSheetKind.Tag, manyLabel(set.tags))
        if (loaded?.libraries.orEmpty().size > 1) row(ListSheetKind.Library, set.library?.name)
        row(ListSheetKind.Runtime, set.runtime?.let(::runtimeLabel))
        row(ListSheetKind.Added, set.addedWithin?.let(::addedLabel))
        if (!shows) {
            row(ListSheetKind.Quality, set.quality?.let(::qualityLabel))
            if (values.audioLanguages.isNotEmpty()) row(ListSheetKind.AudioLanguage, manyLabel(set.audioLanguages.map { it.name }))
            if (values.subtitleLanguages.isNotEmpty()) row(ListSheetKind.SubtitleLanguage, manyLabel(set.subtitleLanguages.map { it.name }))
        }
    }

    private fun listEmptyText(set: FilterSet): String = if (set != FilterSet()) Strings.nothingMatchesFilters else Strings.emptyFolder

    private fun anyRow(label: String, selected: Boolean): List<ListSheetRow> = listOf(ListSheetRow("", label, selected = selected))

    // One put back is placed where the server's order puts it.
    private fun onFavoriteChanged(change: FavoriteChange) {
        if (change.favorite) {
            removedFromList.remove(change.itemId)
            loadFirstPage()
            return
        }
        removedFromList.add(change.itemId)
        content.update { current ->
            val remaining = current.items.withoutRemoved()
            if (remaining.size == current.items.size) current else current.copy(items = remaining, total = current.total?.minus(current.items.size - remaining.size))
        }
    }

    private fun List<MediaItem>.withoutRemoved(): List<MediaItem> = if (removedFromList.isEmpty()) this else filterNot { it.id in removedFromList }

    private fun loadFirstPage() {
        val asked = filters.value

        // The list on screen stays (dimmed) until the new one is in, and so does the count: nothing above it changes size or jumps.
        content.update { current ->
            if (current.items.isNotEmpty() && listFailure.value == null) {
                current.copy(refreshing = true, loadMoreFailed = false)
            } else {
                ChildrenUiState(loading = true, total = current.total)
            }
        }
        listFailure.value = null
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            val result = library.filteredPage(filter, asked)
            if (filters.value != asked) return@launch
            when (result) {
                is ApiResult.Success -> {
                    nextIndex = result.value.nextIndex
                    content.value = ChildrenUiState(items = result.value.items.withoutRemoved(), loading = false, hasMore = result.value.hasMore, total = result.value.total)
                }

                is ApiResult.Failure -> {
                    listFailure.value = result.error
                    content.value = ChildrenUiState(loading = false)
                }
            }
        }
    }
}

private fun SortOrder.flipped(): SortOrder = if (this == SortOrder.Ascending) SortOrder.Descending else SortOrder.Ascending

private fun FilterOption.row(selected: Boolean): ListSheetRow = ListSheetRow(id, name, count?.let { Formats.current.integer(it.toLong()) }, selected)

private fun List<FilterOption>.named(id: String): NamedValue? = firstOrNull { it.id == id }?.let { NamedValue(it.id, it.name) }

private fun List<NamedValue>.toggle(value: NamedValue): List<NamedValue> = if (any { it.id == value.id }) filterNot { it.id == value.id } else this + value

// How long after the last key a People search starts.
private const val SEARCH_DEBOUNCE_MS = 300L

// A page is being fetched, or the list is being refreshed
private val ChildrenUiState.busy get() = loading || loadingMore || refreshing
