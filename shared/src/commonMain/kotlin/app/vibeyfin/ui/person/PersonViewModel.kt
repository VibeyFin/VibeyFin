package app.vibeyfin.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.Person
import app.vibeyfin.i18n.Texts
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.home.MediaRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PersonUiState(
    val person: Person? = null,
    /** The person's own details are on their way. */
    val loading: Boolean = true,
    /** Why the person didn't load, with what to try. */
    val message: String? = null,
    /** What they appear in: movies first, then shows; each row is hidden when it is empty. */
    val movies: MediaRow = MediaRow("movies", Strings.collectionMovies, loading = true),
    val shows: MediaRow = MediaRow("shows", Strings.collectionShows, loading = true),
    /** Why the list of appearances didn't load; the page still shows the person. */
    val appearancesMessage: String? = null,
    /** Set when the session ended and the screen must be left. */
    val exit: HomeExit? = null,
) {
    /** The facts line, "Born 1999 · Amsterdam, Netherlands"; empty when the server knows neither. */
    val facts: List<String>
        get() = listOfNotNull(person?.bornYear?.let(Strings::born), person?.birthplace)

    val appearancesLoading: Boolean get() = movies.loading || shows.loading

    /** Nothing to show under the biography: the list came back and held nothing. */
    val noAppearances: Boolean get() = !appearancesLoading && appearancesMessage == null && movies.items.isEmpty() && shows.items.isEmpty()
}

/** The causes of the page's messages, kept as they came: the text is worded from them whenever the state is read. */
private data class PersonFailures(val person: ApiError? = null, val appearances: ApiError? = null)

private fun PersonUiState.localized(failures: PersonFailures): PersonUiState = copy(
    message = failures.person?.let(LoadMessages::forError),
    appearancesMessage = failures.appearances?.let(LoadMessages::forError),
    movies = movies.copy(title = Strings.collectionMovies),
    shows = shows.copy(title = Strings.collectionShows),
)

/** A cast member's page: their picture and biography, and what they appear in within the library. */
class PersonViewModel(
    private val personId: String,
    private val library: LibraryRepository,
    connection: ConnectionManager,
) : ViewModel() {
    private val content = MutableStateFlow(PersonUiState())
    private val failures = MutableStateFlow(PersonFailures())

    private val localizedContent: Flow<PersonUiState> = combine(content, failures, Texts.language) { content, failures, _ -> content.localized(failures) }

    val uiState: StateFlow<PersonUiState> = combine(localizedContent, connection.sessionEnded()) { content, exit ->
        content.copy(exit = exit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonUiState())

    init {
        load()
    }

    /** Tries again whatever failed: the person, or what they appear in. */
    fun onRetry() {
        if (content.value.person == null) load() else loadAppearances()
    }

    private fun load() {
        content.update { PersonUiState() }
        failures.value = PersonFailures()
        viewModelScope.launch {
            when (val result = library.person(personId)) {
                is ApiResult.Success -> content.update { it.copy(person = result.value, loading = false) }

                is ApiResult.Failure -> {
                    content.update { it.copy(loading = false) }
                    failures.update { it.copy(person = result.error) }
                }
            }
        }
        loadAppearances()
    }

    private fun loadAppearances() {
        content.update { it.copy(movies = it.movies.copy(loading = true), shows = it.shows.copy(loading = true)) }
        failures.update { it.copy(appearances = null) }
        viewModelScope.launch {
            when (val result = library.appearancesOf(personId)) {
                is ApiResult.Success -> content.update {
                    it.copy(
                        movies = it.movies.copy(items = result.value.filter { item -> item.kind == ItemKind.Movie }, loading = false),
                        shows = it.shows.copy(items = result.value.filter { item -> item.kind == ItemKind.Series }, loading = false),
                    )
                }

                is ApiResult.Failure -> {
                    content.update { it.copy(movies = it.movies.copy(loading = false), shows = it.shows.copy(loading = false)) }
                    failures.update { it.copy(appearances = result.error) }
                }
            }
        }
    }
}
