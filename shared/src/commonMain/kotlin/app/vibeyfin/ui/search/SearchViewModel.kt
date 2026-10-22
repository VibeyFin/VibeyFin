package app.vibeyfin.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.SearchHistory
import app.vibeyfin.domain.model.SearchKind
import app.vibeyfin.domain.model.SearchResult
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.home.HomeExit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which matches the page lists: everything, or one kind. */
enum class SearchFilter { All, Movies, Shows, Episodes, People, Collections }

/** One filter button, ready to draw. */
data class SearchChip(val filter: SearchFilter, val label: String, val selected: Boolean)

/** The matches of one kind, under their heading. */
data class SearchGroup(val kind: SearchKind, val title: String, val results: List<SearchResult>)

data class SearchUiState(
    /** What is in the field, as typed. */
    val query: String = "",
    /** A search is on its way (the field has text and nothing has answered for it yet). */
    val loading: Boolean = false,
    /** Why the search didn't run, with what to try. */
    val message: String? = null,
    /** The answer for [searched]: movies, shows, episodes, people, collections, in that order; kinds with no match are left out. */
    val groups: List<SearchGroup> = emptyList(),
    /** The text the groups answer; empty until a search has come back. */
    val searched: String = "",
    /** Which kind is listed; [SearchFilter.All] lists every group. */
    val filter: SearchFilter = SearchFilter.All,
    /** What was searched for before, newest first; shown while the field is empty. */
    val history: List<String> = emptyList(),
    /** Set when the session ended and the screen must be left. */
    val exit: HomeExit? = null,
) {
    /** The field is empty: the page invites a search. */
    val showsPrompt: Boolean get() = query.isBlank()

    /** The groups the chosen filter lists. */
    val visibleGroups: List<SearchGroup>
        get() = if (filter == SearchFilter.All) groups else groups.filter { it.kind == filter.kind }

    /** The buttons above the results: All, then a kind each. Only shown once something is typed. */
    val chips: List<SearchChip>
        get() = listOf(
            SearchFilter.All to Strings.searchAll,
            SearchFilter.Movies to Strings.searchMovies,
            SearchFilter.Shows to Strings.searchShows,
            SearchFilter.Episodes to Strings.searchEpisodes,
            SearchFilter.People to Strings.searchPeople,
            SearchFilter.Collections to Strings.searchCollections,
        ).map { (filter, label) -> SearchChip(filter, label, selected = filter == this.filter) }

    /** A search came back and held nothing in what the filter lists. */
    val noResults: Boolean get() = !showsPrompt && !loading && message == null && searched.isNotEmpty() && visibleGroups.isEmpty()
}

/** Searches the library as the user types, a moment after the last key, and groups what matches. */
class SearchViewModel(
    private val library: LibraryRepository,
    private val history: SearchHistory,
    connection: ConnectionManager,
) : ViewModel() {
    private val content = MutableStateFlow(SearchUiState())
    private var job: Job? = null

    val uiState: StateFlow<SearchUiState> = combine(content, history.entries, connection.sessionEnded()) { content, history, exit ->
        content.copy(history = history, exit = exit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    /** The field changed: searches [text] once the user pauses, and drops any older search still out. */
    fun onQueryChange(text: String) {
        job?.cancel()
        val term = text.trim()
        if (term.isEmpty()) {
            content.value = SearchUiState(query = text, filter = content.value.filter)
            return
        }
        // What was found for the older text stays on screen, dimmed by `loading`, until the new answer replaces it.
        content.update { it.copy(query = text, loading = true, message = null) }
        job = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            search(term)
        }
    }

    /** A filter button was tapped: the same answer, narrowed to one kind or widened to all. */
    fun onFilter(filter: SearchFilter) {
        content.update { it.copy(filter = filter) }
    }

    /** A match was opened: what was typed is worth finding again. */
    fun onResultOpened() {
        val term = content.value.query
        viewModelScope.launch { history.record(term) }
    }

    /** The keyboard's search key. */
    fun onSubmit() = onResultOpened()

    /** A past search was tapped: it runs again, and moves to the front. */
    fun onHistoryPicked(term: String) {
        onQueryChange(term)
    }

    /** The × on one past search. */
    fun onHistoryRemoved(term: String) {
        viewModelScope.launch { history.remove(term) }
    }

    /** "Clear all" on the past searches. */
    fun onHistoryCleared() {
        viewModelScope.launch { history.clear() }
    }

    /** "Clear": empties the field. */
    fun onClear() = onQueryChange("")

    /** "Try again" after a failure. */
    fun onRetry() {
        val term = content.value.query.trim()
        if (term.isEmpty()) return
        job?.cancel()
        content.update { it.copy(loading = true, message = null) }
        job = viewModelScope.launch { search(term) }
    }

    private suspend fun search(term: String) {
        when (val result = library.search(term)) {
            is ApiResult.Success -> content.update { it.copy(loading = false, groups = result.value.grouped(), searched = term, message = null) }
            is ApiResult.Failure -> content.update { it.copy(loading = false, message = LoadMessages.forError(result.error)) }
        }
    }

    private fun List<SearchResult>.grouped(): List<SearchGroup> = listOf(
        SearchKind.Movie to Strings.searchMovies,
        SearchKind.Show to Strings.searchShows,
        SearchKind.Episode to Strings.searchEpisodes,
        SearchKind.Person to Strings.searchPeople,
        SearchKind.Collection to Strings.searchCollections,
    ).mapNotNull { (kind, title) ->
        filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.let { SearchGroup(kind, title, it) }
    }

    companion object {
        /** How long after the last key a search starts. */
        const val DEBOUNCE_MS = 300L
    }
}

private val SearchFilter.kind: SearchKind
    get() = when (this) {
        SearchFilter.Movies -> SearchKind.Movie
        SearchFilter.Shows -> SearchKind.Show
        SearchFilter.Episodes -> SearchKind.Episode
        SearchFilter.People -> SearchKind.Person
        SearchFilter.Collections -> SearchKind.Collection
        SearchFilter.All -> error("All lists every kind")
    }
