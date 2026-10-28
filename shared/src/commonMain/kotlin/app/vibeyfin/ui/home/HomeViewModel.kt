package app.vibeyfin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.Download
import app.vibeyfin.domain.DownloadRepository
import app.vibeyfin.domain.DownloadTarget
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.FilterOption
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.PageSort
import app.vibeyfin.domain.PinLock
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.domain.SearchHistory
import app.vibeyfin.domain.WatchChanges
import app.vibeyfin.domain.WatchRepository
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.downloads.DownloadAction
import app.vibeyfin.ui.downloads.DownloadFlow
import app.vibeyfin.ui.downloads.RemovePrompt
import app.vibeyfin.ui.downloads.downloadParts
import app.vibeyfin.ui.downloads.profileOrNull
import app.vibeyfin.ui.common.PinGate
import app.vibeyfin.ui.common.PinPrompt
import app.vibeyfin.ui.common.PinReason
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.item.seasonIndex
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
    /** The libraries are being listed: until they are, grey rows hold the place of the rows they decide. */
    val loadingLibraries: Boolean = false,
    /** Continue watching: what the user is in the middle of, and the next episode of their shows. */
    val continueRow: ContinueRow = ContinueRow(),
    /** A pull to refresh is running; the cards and rows stay on screen while it does. */
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
     * The rows under Continue watching, in order: My List, Recently added in each video library (one failed "Recently
     * added" row when the libraries couldn't be listed), then a row for every genre the server lists. Only rows worth
     * showing (loading, failed or with items) are here.
     */
    val rows: List<MediaRow> = emptyList(),
    /** The featured items above Continue watching, in the order they page; empty until they are picked, and when nothing qualifies. */
    val heroes: List<MediaItem> = emptyList(),
    /** The hero is switched on and being picked: a grey card holds its place, so it does not just appear. */
    val heroLoading: Boolean = false,
    /** How many items the profile's hero features, 0 when it is off. From 2 the grey card keeps room for the page dots. */
    val heroSlots: Int = 0,
    /** Set when the hero's Play was tapped and the target is known; the screen opens it and calls `onHeroNavHandled`. */
    val heroNav: HeroNav? = null,
    /** A kids profile: the menu has no Sign out. */
    val kidsProfile: Boolean = false,
    /** The PIN sheet, while switching profile or server waits for the profile's PIN. */
    val pinPrompt: PinPrompt? = null,
    /** The profile's downloads by item id: what a Continue watching card's menu shows of its own. */
    val downloads: Map<String, Download> = emptyMap(),
    /** The server lets this user download. Taken as yes until it says otherwise, so the menu entry is not missing while that is read. */
    val canDownload: Boolean = true,
    /** The question before a download is deleted; null when none is asked. */
    val removePrompt: RemovePrompt? = null,
)

/** The hero: the items once picked, or [loading] while they are being picked; [slots] is the profile's setting. */
/** [ready] is false until the profile's hero setting has been read: Home waits for it, so the grey card is in its very first frame, not slid in later. */
private data class HeroState(val items: List<MediaItem> = emptyList(), val loading: Boolean = false, val ready: Boolean = false, val slots: Int = 0) {
    /** [items] with the one with [id] changed; the same when it is no longer featured. */
    fun withItem(id: String, change: (MediaItem) -> MediaItem) = copy(items = items.map { if (it.id == id) change(it) else it })
}

/** The sheet and the page to open, which change together. */
private data class CategoryUi(
    val sheet: FilterSheet? = null,
    val openList: HomeFilter? = null,
    val heroNav: HeroNav? = null,
)

/** Where the hero's Play goes: the player on an item, or (when no episode could be found) the item's page. */
sealed interface HeroNav {
    data class Play(val itemId: String) : HeroNav

    data class Details(val itemId: String) : HeroNav
}

/** How many libraries get a "Recently added" row. Every genre gets a row: there is no cap on Home. */
private const val MAX_LATEST_ROWS = 6

/** How many recently added titles, and how many favourites, the hero is picked from. */
private const val HERO_CANDIDATES = 40

/** How many genre rows load from the server at the same time; the rest wait their turn, top to bottom. */
private const val GENRE_ROWS_AT_ONCE = 4

/** The id of the My List row, the first of Home's rows. */
private const val MY_LIST_ID = "my-list"

