package com.liamgleeson.vibeyfin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liamgleeson.vibeyfin.domain.ConnectionManager
import com.liamgleeson.vibeyfin.domain.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    /** Set when the screen should be left: the user chose to, or the session ended. */
    val exit: HomeExit? = null,
)

class HomeViewModel(private val connection: ConnectionManager) : ViewModel() {
    private val exit = MutableStateFlow<HomeExit?>(null)

    val uiState: StateFlow<HomeUiState> = combine(connection.state, exit) { state, exit ->
        when (state) {
            is ConnectionState.Connected -> HomeUiState(
                userName = state.account.name,
                serverName = state.server.name,
                serverId = state.server.id,
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
