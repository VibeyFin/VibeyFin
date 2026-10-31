package app.vibeyfin.ui.seerr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.DiscoverList
import app.vibeyfin.domain.SeerrConnection
import app.vibeyfin.domain.SeerrRepository
import app.vibeyfin.domain.SeerrState
import app.vibeyfin.domain.model.RequestState
import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.domain.model.SeerrPage
import app.vibeyfin.domain.model.SeerrRequest
import app.vibeyfin.domain.model.SeerrTitle
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** A Seerr list Home shows as a row, and its See all page opens. */
enum class SeerrListKind {
    Trending,
    PopularMovies,
    PopularShows,

    /** Upcoming movies and shows together, by release date. */
    ComingSoon,
}

/** One Seerr row on Home. It loads on its own and is hidden when it is empty or Seerr fails: Seerr is optional. */
data class SeerrRow(
    /** What See all opens; null for My requests, whose See all is the My requests page. */
    val kind: SeerrListKind?,
    val title: String,
    val titles: List<SeerrTitle> = emptyList(),
    val loading: Boolean = true,
    /** Coming soon: the tiles show the release day ("Dec 12") instead of the year and kind. */
    val showsDates: Boolean = false,
) {
    val isVisible: Boolean get() = loading || titles.isNotEmpty()
}

data class SeerrHomeUiState(
    /** "My requests", right after Continue watching; null while it loads, when there is nothing recent to show, or no Seerr. */
    val requests: SeerrRow? = null,
    /** Trending, Popular movies, Popular shows, Coming soon: after the library's rows, before the genre rows. Only visible rows. */
    val discover: List<SeerrRow> = emptyList(),
)

internal fun SeerrListKind.title(): String = when (this) {
    SeerrListKind.Trending -> Strings.seerrTrending
    SeerrListKind.PopularMovies -> Strings.seerrPopularMovies
    SeerrListKind.PopularShows -> Strings.seerrPopularShows
    SeerrListKind.ComingSoon -> Strings.seerrComingSoon
}

// One page of a list. Coming soon is two lists, movies and shows, put together by date.
internal suspend fun SeerrRepository.page(kind: SeerrListKind, page: Int): ApiResult<SeerrPage> = when (kind) {
    SeerrListKind.Trending -> discover(DiscoverList.Trending, page)

    SeerrListKind.PopularMovies -> discover(DiscoverList.PopularMovies, page)

    SeerrListKind.PopularShows -> discover(DiscoverList.PopularShows, page)

    SeerrListKind.ComingSoon -> coroutineScope {
        val movies = async { discover(DiscoverList.UpcomingMovies, page) }
        val shows = async { discover(DiscoverList.UpcomingShows, page) }
        val both = listOf(movies.await(), shows.await()).filterIsInstance<ApiResult.Success<SeerrPage>>().map { it.value }
        if (both.isEmpty()) {
            movies.await()
        } else {
            ApiResult.Success(SeerrPage(both.flatMap { it.titles }.sortedBy { it.releaseDate ?: "9999" }, page, both.maxOf { it.totalPages }))
        }
    }
}

/**
 * Seerr's request list says what was asked for but not what it is called or looks like, so each title is read once and kept for the
 * session. At most a few at a time, so a long list doesn't flood Seerr.
 */
internal class RequestTitles(private val repository: SeerrRepository) {
    private val known = mutableMapOf<Pair<SeerrMediaType, Int>, SeerrTitle>()
    private val gate = Semaphore(READS_AT_ONCE)

    /** The title of each request (with the request's own state on it), in order; a request whose title can't be read is left out. */
    suspend fun titlesOf(requests: List<SeerrRequest>): List<Pair<SeerrRequest, SeerrTitle>> = coroutineScope {
        requests.map { request ->
            async {
                val id = request.tmdbId ?: return@async null
                val key = request.type to id
                val title = known[key] ?: gate.withPermit {
                    (repository.title(request.type, id) as? ApiResult.Success)?.value?.title?.also { known[key] = it }
                }
                title?.let { request to it.copy(state = request.state) }
            }
        }.awaitAll().filterNotNull()
    }

    private companion object {
        const val READS_AT_ONCE = 4
    }
}

internal fun daysSince(clock: Clock, at: Instant?): Int? = at?.let { ((clock.now() - it) / 1.days).toInt().coerceAtLeast(0) }

/**
 * Seerr's rows on Home: My requests after Continue watching, and the discover rows before the genres. Shown only while the profile is
 * signed in to Seerr; each row loads on its own, and a failure hides it without a word.
 */
