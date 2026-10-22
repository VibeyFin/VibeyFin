package app.vibeyfin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.FilterOption
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.WatchChanges
import app.vibeyfin.domain.WatchRepository
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Where Home sends the user when they leave it. */
sealed interface HomeExit {
    data class WhoIsWatching(val serverId: String) : HomeExit

    data object Servers : HomeExit
}

data class HomeUiState(
    val userName: String? = null,
    val serverName: String? = null,
    /** The server's id, for routing back to "Who's watching?" on that server. */
    val serverId: String? = null,
    /** The libraries this profile can open: Movies, Shows, ... */
    val libraries: List<MediaItem> = emptyList(),
    val loadingLibraries: Boolean = false,
    /** Why the libraries didn't load, with what to try. */
    val librariesMessage: String? = null,
    /** Continue watching: what the user is in the middle of, and the next episode of their shows. */
    val continueRow: ContinueRow = ContinueRow(),
    /** A pull to refresh is running; the cards and libraries stay on screen while it does. */
    val refreshing: Boolean = false,
    /** A change that couldn't be saved on the server and was taken back, with what to try. */
    val actionMessage: String? = null,
    /** Set when the screen should be left: the user chose to, or the session ended. */
    val exit: HomeExit? = null,
    /** What Home is limited to; null when it shows everything, as it does on every launch. */
    val filter: HomeFilter? = null,
    /** The category buttons under the header, with the active one marked. */
    val buttons: List<CategoryButton> = categoryButtons(null),
    /** The Studios or Categories sheet while it is open. */
    val sheet: FilterSheet? = null,
    /** "Recently added" under a filter; null with no filter, when the libraries are shown instead. */
    val recentRow: RecentRow? = null,
) {
    /** A filter is on and both Continue watching and Recently added came back empty. */
    val nothingMatchesFilter: Boolean
        get() = filter != null && recentRow != null &&
            !continueRow.loading && continueRow.message == null && continueRow.cards.isEmpty() &&
            !recentRow.loading && recentRow.message == null && recentRow.items.isEmpty()
}

/** The filter, the sheet and the row under it, which change together. */
private data class FilterUi(
    val filter: HomeFilter? = null,
    val sheet: FilterSheet? = null,
    val recent: RecentRow? = null,
)

private data class Libraries(
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
)

/** Coming back to Home reloads Continue watching if it has been this long since it last loaded. */
private val FOREGROUND_REFRESH_AFTER = 60.seconds

