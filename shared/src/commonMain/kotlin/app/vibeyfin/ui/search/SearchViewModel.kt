package app.vibeyfin.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.LibraryRepository
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
    /** Set when the session ended and the screen must be left. */
    val exit: HomeExit? = null,
) {
    /** The field is empty: the page invites a search. */
    val showsPrompt: Boolean get() = query.isBlank()

    /** A search came back and held nothing. */
    val noResults: Boolean get() = !showsPrompt && !loading && message == null && searched.isNotEmpty() && groups.isEmpty()
}

/** Searches the library as the user types, a moment after the last key, and groups what matches. */
class SearchViewModel(
    private val library: LibraryRepository,
    connection: ConnectionManager,
) : ViewModel() {
    private val content = MutableStateFlow(SearchUiState())
    private var job: Job? = null

    val uiState: StateFlow<SearchUiState> = combine(content, connection.sessionEnded()) { content, exit ->
        content.copy(exit = exit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    /** The field changed: searches [text] once the user pauses, and drops any older search still out. */
    fun onQueryChange(text: String) {
        job?.cancel()
        val term = text.trim()
        if (term.isEmpty()) {
            content.value = SearchUiState(query = text)
            return
        }
        // What was found for the older text stays on screen, dimmed by `loading`, until the new answer replaces it.
        content.update { it.copy(query = text, loading = true, message = null) }
        job = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            search(term)
        }
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
