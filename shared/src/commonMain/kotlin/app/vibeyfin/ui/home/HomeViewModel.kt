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
import app.vibeyfin.domain.PageSort
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
    /** The category buttons under the header, in order. */
    val buttons: List<CategoryButton> = categoryButtons(),
    /** The Categories sheet while it is open. */
    val sheet: FilterSheet? = null,
    /** Set when a category page should open (a button or a sheet entry was chosen); the screen calls `onListOpened`. */
    val openList: HomeFilter? = null,
    /**
     * The rows between Continue watching and My media, in order: Recently added in each video library, then a few
     * genres. Only rows worth showing (loading, failed or with items) are here, and at most [MAX_ROWS] in all.
     */
    val rows: List<MediaRow> = emptyList(),
    /** My media waits for everything above it, so it comes in last: its grey tiles show until then. */
    val loadingMyMedia: Boolean = false,
)

/** The sheet and the page to open, which change together. */
private data class CategoryUi(
    val sheet: FilterSheet? = null,
    val openList: HomeFilter? = null,
)

/** Home holds at most 12 rows (`guardrails.md`); Continue watching and My media are two of them. */
private const val MAX_ROWS = 10

/** How many libraries get a "Recently added" row, and how many genres a row, before [MAX_ROWS] cuts. */
private const val MAX_LATEST_ROWS = 6
private const val MAX_GENRE_ROWS = 3

