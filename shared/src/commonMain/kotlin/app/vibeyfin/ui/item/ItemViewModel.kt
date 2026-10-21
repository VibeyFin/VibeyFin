package app.vibeyfin.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.WatchChanges
import app.vibeyfin.domain.WatchRepository
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.home.HomeExit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ItemUiState(
    val item: MediaItem? = null,
    /** The short facts under the title: "S1 E3", year, age rating, runtime, rating. */
    val facts: List<String> = emptyList(),
    val loading: Boolean = true,
    /** Why the item didn't load, with what to try. */
    val message: String? = null,
    /** A change that couldn't be saved on the server and was taken back, with what to try. */
    val actionMessage: String? = null,
    /** Set when the session ended and the screen must be left. */
    val exit: HomeExit? = null,
) {
    /** The one badge for the picture ("4K", "Dolby Vision"), shown beside the facts; null when there is none. */
    val badge: String? get() = item?.formatBadge()

    /** The server says the user has watched it: the page shows a check icon beside the facts. */
    val watched: Boolean get() = item?.played == true

    /** The menu entry that flips the watched mark, worded for what it will do. */
    val markLabel: String get() = if (item?.played == true) Strings.markUnwatched else Strings.markWatched

    /** Whether the page offers Play. */
    val canPlay: Boolean get() = item?.canPlay == true

    /** "Resume · 38m left" when the server says the user stopped part way, else null. */
    private val resumeLabel: String?
        get() = item?.takeIf { it.canPlay && !it.played && it.resumePositionMs > 0 }
            ?.let { found -> found.runtimeMs?.let { Strings.resumeTimeLeft(it - found.resumePositionMs) } ?: Strings.resume }

    /** The primary button: "Resume · 38m left", or "Play". */
    val playLabel: String get() = resumeLabel ?: Strings.play

    /** Whether "Start over" is offered beside the primary button: only when there is something to resume. */
    val canStartOver: Boolean get() = resumeLabel != null
}

/** One item's metadata. */
class ItemViewModel(
    private val itemId: String,
    private val library: LibraryRepository,
    connection: ConnectionManager,
    private val watch: WatchRepository,
    changes: WatchChanges,
) : ViewModel() {
    private val content = MutableStateFlow(ItemUiState())

    val uiState: StateFlow<ItemUiState> = combine(content, connection.sessionEnded()) { content, exit ->
        content.copy(exit = exit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemUiState())

    init {
        load()
        // What was watched changed (playback ended): bring the Resume button up to date, quietly.
        viewModelScope.launch { changes.events.collect { refresh() } }
    }

    fun onRetry() = load()

    /** Reads the item again without the spinner; a failure leaves the page as it was. */
    private suspend fun refresh() {
        if (content.value.item == null) return
        val result = library.item(itemId) as? ApiResult.Success ?: return
        content.update { it.copy(item = result.value, facts = factsOf(result.value)) }
    }

    private fun factsOf(item: MediaItem) = item.facts()

    private var writing = false

    /**
     * Flips the watched mark. The page shows the change at once and the server is told after; if that
     * fails the page goes back and says so. A rejected token ends the session instead, so no message.
     */
    fun onTogglePlayed() {
        val before = content.value.item ?: return
        if (!before.canPlay || writing) return
        writing = true
        val target = !before.played
        val shown = before.copy(played = target, resumePositionMs = 0)
        content.update { it.copy(item = shown, facts = factsOf(shown), actionMessage = null) }
        viewModelScope.launch {
            val result = watch.setPlayed(itemId, target)
            writing = false
            if (result is ApiResult.Failure) {
                val message = when {
                    result.error == ApiError.Unauthorized -> null
                    target -> LoadMessages.MARK_WATCHED_FAILED
                    else -> LoadMessages.MARK_UNWATCHED_FAILED
                }
                content.update { it.copy(item = before, facts = factsOf(before), actionMessage = message) }
            }
        }
    }

    /** "Try again" on the failure message: the page was taken back, so flipping again repeats the change. */
    fun onRetryAction() = onTogglePlayed()

    fun onDismissMessage() = content.update { it.copy(actionMessage = null) }

    private fun load() {
        content.value = ItemUiState(loading = true)
        viewModelScope.launch {
            content.value = when (val result = library.item(itemId)) {
                is ApiResult.Success -> ItemUiState(item = result.value, facts = factsOf(result.value), loading = false)
                is ApiResult.Failure -> ItemUiState(loading = false, message = LoadMessages.forError(result.error))
            }
        }
    }
}