/** The id of the row that stands in for the Recently added rows when the libraries couldn't be listed. */
private const val LIBRARIES_FAILED_ID = "latest:failed"

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
    private val profileSettings: ProfileSettings,
    private val searchHistory: SearchHistory,
    private val pins: PinLock,
    auth: AuthRepository,
    private val downloads: DownloadRepository,
) : ViewModel() {
    private val pin = PinGate(pins, auth, viewModelScope)
    private val downloadFlow = DownloadFlow(viewModelScope, downloads) { connection.state.value.profileOrNull() }
    private val canDownload = MutableStateFlow(true)
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
    // Picked once per session (a new profile or address picks again).
    private val hero = MutableStateFlow(HeroState())
    // False from a new session until Continue watching has answered once; the rows below wait for it (a later retry of it
    // does not hide them again).
    private val continueAnswered = MutableStateFlow(false)
    private val rows = combine(myListRow, latestRows, genreRows, continueAnswered) { myList, latest, genres, answered ->
        // Shown from top to bottom: each waits for the rows above it, Continue watching first.
        revealInOrder(listOfNotNull(myList) + latest + genres, aboveLoading = !answered).filter { it.isVisible }
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
                loadingLibraries = libraries.loading,
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

    private val content = combine(screen, actionMessage, categoryUi, rows, hero) { screen, message, categories, rows, hero ->
        // A signed-in Home waits for the hero setting to be read, so everything appears in the same first frame.
        if (screen.userName != null && !hero.ready) return@combine HomeUiState()
        screen.copy(
            heroes = hero.items,
            heroLoading = hero.loading,
            heroSlots = hero.slots,
            actionMessage = message,
            sheet = categories.sheet,
            openList = categories.openList,
            heroNav = categories.heroNav,
            rows = rows,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        content, profileSettings.kidsProfile, pin.prompt, downloadParts(connection, downloads, downloadFlow, canDownload),
    ) { content, kids, prompt, parts ->
        content.copy(
            kidsProfile = kids == true,
            pinPrompt = prompt,
            actionMessage = content.actionMessage ?: parts.message,
            downloads = parts.byId,
            canDownload = parts.canDownload,
            removePrompt = parts.removal,
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
                    // The grey card is up from the first state when the profile has the hero on, so it is there, not slid in later.
                    val slots = profileSettings.homeHeroItems.first() ?: ProfileSettings.DEFAULT_HOME_HERO_ITEMS
                    hero.value = HeroState(loading = slots > 0, ready = true, slots = slots)
                    continueAnswered.value = false
                    coroutineScope {
                        launch {
                            loadLibraries()
                            loadLatestRows(quiet = false)
                        }
                        launch { loadContinue(first = true) }
                        launch { loadGenreRows(quiet = false) }
                        launch { loadMyList(quiet = false) }
                        launch { loadHeroIfEnabled() }
                        launch { readDownloadPermission() }
                    }
                }
        }
        // The setting changed (from Settings, while Home waits underneath): the genre rows are listed again, keeping the ones still shown.
        viewModelScope.launch {
            profileSettings.homeGenreRows.filterNotNull().distinctUntilChanged().drop(1).collect {
                if (connection.state.value is ConnectionState.Connected) loadGenreRows(quiet = true)
            }
        }
        // The hero's number changed in Settings: it is picked again for the new number, or goes when switched off.
        viewModelScope.launch {
            profileSettings.homeHeroItems.filterNotNull().distinctUntilChanged().drop(1).collect { slots ->
                if (slots == 0) hero.value = HeroState(ready = true) else if (connection.state.value is ConnectionState.Connected) loadHeroIfEnabled()
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

    /** "Try again" on one row: only it reloads. */
    fun onRetryRow(id: String) {
        if (id == MY_LIST_ID) {
            myListRow.update { it?.copy(loading = true, message = null) }
            viewModelScope.launch { loadMyList(quiet = false) }
            return
        }
        if (id == LIBRARIES_FAILED_ID) {
            latestRows.updateRow(id) { it.copy(loading = true, message = null) }
            viewModelScope.launch {
                loadLibraries()
                loadLatestRows(quiet = false)
            }
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

    /** Whether this user may download is read once per session, quietly: it only decides whether the menu entry is there. */
    private suspend fun readDownloadPermission() {
        val profile = connection.state.value.profileOrNull() ?: return
        downloads.canDownload(profile)?.let { canDownload.value = it }
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

    /** "Try again" on the failure message: the mark that failed, or else the download that could not be asked for. */
    fun onRetryAction() {
        val card = failedMark
        if (card == null) {
            if (downloadFlow.message.value != null) downloadFlow.retry()
            return
        }
        failedMark = null
        onMarkWatched(card)
    }

    fun onDismissMessage() {
        actionMessage.value = null
        downloadFlow.dismissMessage()
    }

    /** "Download" in a Continue watching card's menu: a movie or an episode goes straight into the queue. */
    fun onDownload(cardId: String) {
        val card = continueRow.value.cards.firstOrNull { it.id == cardId } ?: return
        downloadFlow.start(DownloadTarget.Item(cardId), card.title, ask = false)
    }

    /** Pause, resume or remove (after asking) the download of a Continue watching card. */
    fun onDownloadAction(cardId: String, action: DownloadAction) {
        val download = uiState.value.downloads[cardId] ?: return
        downloadFlow.act(download, action)
    }

    fun onConfirmRemove() = downloadFlow.confirmRemove()

    fun onDismissRemoval() = downloadFlow.dismissRemoval()

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
        // Without the libraries there is nothing to ask for: one row says why, where their rows would be, and tries again.
        libraries.value.message?.let { message ->
            latestRows.value = listOf(MediaRow(LIBRARIES_FAILED_ID, Strings.recentlyAdded, loading = false, message = message))
            return
        }
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

    /** Every genre, biggest first, a row each; they are optional, so a failure to list them shows nothing. */
    private suspend fun loadGenreRows(quiet: Boolean) {
        val listed = library.genres()
        if (listed is ApiResult.Success) genreOptions = listed.value
        // The profile's setting: every genre, none, or the biggest few.
        val limit = profileSettings.homeGenreRows.first() ?: ProfileSettings.HOME_GENRES_ALL
        val top = (listed as? ApiResult.Success)?.value.orEmpty()
            .sortedWith(compareByDescending<FilterOption> { it.count ?: 0 }.thenBy { it.name.lowercase() })
            .let { sorted ->
                when {
                    limit == ProfileSettings.HOME_GENRES_NONE -> emptyList()
                    limit > 0 -> sorted.take(limit)
                    else -> sorted
                }
            }
        if (listed is ApiResult.Failure && quiet) return
        val filters = top.map { HomeFilter.Genre(it.id, it.name) }
        genreRows.update { old ->
            filters.map { filter ->
                old.firstOrNull { it.id == genreId(filter) }?.takeIf { quiet }
                    ?: MediaRow(genreId(filter), filter.name, seeAll = SeeAll.Genre(filter))
            }
        }
        // Every genre has a row, so only a few ask the server at a time, in the order they are shown.
        val turns = Semaphore(GENRE_ROWS_AT_ONCE)
        coroutineScope { filters.forEach { filter -> launch { turns.withPermit { loadGenreRow(filter, quiet) } } } }
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

    /**
     * Play on the hero. A movie plays at once. A show plays the episode it is up to (the next up, a half-watched one included), or
     * its first episode when it was never started or is finished; if that cannot be found the show's page opens instead.
     */
    fun onHeroPlay(itemId: String) {
        val item = hero.value.items.firstOrNull { it.id == itemId } ?: return
        if (item.kind != ItemKind.Series) {
            categoryUi.update { it.copy(heroNav = HeroNav.Play(item.id)) }
            return
        }
        viewModelScope.launch {
            val episode = (watch.nextUpOf(item.id) as? ApiResult.Success)?.value?.id ?: firstEpisodeOf(item.id)
            categoryUi.update { it.copy(heroNav = if (episode != null) HeroNav.Play(episode) else HeroNav.Details(item.id)) }
        }
    }

    /** The first episode of the first real season (not Specials), or of the first season there is. */
    private suspend fun firstEpisodeOf(seriesId: String): String? {
        val seasons = (library.seasons(seriesId) as? ApiResult.Success)?.value.orEmpty()
        val season = seasons.firstOrNull { (it.seasonIndex ?: 0) > 0 } ?: seasons.firstOrNull() ?: return null
        return (library.episodes(seriesId, season.id) as? ApiResult.Success)?.value?.firstOrNull()?.id
    }

    /** The screen has opened what the hero's Play asked for. */
    fun onHeroNavHandled() = categoryUi.update { it.copy(heroNav = null) }

    // The featured items whose My List write is in flight; a second tap on one waits for it.
    private val heroWriting = mutableSetOf<String>()

    /** "My List" on a featured item: shown at once, taken back with a message if the server can't save it. */
    fun onHeroMyList(itemId: String) {
        val before = hero.value.items.firstOrNull { it.id == itemId } ?: return
        if (!heroWriting.add(itemId)) return
        val target = !before.isFavorite
        actionMessage.value = null
        hero.update { it.withItem(itemId) { item -> item.copy(isFavorite = target) } }
        viewModelScope.launch {
            val result = watch.setFavorite(itemId, target)
            heroWriting.remove(itemId)
            if (result is ApiResult.Failure) {
                hero.update { it.withItem(itemId) { item -> item.copy(isFavorite = before.isFavorite) } }
                if (result.error != ApiError.Unauthorized) {
                    actionMessage.value = if (target) LoadMessages.ADD_TO_MY_LIST_FAILED else LoadMessages.REMOVE_FROM_MY_LIST_FAILED
                }
            }
        }
    }

    /** Picks the hero when the profile has it on; a grey card stands in until it is known, and goes if nothing qualifies. */
    private suspend fun loadHeroIfEnabled() {
        val slots = profileSettings.homeHeroItems.first() ?: ProfileSettings.DEFAULT_HOME_HERO_ITEMS
        if (slots == 0) {
            hero.value = HeroState(ready = true)
            return
        }
        hero.update { it.copy(loading = it.items.isEmpty(), slots = slots) }
        loadHero(slots)
    }

    /** Picks up to [count] featured items from what was added lately and from My List. A failure shows no hero: it is optional. */
    private suspend fun loadHero(count: Int) {
        val (added, favourites) = coroutineScope {
            val added = async { library.filteredPage(HomeFilter.New, PageSort.Added, limit = HERO_CANDIDATES, fields = "Genres") }
            val favourites = async { library.filteredPage(HomeFilter.MyList, PageSort.Added, limit = HERO_CANDIDATES, fields = "Genres") }
            added.await() to favourites.await()
        }
        val candidates = listOf(added, favourites).flatMap { (it as? ApiResult.Success)?.value?.items.orEmpty() }
        hero.value = HeroState(items = pickHeroes(candidates, count, Random.Default), loading = false, ready = true, slots = count)
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

    /** Leaves this profile signed in and goes back to "Who's watching?" on the same server, after its PIN if it asks. */
    fun onSwitchProfile() {
        val serverId = currentServerId() ?: return
        leaveProfile(HomeExit.WhoIsWatching(serverId))
    }

    /** Leaving for the servers list is leaving the profile too, so it asks for the PIN the same way. */
    fun onSwitchServer() {
        if (currentServerId() == null) {
            connection.switchProfile()
            exit.value = HomeExit.Servers
            return
        }
        leaveProfile(HomeExit.Servers)
    }

    /** A PIN was entered in the open PIN sheet. */
    fun onSubmitPin(pin: String) = this.pin.submitPin(pin)

    /** "Forgot PIN?": the PIN sheet asks for the profile's Jellyfin password instead. */
    fun onForgotPin() = pin.forgotPin()

    /** The Jellyfin password entered after "Forgot PIN?". */
    fun onSubmitPassword(password: String) = pin.submitPassword(password)

    fun onDismissPin() = pin.dismiss()

    private fun leaveProfile(to: HomeExit) {
        val account = (connection.state.value as? ConnectionState.Connected)?.account ?: return
        viewModelScope.launch {
            val leave: suspend () -> Unit = {
                connection.switchProfile()
                exit.value = to
            }
            if (pins.gates(account.serverId, account.userId).switchProfile) {
                pin.ask(PinReason.SwitchProfile, account.serverId, account.userId, account.name, onPass = leave)
            } else {
                leave()
            }
        }
    }

    /**
     * Revokes the token on the server, locks the profile here, forgets what it searched for, and goes back to "Who's watching?".
     * (An expired session keeps the history: that profile is coming back.)
     */
    fun onSignOut() {
        val serverId = currentServerId() ?: return
        viewModelScope.launch {
            // A kids profile has no Sign out; its menu hides it, and this keeps it that way.
            if (profileSettings.kidsProfile.first() == true) return@launch
            // While still signed in: the history is the signed-in profile's.
            searchHistory.clear()
            connection.signOut()
            exit.value = HomeExit.WhoIsWatching(serverId)
        }
    }

    private fun currentServerId() = (connection.state.value as? ConnectionState.Connected)?.server?.id
}
