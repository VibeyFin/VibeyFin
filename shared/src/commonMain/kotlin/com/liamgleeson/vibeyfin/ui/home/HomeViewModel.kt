package com.liamgleeson.vibeyfin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liamgleeson.vibeyfin.data.api.ApiError
import com.liamgleeson.vibeyfin.data.api.ApiResult
import com.liamgleeson.vibeyfin.domain.ConnectionManager
import com.liamgleeson.vibeyfin.domain.ConnectionState
import com.liamgleeson.vibeyfin.domain.LibraryRepository
import com.liamgleeson.vibeyfin.domain.WatchChanges
import com.liamgleeson.vibeyfin.domain.WatchRepository
import com.liamgleeson.vibeyfin.domain.model.MediaItem
import com.liamgleeson.vibeyfin.ui.common.LoadMessages
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

    val uiState: StateFlow<HomeUiState> = combine(screen, actionMessage) { screen, message ->
        screen.copy(actionMessage = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Load for the session we are in, and again if it becomes another profile or address.
        viewModelScope.launch {
            connection.state
                .map { (it as? ConnectionState.Connected)?.let { c -> Triple(c.server.id, c.account.userId, c.api.baseUrl) } }
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest {
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
    private suspend fun loadContinue(first: Boolean) {
        if (first) continueRow.value = ContinueRow(loading = true)
        val result = watch.continueWatching()
        continueLoadedAt = clock.now()
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