class SeerrHomeViewModel(
    private val seerr: SeerrConnection,
    private val repository: SeerrRepository,
    private val clock: Clock,
) : ViewModel() {
    private val content = MutableStateFlow(SeerrHomeUiState())
    val uiState: StateFlow<SeerrHomeUiState> = content.asStateFlow()

    private val titles = RequestTitles(repository)
    private var load: Job? = null

    init {
        viewModelScope.launch {
            // Again for another profile, another Seerr or a new sign-in; gone when Seerr is.
            seerr.state.map { (it as? SeerrState.Ready)?.let { ready -> ready.address to ready.user.id } }.distinctUntilChanged().collect { session ->
                load?.cancel()
                if (session == null) {
                    content.value = SeerrHomeUiState()
                } else {
                    load = viewModelScope.launch { loadAll() }
                }
            }
        }
    }

    /** Home's pull to refresh: Seerr is asked again, signing in again first if it couldn't be reached. */
    fun onRefresh() {
        viewModelScope.launch {
            seerr.retry()
            if (seerr.state.value is SeerrState.Ready) loadAll(quiet = true)
        }
    }

    /** Home came back on screen: My requests may have moved on. */
    fun onForeground() {
        if (seerr.state.value is SeerrState.Ready) viewModelScope.launch { loadRequests() }
    }

    private suspend fun loadAll(quiet: Boolean = false) = coroutineScope {
        if (!quiet) {
            content.value = SeerrHomeUiState(
                // My requests is often empty: it appears once it has something rather than holding grey posters that then vanish.
                requests = null,
                discover = SeerrListKind.entries.map { kind -> row(kind, emptyList(), loading = true) },
            )
        }
        launch { loadRequests() }
        SeerrListKind.entries.forEach { kind ->
            launch {
                val found = (repository.page(kind, 1) as? ApiResult.Success)?.value?.titles.orEmpty().take(ROW_SIZE)
                content.update { state -> state.copy(discover = state.discover.replaceRow(kind, row(kind, found, loading = false)).filter { it.isVisible }) }
            }
        }
    }

    // The profile's requests still on their way, and those that arrived or were declined in the last week; newest change first.
    private suspend fun loadRequests() {
        val recent = (repository.myRequests(page = 1, pageSize = REQUESTS_READ) as? ApiResult.Success)?.value?.requests.orEmpty().filter { request ->
            val days = daysSince(clock, request.updatedAt ?: request.createdAt)
            when (request.state) {
                RequestState.Requested, RequestState.Approved, RequestState.Failed -> true
                RequestState.Declined, RequestState.Available, RequestState.PartlyAvailable -> days == null || days <= RECENT_DAYS
                RequestState.NotRequested -> false
            }
        }.take(ROW_SIZE)
        val tiles = titles.titlesOf(recent).map { it.second }
        content.update { it.copy(requests = SeerrRow(null, Strings.seerrMyRequests, tiles, loading = false).takeIf { row -> row.isVisible }) }
    }

    private fun row(kind: SeerrListKind, found: List<SeerrTitle>, loading: Boolean) = SeerrRow(
        kind = kind,
        title = kind.title(),
        titles = found,
        loading = loading,
        showsDates = kind == SeerrListKind.ComingSoon,
    )

    private fun List<SeerrRow>.replaceRow(kind: SeerrListKind, row: SeerrRow): List<SeerrRow> = if (any { it.kind == kind }) map { if (it.kind == kind) row else it } else (this + row).sortedBy { it.kind?.ordinal }

    private companion object {
        const val ROW_SIZE = 20
        const val REQUESTS_READ = 40
        const val RECENT_DAYS = 7
    }
}

/** A discover list's See all page: a grid of posters, Movies and Shows chips where the list holds both, more pages as it scrolls. */
data class SeerrListUiState(
    val title: String = "",
    val titles: List<SeerrTitle> = emptyList(),
    val loading: Boolean = true,
    /** Why the first page didn't come; the page offers Try again. */
    val message: String? = null,
    val loadingMore: Boolean = false,
    /** Trending and Coming soon hold both kinds, so the page offers Movies and Shows chips; null for the others. */
    val typeFilter: SeerrMediaType? = null,
    val offersTypeFilter: Boolean = false,
    val showsDates: Boolean = false,
)

