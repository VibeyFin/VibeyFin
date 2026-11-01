package app.vibeyfin.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.OfflineLibrary
import app.vibeyfin.domain.OfflineMode
import app.vibeyfin.domain.SearchHistory
import app.vibeyfin.domain.SeerrConnection
import app.vibeyfin.domain.SeerrRepository
import app.vibeyfin.domain.SeerrState
import app.vibeyfin.domain.model.RequestState
import app.vibeyfin.domain.model.SearchKind
import app.vibeyfin.domain.model.SearchResult
import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.domain.model.SeerrTitle
import app.vibeyfin.domain.model.notInLibrary
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.downloads.profileOrNull
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.seerr.SeerrMessages
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which matches the page lists: everything, one kind, or only what Seerr found that isn't in the library. */
enum class SearchFilter { All, Movies, Shows, Episodes, People, Collections, NotInLibrary }

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
    /** The app is offline: the search covers only what is downloaded, and the page says so. */
    val offline: Boolean = false,
    /** What Seerr found for [searched]; null when Seerr isn't there for this profile, or offline: then nothing of Seerr shows. */
    val seerr: SeerrSearch? = null,
    /** Seerr is signed in for this profile, so the "Not in library" filter is offered. */
    val seerrReady: Boolean = false,
) {
    /** The field is empty: the page invites a search. */
    val showsPrompt: Boolean get() = query.isBlank()

    /** The library's groups the chosen filter lists; none under "Not in library". */
    val visibleGroups: List<SearchGroup>
        get() = when (filter) {
            SearchFilter.All -> groups
            SearchFilter.NotInLibrary -> emptyList()
            else -> groups.filter { it.kind == filter.kind }
        }

    /**
     * The "Not in your library" group as the filter lists it, always after the library's groups; null when it doesn't show. Movies and
     * Shows narrow it to that kind; Episodes, People and Collections hide it. A group with nothing in it doesn't show.
     */
    val seerrGroup: SeerrSearch?
        get() {
            val found = seerr ?: return null
            if (showsPrompt) return null
            val type = when (filter) {
                SearchFilter.All, SearchFilter.NotInLibrary -> null
                SearchFilter.Movies -> SeerrMediaType.Movie
                SearchFilter.Shows -> SeerrMediaType.Show
                SearchFilter.Episodes, SearchFilter.People, SearchFilter.Collections -> return null
            }
            if (found !is SeerrSearch.Found) return found
            val titles = if (type == null) found.titles else found.titles.filter { it.type == type }
            return titles.takeIf { it.isNotEmpty() }?.let(SeerrSearch::Found)
        }

    /** The library found nothing, but Seerr's group shows: a line says so above it (issue #14). */
    val libraryNoMatch: Boolean
        get() = filter != SearchFilter.NotInLibrary && answered && visibleGroups.isEmpty() && seerrGroup != null

    // A search came back for what is typed, without a failure.
    private val answered: Boolean get() = !showsPrompt && !loading && message == null && searched.isNotEmpty()

    /** The buttons above the results: All, then a kind each. Only shown once something is typed. */
    val chips: List<SearchChip>
        get() = listOf(
            SearchFilter.All to Strings.searchAll,
            SearchFilter.Movies to Strings.searchMovies,
            SearchFilter.Shows to Strings.searchShows,
            SearchFilter.Episodes to Strings.searchEpisodes,
            SearchFilter.People to Strings.searchPeople,
            SearchFilter.Collections to Strings.searchCollections,
            SearchFilter.NotInLibrary to Strings.seerrNotInLibraryChip,
        )
            // What is downloaded is movies and episodes; the rest has nothing to find offline.
            .filter { (filter, _) -> !offline || filter == SearchFilter.All || filter == SearchFilter.Movies || filter == SearchFilter.Episodes }
            .filter { (filter, _) -> filter != SearchFilter.NotInLibrary || (seerrReady && !offline) }
            .map { (filter, label) -> SearchChip(filter, label, selected = filter == this.filter) }

    /** A search came back and held nothing in what the filter lists, in the library or from Seerr. */
    val noResults: Boolean get() = answered && visibleGroups.isEmpty() && seerrGroup == null
}

