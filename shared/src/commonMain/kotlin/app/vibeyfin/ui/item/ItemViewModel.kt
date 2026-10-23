package app.vibeyfin.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.WatchChanges
import app.vibeyfin.domain.WatchRepository
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.home.MediaRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The selected season's episodes: while another season loads the old list stays on screen. */
data class EpisodeList(
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = false,
    /** Why the season's episodes didn't load, with what to try. */
    val message: String? = null,
)

/** A show's seasons, which one is selected, and its episodes. Empty for a movie or an episode. */
data class SeasonsState(
    val seasons: List<MediaItem> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
    val selectedId: String? = null,
    val episodes: EpisodeList = EpisodeList(),
    /** The episode Play starts: the show's next up, else its first. Marked current in the list. */
    val startEpisode: MediaItem? = null,
    /** What Play starts is known (or known to be nothing): until then the page shows a grey button where Play will be. */
    val playSettled: Boolean = false,
)

/** The trailer the page can play: one the server holds, which the player opens, or a page on the web, which the system opens. */
sealed interface Trailer {
    data class Local(val itemId: String) : Trailer

    data class Remote(val url: String) : Trailer
}

data class ItemUiState(
    val item: MediaItem? = null,
    /** The short facts under the title: "S1 E3", year, age rating, runtime or seasons, rating. */
    val facts: List<String> = emptyList(),
    val loading: Boolean = true,
    /** Why the item didn't load, with what to try. */
    val message: String? = null,
    /** A change that couldn't be saved on the server and was taken back, with what to try. */
    val actionMessage: String? = null,
    /** The trailer under Play; null when there is none, and then the page has no Trailer button. */
    val trailer: Trailer? = null,
    /** "More like this"; null until the page is read, and not [MediaRow.isVisible] when the server knows nothing like it. */
    val similar: MediaRow? = null,
    /** "In collections": the box sets the item is part of; not [MediaRow.isVisible] when it is in none. Null until the page is read. */
    val collections: MediaRow? = null,
    /** "Extras": behind the scenes and the like; not [MediaRow.isVisible] when the item has none. Null until the page is read. */
    val extras: MediaRow? = null,
    /** A show's seasons and episodes. */
    val seasons: SeasonsState = SeasonsState(),
    /** Set when the session ended and the screen must be left. */
    val exit: HomeExit? = null,
) {
    /** The one badge for the picture ("4K", "Dolby Vision"), shown beside the facts; null when there is none. */
    val badge: String? get() = item?.formatBadge()

    /** The server says the user has watched it: the page shows a check icon beside the facts. */
    val watched: Boolean get() = item?.played == true

    /** On "My List" (a favourite). */
    val isFavorite: Boolean get() = item?.isFavorite == true

    /** The menu entry that flips the watched mark, worded for what it will do. */
    val markLabel: String
        get() = when {
            isSeries -> if (item?.played == true) Strings.markSeriesUnwatched else Strings.markSeriesWatched
            item?.played == true -> Strings.markUnwatched
            else -> Strings.markWatched
        }

    /** The file's details, collapsed at the bottom of the page; empty when the server sent none. */
    val technical: List<TechnicalLine> get() = item?.technicalLines().orEmpty()

    private val isSeries: Boolean get() = item?.kind == ItemKind.Series

    /** What Play opens the player on: the item itself, or for a show the episode to watch next. */
    val playTarget: MediaItem? get() = if (isSeries) seasons.startEpisode else item?.takeIf { it.canPlay }

    /** A show's Play is on its way: the page draws a grey button of the same size so Play does not pop in. */
    val playLoading: Boolean get() = isSeries && playTarget == null && !seasons.playSettled

    /** Whether the page offers Play. */
    val canPlay: Boolean get() = playTarget != null

    /** Whether the page offers the watched mark: for the item itself, and for a show as a whole (all its episodes). */
    val canMark: Boolean get() = item?.canPlay == true || isSeries

    private val resuming: Boolean get() = playTarget?.let { !it.played && it.resumePositionMs > 0 } == true

    /** The primary button: "Play", "Resume · 38m left", and for a show which episode: "Play S2 E4", "Resume S2 E4 from 12:30". */
    val playLabel: String
        get() {
            val target = playTarget ?: return Strings.play
            if (isSeries) {
                val episode = target.episodeLabel() ?: return if (resuming) Strings.resume else Strings.play
                return if (resuming) Strings.resumeEpisodeFrom(episode, target.resumePositionMs) else Strings.playEpisode(episode)
            }
            if (!resuming) return Strings.play
            return target.runtimeMs?.let { Strings.resumeTimeLeft(it - target.resumePositionMs) } ?: Strings.resume
        }

    /** Whether "Start over" is offered beside the primary button: only when there is something to resume. */
    val canStartOver: Boolean get() = resuming
}

