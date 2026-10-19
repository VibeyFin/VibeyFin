package com.liamgleeson.vibeyfin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liamgleeson.vibeyfin.data.api.ApiResult
import com.liamgleeson.vibeyfin.domain.ConnectionManager
import com.liamgleeson.vibeyfin.domain.ConnectionState
import com.liamgleeson.vibeyfin.domain.LibraryRepository
import com.liamgleeson.vibeyfin.domain.model.MediaItem
import com.liamgleeson.vibeyfin.ui.common.LoadMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    /** Set when the screen should be left: the user chose to, or the session ended. */
    val exit: HomeExit? = null,
)

private data class Libraries(
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
)

class HomeViewModel(
    private val connection: ConnectionManager,
    private val library: LibraryRepository,
) : ViewModel() {
    private val exit = MutableStateFlow<HomeExit?>(null)
    // Signed in, the libraries are always on their way, so the first thing to show is loading, not empty.
    private val libraries = MutableStateFlow(Libraries(loading = true))

    val uiState: StateFlow<HomeUiState> = combine(connection.state, exit, libraries) { state, exit, libraries ->
        when (state) {
            is ConnectionState.Connected -> HomeUiState(
                userName = state.account.name,
                serverName = state.server.name,
                serverId = state.server.id,
                libraries = libraries.items,
                loadingLibraries = libraries.loading,
                librariesMessage = libraries.message,
                exit = exit,
            )
            // A rejected token ended the session: back to "Who's watching?", where the tile is now locked.
            is ConnectionState.Expired -> HomeUiState(
                serverId = state.serverId,
                exit = HomeExit.WhoIsWatching(state.serverId),
            )
            ConnectionState.Disconnected -> HomeUiState(exit = exit)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Load for the session we are in, and again if it becomes another profile or address.
        viewModelScope.launch {
            connection.state
                .map { (it as? ConnectionState.Connected)?.let { c -> Triple(c.server.id, c.account.userId, c.api.baseUrl) } }
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest { loadLibraries() }
        }
    }

    fun onRetryLibraries() {
        viewModelScope.launch { loadLibraries() }
    }

    private suspend fun loadLibraries() {
        libraries.value = Libraries(loading = true)
        libraries.value = when (val result = library.libraries()) {
            is ApiResult.Success -> Libraries(items = result.value)
            is ApiResult.Failure -> Libraries(message = LoadMessages.forError(result.error))
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