/**
 * Searches the library as the user types, a moment after the last key, and groups what matches. Offline it searches what is
 * downloaded instead, by title, show and cast, and starts over when the app goes offline or comes back.
 */
class SearchViewModel(
    private val library: LibraryRepository,
    private val history: SearchHistory,
    private val connection: ConnectionManager,
    private val offlineMode: OfflineMode,
    private val offlineLibrary: OfflineLibrary,
    private val seerr: SeerrConnection? = null,
    private val seerrRepository: SeerrRepository? = null,
) : ViewModel() {
    private val content = MutableStateFlow(SearchUiState())
    private var job: Job? = null
    private var seerrJob: Job? = null
    private val seerrState: StateFlow<SeerrState> = seerr?.state ?: MutableStateFlow(SeerrState.Hidden)

    val uiState: StateFlow<SearchUiState> = combine(content, history.entries, connection.sessionEnded(), offlineMode.isOffline, seerrState) { content, history, exit, offline, seerr ->
        val ready = seerr is SeerrState.Ready && !offline
        content.copy(
            history = history,
            exit = exit,
            offline = offline,
            seerrReady = ready,
            // Seerr signing out (or the app going offline) takes its group and its filter away at once.
            seerr = content.seerr.takeIf { ready },
            filter = if (!ready && content.filter == SearchFilter.NotInLibrary) SearchFilter.All else content.filter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState(offline = offlineMode.isOffline.value))

    init {
        // Going offline, or coming back, answers the same text from the other place.
        viewModelScope.launch {
            offlineMode.isOffline.drop(1).collect {
                val term = content.value.query.trim()
                if (term.isNotEmpty()) onRetry()
            }
        }
    }

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

    /** "Try again" in the Seerr group: asks Seerr again for the same text, leaving the library's answer alone. */
    fun onSeerrRetry() {
        val term = content.value.searched.ifEmpty { content.value.query.trim() }
        if (term.isEmpty()) return
        seerrJob?.cancel()
        seerrJob = viewModelScope.launch { searchSeerr(term) }
    }

    /** "Try again" after a failure. */
    fun onRetry() {
        val term = content.value.query.trim()
        if (term.isEmpty()) return
        job?.cancel()
        content.update { it.copy(loading = true, message = null) }
        job = viewModelScope.launch { search(term) }
    }

    private suspend fun search(term: String) {
        seerrJob?.cancel()
        if (offlineMode.isOffline.value) {
            val profile = connection.state.value.profileOrNull()
            val found = if (profile == null) emptyList() else offlineLibrary.search(profile, term)
            content.update { it.copy(loading = false, groups = found.grouped(), searched = term, message = null, seerr = null) }
            return
        }
        // Seerr is asked alongside the library and answers on its own: the library's groups never wait for it.
        coroutineScope {
            seerrJob = launch { searchSeerr(term) }
            when (val result = library.search(term)) {
                is ApiResult.Success -> content.update { it.copy(loading = false, groups = result.value.grouped(), searched = term, message = null) }
                is ApiResult.Failure -> content.update { it.copy(loading = false, message = LoadMessages.forError(result.error)) }
            }
        }
    }

    private suspend fun searchSeerr(term: String) {
        val repository = seerrRepository
        if (repository == null || seerrState.value !is SeerrState.Ready) {
            content.update { it.copy(seerr = null) }
            return
        }
        content.update { it.copy(seerr = SeerrSearch.Searching) }
        val found = when (val result = repository.search(term)) {
            // A title Seerr knows is in the library is already among the library's groups.
            is ApiResult.Success -> SeerrSearch.Found(result.value.titles.filter { it.notInLibrary() }.distinctBy { it.type to it.tmdbId })

            // Signed out of Seerr meanwhile: the group goes, as Seerr does.
            is ApiResult.Failure -> if (result.error == ApiError.Unauthorized) null else SeerrSearch.Failed(SeerrMessages.UNREACHABLE_NOW)
        }
        content.update { it.copy(seerr = found) }
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
        SearchFilter.All, SearchFilter.NotInLibrary -> error("$this lists no one kind of the library's")
    }