/** One item's page: its metadata, its cast and what is like it, and for a show its seasons and episodes. */
class ItemViewModel(
    private val itemId: String,
    private val library: LibraryRepository,
    connection: ConnectionManager,
    private val watch: WatchRepository,
    changes: WatchChanges,
) : ViewModel() {
    private val content = MutableStateFlow(ItemUiState())
    private var episodesJob: Job? = null

    val uiState: StateFlow<ItemUiState> = combine(content, connection.sessionEnded()) { content, exit ->
        content.copy(exit = exit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemUiState())

    init {
        load()
        // What was watched changed (playback ended): bring the Resume button and the marks up to date, quietly.
        viewModelScope.launch { changes.events.collect { refresh() } }
    }

    fun onRetry() = load()

    /** "Try again" on "More like this". */
    fun onRetrySimilar() {
        viewModelScope.launch { loadSimilar() }
    }

    /** "Try again" on "Extras". */
    fun onRetryExtras() {
        viewModelScope.launch { loadExtras() }
    }

    /** "Try again" on "In collections". */
    fun onRetryCollections() {
        viewModelScope.launch { loadCollections() }
    }

    /** "Try again" on a show's seasons. */
    fun onRetrySeasons() {
        viewModelScope.launch { loadSeasons() }
    }

    /** "Try again" on the selected season's episodes. */
    fun onRetryEpisodes() {
        val season = content.value.seasons.selectedId ?: return
        selectSeason(season, force = true)
    }

    /** A season button was tapped: the list swaps in place, and the old one stays until the new one is there. */
    fun onSelectSeason(seasonId: String) = selectSeason(seasonId, force = false)

    private fun selectSeason(seasonId: String, force: Boolean) {
        val current = content.value.seasons
        if (!force && current.selectedId == seasonId) return
        episodesJob?.cancel()
        // The old season's cards go at once, so the page shows the new season loading, not the old one under another name.
        content.update { it.copy(seasons = it.seasons.copy(selectedId = seasonId, episodes = EpisodeList(loading = true))) }
        episodesJob = viewModelScope.launch { loadEpisodes(seasonId, setStart = false) }
    }

    /** Reads the item again without the spinner; a failure leaves the page as it was. */
    private suspend fun refresh() {
        val shown = content.value.item ?: return
        val result = library.item(itemId) as? ApiResult.Success ?: return
        content.update { it.copy(item = result.value, facts = result.value.facts()) }
        if (shown.kind == ItemKind.Series) {
            val next = watch.nextUpOf(itemId) as? ApiResult.Success
            if (next != null) content.update { it.copy(seasons = it.seasons.copy(startEpisode = next.value ?: it.seasons.startEpisode)) }
            // The marks in the list follow what was just watched.
            content.value.seasons.selectedId?.let { season ->
                (library.episodes(itemId, season) as? ApiResult.Success)?.let { episodes ->
                    content.update { it.copy(seasons = it.seasons.copy(episodes = EpisodeList(episodes.value))) }
                }
            }
        }
    }

    private var writing = false

    /**
     * Flips the watched mark. The page shows the change at once and the server is told after; if that
     * fails the page goes back and says so. A rejected token ends the session instead, so no message.
     */
    fun onTogglePlayed() {
        val before = content.value.item ?: return
        if (!(before.canPlay || before.kind == ItemKind.Series) || writing) return
        writing = true
        val target = !before.played
        val shown = before.copy(played = target, resumePositionMs = 0)
        content.update { it.copy(item = shown, facts = shown.facts(), actionMessage = null) }
        viewModelScope.launch {
            val result = watch.setPlayed(itemId, target)
            writing = false
            if (result is ApiResult.Failure) {
                content.update { it.copy(item = before, facts = before.facts(), actionMessage = failureMessage(result.error, target)) }
            } else if (before.kind == ItemKind.Series) {
                // Every episode changed with it: the list's marks and what Play starts are read again.
                loadSeasons()
            }
        }
    }

    /** The watched mark of an episode in the list, from its long-press menu: shown at once, taken back if it can't be saved. */
    fun onToggleEpisodePlayed(episodeId: String) {
        val before = content.value.seasons.episodes.items.firstOrNull { it.id == episodeId } ?: return
        val target = !before.played
        replaceEpisode(before.copy(played = target, resumePositionMs = 0))
        viewModelScope.launch {
            val result = watch.setPlayed(episodeId, target)
            if (result is ApiResult.Failure) {
                replaceEpisode(before)
                content.update { it.copy(actionMessage = failureMessage(result.error, target)) }
            }
        }
    }

    private fun replaceEpisode(episode: MediaItem) = content.update { state ->
        state.copy(seasons = state.seasons.copy(episodes = state.seasons.episodes.copy(items = state.seasons.episodes.items.map { if (it.id == episode.id) episode else it })))
    }

    private fun failureMessage(error: ApiError, markedWatched: Boolean) = when {
        error == ApiError.Unauthorized -> null
        markedWatched -> LoadMessages.MARK_WATCHED_FAILED
        else -> LoadMessages.MARK_UNWATCHED_FAILED
    }

    private var writingFavorite = false

    /** Which change last failed, so "Try again" repeats that one. */
    private var failedFavorite = false

    /**
     * Puts the item on "My List" or takes it off. The page shows the change at once and the server is told after; if that fails
     * the page goes back and says so. A rejected token ends the session instead, so no message.
     */
    fun onToggleFavorite() {
        val before = content.value.item ?: return
        if (writingFavorite) return
        writingFavorite = true
        failedFavorite = false
        val target = !before.isFavorite
        content.update { it.copy(item = before.copy(isFavorite = target), actionMessage = null) }
        viewModelScope.launch {
            val result = watch.setFavorite(itemId, target)
            writingFavorite = false
            if (result is ApiResult.Failure) {
                failedFavorite = result.error != ApiError.Unauthorized
                val message = when {
                    result.error == ApiError.Unauthorized -> null
                    target -> LoadMessages.ADD_TO_MY_LIST_FAILED
                    else -> LoadMessages.REMOVE_FROM_MY_LIST_FAILED
                }
                content.update { it.copy(item = it.item?.copy(isFavorite = before.isFavorite), actionMessage = message) }
            }
        }
    }

    /** "Try again" on the failure message: the page was taken back, so doing the change again repeats it. */
    fun onRetryAction() = if (failedFavorite) onToggleFavorite() else onTogglePlayed()

    fun onDismissMessage() = content.update { it.copy(actionMessage = null) }

    private fun load() {
        episodesJob?.cancel()
        content.value = ItemUiState(loading = true)
        viewModelScope.launch {
            when (val result = library.item(itemId)) {
                is ApiResult.Success -> {
                    val item = result.value
                    content.value = ItemUiState(item = item, facts = item.facts(), loading = false, trailer = item.remoteTrailerUrl?.takeIf { item.canHaveTrailer }?.let { Trailer.Remote(it) })
                    // The rest of the page loads on its own: each part fails and retries alone.
                    coroutineScope {
                        launch { loadSimilar() }
                        // A trailer the server holds plays in the app and wins over one on the web.
                        if (item.canHaveTrailer && item.localTrailerCount > 0) launch { loadLocalTrailer() }
                        // Movies and shows can be in collections; an episode or a season is not.
                        if (item.kind == ItemKind.Movie || item.kind == ItemKind.Series) launch { loadCollections() }
                        if (item.kind == ItemKind.Movie || item.kind == ItemKind.Series) launch { loadExtras() }
                        if (item.kind == ItemKind.Series) launch { loadSeasons() }
                    }
                }
                is ApiResult.Failure -> content.value = ItemUiState(loading = false, message = LoadMessages.forError(result.error))
            }
        }
    }

    private suspend fun loadLocalTrailer() {
        val trailer = (library.localTrailer(itemId) as? ApiResult.Success)?.value ?: return
        content.update { it.copy(trailer = Trailer.Local(trailer.id)) }
    }

    private suspend fun loadExtras() {
        content.update { it.copy(extras = MediaRow("extras", Strings.extras, loading = true)) }
        val result = library.specialFeatures(itemId)
        content.update {
            it.copy(
                extras = when (result) {
                    is ApiResult.Success -> MediaRow("extras", Strings.extras, items = result.value, loading = false)
                    is ApiResult.Failure -> MediaRow("extras", Strings.extras, loading = false, message = LoadMessages.forError(result.error))
                },
            )
        }
    }

    private suspend fun loadCollections() {
        content.update { it.copy(collections = MediaRow("collections", Strings.inCollections, loading = true)) }
        val result = library.collectionsOf(itemId)
        content.update {
            it.copy(
                collections = when (result) {
                    is ApiResult.Success -> MediaRow("collections", Strings.inCollections, items = result.value, loading = false)
                    is ApiResult.Failure -> MediaRow("collections", Strings.inCollections, loading = false, message = LoadMessages.forError(result.error))
                },
            )
        }
    }

    private suspend fun loadSimilar() {
        content.update { it.copy(similar = MediaRow("similar", Strings.moreLikeThis, loading = true)) }
        val result = library.similar(itemId)
        content.update {
            it.copy(
                similar = when (result) {
                    is ApiResult.Success -> MediaRow("similar", Strings.moreLikeThis, items = result.value, loading = false)
                    is ApiResult.Failure -> MediaRow("similar", Strings.moreLikeThis, loading = false, message = LoadMessages.forError(result.error))
                },
            )
        }
    }

    /** The seasons and the episode to watch next, then the episodes of the season that episode is in. */
    private suspend fun loadSeasons() {
        // A reload (after marking the whole show) keeps the Play button it had.
        content.update { it.copy(seasons = SeasonsState(loading = true, startEpisode = it.seasons.startEpisode, playSettled = it.seasons.playSettled)) }
        val (seasons, next) = coroutineScope {
            val seasons = async { library.seasons(itemId) }
            val next = async { watch.nextUpOf(itemId) }
            seasons.await() to next.await()
        }
        if (seasons is ApiResult.Failure) {
            content.update { it.copy(seasons = SeasonsState(message = LoadMessages.forError(seasons.error), playSettled = true)) }
            return
        }
        val list = (seasons as ApiResult.Success).value
        val nextEpisode = (next as? ApiResult.Success)?.value
        // Opens on the season of the episode Play starts; a show never started opens on its first real season, not Specials.
        val opening = list.firstOrNull { nextEpisode != null && it.seasonIndex == nextEpisode.seasonNumber }
            ?: list.firstOrNull { (it.seasonIndex ?: 0) > 0 }
            ?: list.firstOrNull()
        // The episodes are already marked as loading, so the page never shows "no episodes" in between.
        content.update {
            it.copy(seasons = SeasonsState(
                    seasons = list,
                    selectedId = opening?.id,
                    startEpisode = nextEpisode,
                    episodes = EpisodeList(loading = opening != null),
                    // With a next-up episode Play is known now; otherwise it waits for the first season's episodes.
                    playSettled = nextEpisode != null || opening == null,
                ))
        }
        if (opening != null) {
            loadEpisodes(opening.id, setStart = nextEpisode == null)
        }
    }

    private suspend fun loadEpisodes(seasonId: String, setStart: Boolean) {
        val result = library.episodes(itemId, seasonId)
        // Another season was chosen while this was out: its own answer is the one that counts.
        if (content.value.seasons.selectedId != seasonId) return
        content.update { state ->
            val seasons = state.seasons
            state.copy(
                seasons = when (result) {
                    is ApiResult.Success -> seasons.copy(
                        episodes = EpisodeList(items = result.value),
                        // A show with nothing next up (never started, or finished) plays from its first episode.
                        startEpisode = seasons.startEpisode ?: if (setStart) result.value.firstOrNull() else null,
                    )
                    is ApiResult.Failure -> seasons.copy(episodes = EpisodeList(message = LoadMessages.forError(result.error)))
                }.let { it.copy(playSettled = true) },
            )
        }
    }
}
