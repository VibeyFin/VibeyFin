package com.liamgleeson.vibeyfin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liamgleeson.vibeyfin.domain.ConnectionManager
import com.liamgleeson.vibeyfin.domain.ConnectionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val userName: String? = null,
    val serverName: String? = null,
    /** The server's id, for routing back to "Who's watching?" on that server. */
    val serverId: String? = null,
    /** The session ended (token rejected); the screen sends the user to "Who's watching?". */
    val expired: Boolean = false,
)

class HomeViewModel(connection: ConnectionManager) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = connection.state
        .map { state ->
            when (state) {
                is ConnectionState.Connected -> HomeUiState(
                    userName = state.account.name,
                    serverName = state.server.name,
                    serverId = state.server.id,
                )
                is ConnectionState.Expired -> HomeUiState(serverId = state.serverId, expired = true)
                ConnectionState.Disconnected -> HomeUiState()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