/** The id of the My List row, the first of Home's rows. */
private const val MY_LIST_ID = "my-list"

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

    private val latestRows = MutableStateFlow<List<MediaRow>>(emptyList())
    private val genreRows = MutableStateFlow<List<MediaRow>>(emptyList())
    // The first row under Continue watching; hidden while the user has nothing on My List.
    private val myListRow = MutableStateFlow<MediaRow?>(null)
    // False from a new session until Continue watching has answered once; the rows below wait for it (a later retry of it
    // does not hide them again).
    private val continueAnswered = MutableStateFlow(false)
    private val rows = combine(myListRow, latestRows, genreRows, continueAnswered) { myList, latest, genres, answered ->
        // Shown from top to bottom: each waits for the rows above it, Continue watching first.
        revealInOrder(listOfNotNull(myList) + latest + genres, aboveLoading = !answered).filter { it.isVisible }.take(MAX_ROWS)
    }

    private val categoryUi = MutableStateFlow(CategoryUi())
    private var sheetJob: Job? = null
    // The sheets' lists, fetched once per session when first opened.
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

    val uiState: StateFlow<HomeUiState> = combine(screen, actionMessage, categoryUi, rows) { screen, message, categories, rows ->
        screen.copy(
            actionMessage = message,
            sheet = categories.sheet,
            openList = categories.openList,
            rows = rows,
            loadingMyMedia = screen.loadingLibraries || screen.continueRow.loading && screen.continueRow.cards.isEmpty() || rows.any { it.loading },
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
                    // Another profile or address has its own genres.
                    sheetJob?.cancel()
                    categoryUi.value = CategoryUi()
                    genreOptions = null
                    latestRows.value = emptyList()
                    genreRows.value = emptyList()
                    myListRow.value = MediaRow(MY_LIST_ID, Strings.myList, seeAll = SeeAll.MyList)
                    continueAnswered.value = false
                    coroutineScope {
                        launch {
                            loadLibraries()
                            loadLatestRows(quiet = false)
                        }
                        launch { loadContinue(first = true) }
                        launch { loadGenreRows(quiet = false) }
                        launch { loadMyList(quiet = false) }
                    }
                }
        }
        // What was watched changed (a play session ended, something was marked): bring the row up to date.
        viewModelScope.launch {
            changes.events.collect {
                reloadContinue()
                // A favourite may have changed too.
                if (connection.state.value is ConnectionState.Connected) launch { loadMyList(quiet = true) }
            }
        }
    }

    fun onRetryLibraries() {
        viewModelScope.launch {
            loadLibraries()
            loadLatestRows(quiet = false)
        }
    }

    /** "Try again" on one row: only it reloads. */
    fun onRetryRow(id: String) {
        if (id == MY_LIST_ID) {
            myListRow.update { it?.copy(loading = true, message = null) }
            viewModelScope.launch { loadMyList(quiet = false) }
            return
        }
        val row = (latestRows.value + genreRows.value).firstOrNull { it.id == id } ?: return
        val to = row.seeAll
        viewModelScope.launch {
            when (to) {
                is SeeAll.Library -> {
                    latestRows.updateRow(id) { it.copy(loading = true, message = null) }
                    loadLatest(to.library, quiet = false)
                }
                is SeeAll.Genre -> {
                    genreRows.updateRow(id) { it.copy(loading = true, message = null) }
                    loadGenreRow(to.filter, quiet = false)
                }
                SeeAll.MyList, null -> Unit
            }
        }
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
                // Continue watching is asked first; the rest follow at once.
                coroutineScope {
                    launch { loadContinue(first = false) }
                    launch {
                        loadLibraries(quiet = true)
                        loadLatestRows(quiet = true)
                    }
                    launch { loadGenreRows(quiet = true) }
                    launch { loadMyList(quiet = true) }
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
    private suspend fun loadContinue(first: Boolean) {
        if (first) continueRow.value = ContinueRow(loading = true)
        val result = watch.continueWatching()
        continueLoadedAt = clock.now()
        continueAnswered.value = true
        when (result) {
            is ApiResult.Success -> continueRow.value = ContinueRow(cards = result.value.map { it.toContinueCard() })
            is ApiResult.Failure -> if (first) continueRow.value = ContinueRow(message = LoadMessages.forError(result.error))
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

    /** One "Recently added" row per library that holds video; the rows keep their order while each loads on its own. */
    private suspend fun loadLatestRows(quiet: Boolean) {
        val videoLibraries = libraries.value.items.filter { it.holdsVideo }.take(MAX_LATEST_ROWS)
        latestRows.update { old ->
            videoLibraries.map { lib ->
                // A refresh keeps what is on screen until the answer comes.
                old.firstOrNull { it.id == latestId(lib) }?.takeIf { quiet }
                    ?: MediaRow(latestId(lib), Strings.recentlyAddedIn(lib.title), seeAll = SeeAll.Library(lib))
            }
        }
        coroutineScope { videoLibraries.forEach { lib -> launch { loadLatest(lib, quiet) } } }
    }

    private suspend fun loadLatest(lib: MediaItem, quiet: Boolean) {
        val result = library.latest(lib.id)
        latestRows.updateRow(latestId(lib)) { row ->
            when (result) {
                is ApiResult.Success -> row.copy(items = result.value, loading = false, message = null)
                is ApiResult.Failure -> failed(row, result.error, quiet)
            }
        }
    }

    /** The genres with the most titles, a row each; they are optional, so a failure to list them shows nothing. */
    private suspend fun loadGenreRows(quiet: Boolean) {
        val listed = library.genres()
        if (listed is ApiResult.Success) genreOptions = listed.value
        val top = (listed as? ApiResult.Success)?.value.orEmpty()
            .sortedWith(compareByDescending<FilterOption> { it.count ?: 0 }.thenBy { it.name.lowercase() })
            .take(MAX_GENRE_ROWS)
        if (listed is ApiResult.Failure && quiet) return
        val filters = top.map { HomeFilter.Genre(it.id, it.name) }
        genreRows.update { old ->
            filters.map { filter ->
                old.firstOrNull { it.id == genreId(filter) }?.takeIf { quiet }
                    ?: MediaRow(genreId(filter), filter.name, seeAll = SeeAll.Genre(filter))
            }
        }
        coroutineScope { filters.forEach { filter -> launch { loadGenreRow(filter, quiet) } } }
    }

    private suspend fun loadGenreRow(filter: HomeFilter.Genre, quiet: Boolean) {
        val result = library.filteredPage(filter, PageSort.Rating, limit = LibraryRepository.ROW_LIMIT)
        genreRows.updateRow(genreId(filter)) { row ->
            when (result) {
                is ApiResult.Success -> row.copy(items = result.value.items, loading = false, message = null)
                is ApiResult.Failure -> failed(row, result.error, quiet)
            }
        }
    }

    /** What is on My List, newest additions first; the row is hidden while it is empty. */
    private suspend fun loadMyList(quiet: Boolean) {
        val result = library.filteredPage(HomeFilter.MyList, PageSort.Added, limit = LibraryRepository.ROW_LIMIT)
        myListRow.update { row ->
            val base = row ?: MediaRow(MY_LIST_ID, Strings.myList, seeAll = SeeAll.MyList)
            when (result) {
                is ApiResult.Success -> base.copy(items = result.value.items, loading = false, message = null)
                is ApiResult.Failure -> failed(base, result.error, quiet)
            }
        }
    }

    /** A refresh that fails leaves the row as it was; a first load or a retry shows why. */
    private fun failed(row: MediaRow, error: ApiError, quiet: Boolean) =
        if (quiet && row.items.isNotEmpty()) row.copy(loading = false) else row.copy(items = emptyList(), loading = false, message = LoadMessages.forError(error))

    private fun latestId(lib: MediaItem) = "latest:${lib.id}"

    private fun genreId(filter: HomeFilter.Genre) = "genre:${filter.id}"

    private fun MutableStateFlow<List<MediaRow>>.updateRow(id: String, transform: (MediaRow) -> MediaRow) =
        update { rows -> rows.map { if (it.id == id) transform(it) else it } }

    /**
     * A tap on a category button. Shows, Movies and New open their page at once; Studios and Categories open a
     * sheet to pick from, and the pick opens the page.
     */
    fun onCategory(kind: CategoryKind) {
        when (kind) {
            CategoryKind.Shows -> categoryUi.update { it.copy(openList = HomeFilter.Shows) }
            CategoryKind.Movies -> categoryUi.update { it.copy(openList = HomeFilter.Movies) }
            CategoryKind.New -> categoryUi.update { it.copy(openList = HomeFilter.New) }
            CategoryKind.Categories -> openSheet()
        }
    }

    /** An entry of the open sheet was chosen: its page opens and the sheet closes. */
    fun onPickOption(id: String) {
        val sheet = categoryUi.value.sheet ?: return
        val option = sheet.options.firstOrNull { it.id == id } ?: return
        val filter = HomeFilter.Genre(option.id, option.name)
        sheetJob?.cancel()
        categoryUi.value = CategoryUi(openList = filter)
    }

    /** The screen has opened the page asked for. */
    fun onListOpened() = categoryUi.update { it.copy(openList = null) }

    fun onDismissSheet() {
        sheetJob?.cancel()
        categoryUi.update { it.copy(sheet = null) }
    }

    /** "Try again" in the sheet. */
    fun onRetrySheet() {
        val sheet = categoryUi.value.sheet ?: return
        categoryUi.update { it.copy(sheet = sheet.copy(loading = true, message = null)) }
        loadSheet()
    }

    private fun openSheet() {
        val cached = genreOptions
        categoryUi.update {
            it.copy(
                sheet = FilterSheet(
                    kind = CategoryKind.Categories,
                    title = Strings.filterCategories,
                    options = cached.orEmpty(),
                    loading = cached == null,
                ),
            )
        }
        if (cached == null) loadSheet()
    }

    private fun loadSheet() {
        sheetJob?.cancel()
        sheetJob = viewModelScope.launch {
            val result = library.genres()
            if (result is ApiResult.Success) genreOptions = result.value
            categoryUi.update { ui ->
                val sheet = ui.sheet ?: return@update ui
                ui.copy(
                    sheet = when (result) {
                        is ApiResult.Success -> sheet.copy(options = result.value, loading = false, message = null)
                        is ApiResult.Failure -> sheet.copy(loading = false, message = LoadMessages.forError(result.error))
                    },
                )
            }
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