class SeerrListViewModel(
    private val kind: SeerrListKind,
    private val repository: SeerrRepository,
) : ViewModel() {
    private val all = MutableStateFlow<List<SeerrTitle>>(emptyList())
    private val content = MutableStateFlow(
        SeerrListUiState(
            title = kind.title(),
            offersTypeFilter = kind == SeerrListKind.Trending || kind == SeerrListKind.ComingSoon,
            showsDates = kind == SeerrListKind.ComingSoon,
        ),
    )
    val uiState: StateFlow<SeerrListUiState> = content.asStateFlow()

    private var page = 0
    private var pages = 1
    private var loading: Job? = null

    init {
        loadMore()
    }

    fun onRetry() {
        content.update { it.copy(loading = true, message = null) }
        loadMore()
    }

    /** The grid came near its end: the next page, if there is one. */
    fun onLoadMore() {
        if (page >= pages || loading?.isActive == true || content.value.message != null) return
        loadMore()
    }

    /** A chip: only movies, only shows, or (tapping the chosen one again) both. */
    fun onTypeFilter(type: SeerrMediaType?) {
        content.update { it.copy(typeFilter = if (it.typeFilter == type) null else type) }
        show()
    }

    private fun loadMore() {
        loading = viewModelScope.launch {
            content.update { it.copy(loadingMore = page > 0) }
            when (val result = repository.page(kind, page + 1)) {
                is ApiResult.Success -> {
                    page = result.value.page
                    pages = result.value.totalPages
                    all.value = (all.value + result.value.titles).distinctBy { it.type to it.tmdbId }
                    // The titles and the end of loading arrive together, so the page is never seen loaded and empty.
                    content.update { shown(it.copy(loading = false, loadingMore = false, message = null)) }
                }

                is ApiResult.Failure -> content.update {
                    // A later page failing leaves what is shown; the first one failing is the page's error state.
                    if (page == 0) it.copy(loading = false, loadingMore = false, message = SeerrMessages.UNREACHABLE_NOW) else it.copy(loadingMore = false)
                }
            }
        }
    }

    private fun show() {
        content.update(::shown)
    }

    private fun shown(state: SeerrListUiState) = state.copy(titles = state.typeFilter?.let { type -> all.value.filter { it.type == type } } ?: all.value)
}

/** The filters of the My requests page. */
enum class RequestFilter { All, Waiting, Approved, Available, Declined }

/** One request on the My requests page. */
data class RequestListItem(
    val request: SeerrRequest,
    val title: SeerrTitle,
    /** "Show · Seasons 1–2", "Movie · 2022". */
    val kind: String,
    /** The state's word ("Approved"). */
    val stateLabel: String,
    /** "Approved yesterday". */
    val changed: String,
)

data class MyRequestsUiState(
    val items: List<RequestListItem> = emptyList(),
    val filter: RequestFilter = RequestFilter.All,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val message: String? = null,
) {
    /** Nothing was ever requested: the page says how to start. */
    val empty: Boolean get() = !loading && message == null && items.isEmpty() && filter == RequestFilter.All
}

/** The profile's requests, newest change first, a page at a time, with filters by state. */
class MyRequestsViewModel(
    private val repository: SeerrRepository,
    private val clock: Clock,
) : ViewModel() {
    private val titles = RequestTitles(repository)
    private val loaded = MutableStateFlow<List<RequestListItem>>(emptyList())
    private val content = MutableStateFlow(MyRequestsUiState())
    val uiState: StateFlow<MyRequestsUiState> = content.asStateFlow()

    private var page = 0
    private var pages = 1
    private var loading: Job? = null

    init {
        loadMore()
    }

    fun onFilter(filter: RequestFilter) {
        content.update { it.copy(filter = filter) }
        show()
    }

    fun onLoadMore() {
        if (page >= pages || loading?.isActive == true || content.value.message != null) return
        loadMore()
    }

    fun onRetry() {
        content.update { it.copy(loading = true, message = null) }
        loadMore()
    }

    private fun loadMore() {
        loading = viewModelScope.launch {
            content.update { it.copy(loadingMore = page > 0) }
            when (val result = repository.myRequests(page = page + 1)) {
                is ApiResult.Success -> {
                    page = result.value.page
                    pages = result.value.pages
                    val items = titles.titlesOf(result.value.requests).map { (request, title) ->
                        RequestListItem(
                            request = request,
                            title = title,
                            kind = Strings.seerrRequestKind(request.type, title.year, request.seasons),
                            stateLabel = Strings.seerrStateLabel(request.state) ?: Strings.seerrStateLabel(RequestState.Requested).orEmpty(),
                            changed = Strings.seerrRequestWhen(request.state, daysSince(clock, request.updatedAt ?: request.createdAt)),
                        )
                    }
                    loaded.value = (loaded.value + items).distinctBy { it.request.id }
                    // The requests and the end of loading arrive together, so "nothing requested yet" never flashes by.
                    content.update { shown(it.copy(loading = false, loadingMore = false, message = null)) }
                }

                is ApiResult.Failure -> content.update {
                    if (page == 0) it.copy(loading = false, loadingMore = false, message = SeerrMessages.UNREACHABLE_NOW) else it.copy(loadingMore = false)
                }
            }
        }
    }

    private fun show() {
        content.update(::shown)
    }

    private fun shown(state: MyRequestsUiState): MyRequestsUiState = state.copy(
        items = loaded.value.filter { item ->
            when (state.filter) {
                RequestFilter.All -> true
                RequestFilter.Waiting -> item.request.state == RequestState.Requested
                RequestFilter.Approved -> item.request.state == RequestState.Approved
                RequestFilter.Available -> item.request.state == RequestState.Available || item.request.state == RequestState.PartlyAvailable
                RequestFilter.Declined -> item.request.state == RequestState.Declined
            }
        },
    )
}