class HomeViewModel(
    private val connection: ConnectionManager,
    private val library: LibraryRepository,
    private val watch: WatchRepository,
    private val changes: WatchChanges,
    private val clock: Clock,
) : ViewModel() {
    private val exit = MutableStateFlow<HomeExit?>(null)
    // Signed in, the libraries are always on their way, so the first thing to show is loading, not empty.
    private val libraries = MutableStateFlow(Libraries(loading = true))
    private val continueRow = MutableStateFlow(ContinueRow(loading = true))
    private val refreshing = MutableStateFlow(false)

    private var continueReload: Job? = null
    private var refresh: Job? = null
    private var continueLoadedAt: Instant? = null

    private val actionMessage = MutableStateFlow<String?>(null)

    private val filterUi = MutableStateFlow(FilterUi())
    private var filterJob: Job? = null
    private var sheetJob: Job? = null
    // The sheets' lists, fetched once per session when first opened.
    private var studioOptions: List<FilterOption>? = null
    private var genreOptions: List<FilterOption>? = null

    private val screen = combine(connection.state, exit, libraries, continueRow, refreshing) { state, exit, libraries, continueRow, refreshing ->
        when (state) {
            is ConnectionState.Connected -> HomeUiState(
                userName = state.account.name,
                serverName = state.server.name,
                serverId = state.server.id,
                libraries = libraries.items,
                loadingLibraries = libraries.loading,
                librariesMessage = libraries.message,
                continueRow = continueRow,
                refreshing = refreshing,
                exit = exit,
            )
            // A rejected token ended the session: back to "Who's watching?", where the tile is now locked.
            is ConnectionState.Expired -> HomeUiState(
                serverId = state.serverId,
                exit = HomeExit.WhoIsWatching(state.serverId),
            )
            ConnectionState.Disconnected -> HomeUiState(exit = exit)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(screen, actionMessage, filterUi) { screen, message, filters ->
        screen.copy(
            actionMessage = message,
            filter = filters.filter,
            buttons = categoryButtons(filters.filter),
            sheet = filters.sheet,
            recentRow = filters.recent,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Load for the session we are in, and again if it becomes another profile or address.
        viewModelScope.launch {
            connection.state
                .map { (it as? ConnectionState.Connected)?.let { c -> Triple(c.server.id, c.account.userId, c.api.baseUrl) } }
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest {
                    // Another profile or address starts unfiltered, with its own studios and genres.
                    filterJob?.cancel()
                    sheetJob?.cancel()
                    filterUi.value = FilterUi()
                    studioOptions = null
                    genreOptions = null
                    coroutineScope {
                        launch { loadLibraries() }
                        launch { loadContinue(first = true) }
                    }
                }
        }
        // What was watched changed (a play session ended, something was marked): bring the row up to date.
        viewModelScope.launch { changes.events.collect { reloadContinue() } }
    }

    fun onRetryLibraries() {
        viewModelScope.launch { loadLibraries() }
    }

    private val marking = mutableSetOf<String>()
    private var failedMark: String? = null

    /**
     * "Mark as watched" on a Continue watching card: the card goes at once and the server is told after.
     * If that fails the card comes back where it was, with a message. A rejected token ends the session
     * instead, so no message.
     */
    fun onMarkWatched(cardId: String) {
        val row = continueRow.value
        val index = row.cards.indexOfFirst { it.id == cardId }
        if (index < 0 || !marking.add(cardId)) return
        val card = row.cards[index]
        actionMessage.value = null
        continueRow.value = row.copy(cards = row.cards - card)
        viewModelScope.launch {
            val result = watch.setPlayed(cardId, played = true)
            marking.remove(cardId)
            if (result is ApiResult.Failure) {
                continueRow.update { current ->
                    if (current.cards.any { it.id == cardId }) {
                        current
                    } else {
                        current.copy(cards = current.cards.toMutableList().apply { add(index.coerceAtMost(size), card) })
                    }
                }
                if (result.error != ApiError.Unauthorized) {
                    failedMark = cardId
                    actionMessage.value = LoadMessages.MARK_WATCHED_FAILED
                }
            }
        }
    }

    /** "Try again" on the failure message. */
    fun onRetryAction() {
        val card = failedMark ?: return
        failedMark = null
        onMarkWatched(card)
    }

    fun onDismissMessage() {
        actionMessage.value = null
    }

    fun onRetryContinue() {
        continueReload?.cancel()
        continueReload = viewModelScope.launch { loadContinue(first = true) }
    }

    /** Pull to refresh: both reload, the old content stays up, and a failure leaves it as it was. */
    fun onRefresh() {
        if (connection.state.value !is ConnectionState.Connected) return
        refresh?.cancel()
        refresh = viewModelScope.launch {
            refreshing.value = true
            try {
                coroutineScope {
                    launch { loadContinue(first = false) }
                    launch { loadLibraries(quiet = true) }
                }
            } finally {
                refreshing.value = false
            }
        }
    }

    /**
     * The app came back to the foreground, where the user may have watched something on another
     * device. Reloads Continue watching unless it was loaded a minute ago or less.
     */
    fun onForeground() {
        val loadedAt = continueLoadedAt ?: return
        if (clock.now() - loadedAt >= FOREGROUND_REFRESH_AFTER) reloadContinue()
    }

    private fun reloadContinue() {
        if (connection.state.value !is ConnectionState.Connected) return
        continueReload?.cancel()
        continueReload = viewModelScope.launch { loadContinue(first = false) }
    }

    /**
     * [first] puts the skeleton up (a new session, or "Try again"); otherwise the cards on screen stay
     * until the answer replaces them, and a failure leaves them as they were.
     */
    private suspend fun loadContinue(first: Boolean, filterChanged: Boolean = false) {
        if (first) continueRow.value = ContinueRow(loading = true)
        val asked = filterUi.value.filter
        val result = watch.continueWatching(asked, clock.now())
        // Another filter was picked while this was out: its own load answers, and this one must not replace it.
        if (filterUi.value.filter != asked) return
        continueLoadedAt = clock.now()
        when (result) {
            is ApiResult.Success -> continueRow.value = ContinueRow(cards = result.value.map { it.toContinueCard() })
            // Cards left from another filter would be wrong under this one, so a changed filter shows the failure.
            is ApiResult.Failure -> if (first || filterChanged) continueRow.value = ContinueRow(message = LoadMessages.forError(result.error))
        }
    }

    /** The row under a filter. The old items stay up while a changed filter loads; only the first load is a skeleton. */
    private suspend fun loadRecent(asked: HomeFilter) {
        if (filterUi.value.recent?.items.isNullOrEmpty()) filterUi.update { it.copy(recent = RecentRow(loading = true)) }
        val result = library.recentlyAdded(asked, clock.now())
        if (filterUi.value.filter != asked) return
        val row = when (result) {
            is ApiResult.Success -> RecentRow(items = result.value, loading = false)
            is ApiResult.Failure -> RecentRow(loading = false, message = LoadMessages.forError(result.error))
        }
        filterUi.update { it.copy(recent = row) }
    }

    /**
     * A tap on a category button. Shows, Movies and New filter at once, and a second tap clears them. Studios and
     * Categories open their sheet, whether or not they are already the active filter.
     */
    fun onCategory(kind: CategoryKind) {
        when (kind) {
            CategoryKind.Shows -> toggle(HomeFilter.Shows)
            CategoryKind.Movies -> toggle(HomeFilter.Movies)
            CategoryKind.New -> toggle(HomeFilter.New)
            CategoryKind.Studios, CategoryKind.Categories -> openSheet(kind)
        }
    }

    /** The × on the active button. */
    fun onClearFilter() = setFilter(null)

    /** A row in the open sheet was picked: it becomes the filter and the sheet closes. */
    fun onPickOption(id: String) {
        val sheet = filterUi.value.sheet ?: return
        val option = sheet.options.firstOrNull { it.id == id } ?: return
        setFilter(if (sheet.kind == CategoryKind.Studios) HomeFilter.Studio(option.id, option.name) else HomeFilter.Genre(option.id, option.name))
    }

    fun onDismissSheet() {
        sheetJob?.cancel()
        filterUi.update { it.copy(sheet = null) }
    }

    /** "Try again" in the sheet. */
    fun onRetrySheet() {
        val sheet = filterUi.value.sheet ?: return
        filterUi.update { it.copy(sheet = sheet.copy(loading = true, message = null)) }
        loadSheet(sheet.kind)
    }

    /** "Try again" on the Recently added row. */
    fun onRetryRecent() {
        val asked = filterUi.value.filter ?: return
        filterJob?.cancel()
        filterJob = viewModelScope.launch { loadRecent(asked) }
    }

    private fun toggle(filter: HomeFilter) = setFilter(if (filterUi.value.filter == filter) null else filter)

    /** The last tap wins: loads for a filter that is no longer the active one are dropped when they answer. */
    private fun setFilter(filter: HomeFilter?) {
        if (connection.state.value !is ConnectionState.Connected) return
        sheetJob?.cancel()
        filterUi.update { it.copy(filter = filter, sheet = null, recent = if (filter == null) null else it.recent) }
        filterJob?.cancel()
        continueReload?.cancel()
        filterJob = viewModelScope.launch {
            coroutineScope {
                launch { loadContinue(first = false, filterChanged = true) }
                if (filter != null) launch { loadRecent(filter) }
            }
        }
    }

    private fun openSheet(kind: CategoryKind) {
        val filter = filterUi.value.filter
        val cached = if (kind == CategoryKind.Studios) studioOptions else genreOptions
        val sheet = FilterSheet(
            kind = kind,
            title = if (kind == CategoryKind.Studios) Strings.filterStudios else Strings.filterCategories,
            options = cached.orEmpty(),
            loading = cached == null,
            pickedId = (filter as? HomeFilter.Studio)?.id.takeIf { kind == CategoryKind.Studios }
                ?: (filter as? HomeFilter.Genre)?.id.takeIf { kind == CategoryKind.Categories },
        )
        filterUi.update { it.copy(sheet = sheet) }
        if (cached == null) loadSheet(kind)
    }

    private fun loadSheet(kind: CategoryKind) {
        sheetJob?.cancel()
        sheetJob = viewModelScope.launch {
            val result = if (kind == CategoryKind.Studios) library.studios() else library.genres()
            if (result is ApiResult.Success) {
                if (kind == CategoryKind.Studios) studioOptions = result.value else genreOptions = result.value
            }
            filterUi.update { ui ->
                val sheet = ui.sheet?.takeIf { it.kind == kind } ?: return@update ui
                ui.copy(
                    sheet = when (result) {
                        is ApiResult.Success -> sheet.copy(options = result.value, loading = false, message = null)
                        is ApiResult.Failure -> sheet.copy(loading = false, message = LoadMessages.forError(result.error))
                    },
                )
            }
        }
    }

    private suspend fun loadLibraries(quiet: Boolean = false) {
        val before = libraries.value
        if (!quiet || before.items.isEmpty()) libraries.value = Libraries(loading = true)
        libraries.value = when (val result = library.libraries()) {
            is ApiResult.Success -> Libraries(items = result.value)
            is ApiResult.Failure -> if (quiet && before.items.isNotEmpty()) before else Libraries(message = LoadMessages.forError(result.error))
        }
    }

    /** Leaves this profile signed in and goes back to "Who's watching?" on the same server. */
    fun onSwitchProfile() {
        val serverId = currentServerId() ?: return
        connection.switchProfile()
        exit.value = HomeExit.WhoIsWatching(serverId)
    }

    fun onSwitchServer() {
        connection.switchProfile()
        exit.value = HomeExit.Servers
    }

    /** Revokes the token on the server, locks the profile here, and goes back to "Who's watching?". */
    fun onSignOut() {
        val serverId = currentServerId() ?: return
        viewModelScope.launch {
            connection.signOut()
            exit.value = HomeExit.WhoIsWatching(serverId)
        }
    }

    private fun currentServerId() = (connection.state.value as? ConnectionState.Connected)?.server?.id
}
