package app.vibeyfin.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.domain.AccountRepository
import app.vibeyfin.domain.ServerRemoval
import app.vibeyfin.domain.ServerRepository
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerAddressRow(val address: String, val lastWorked: Boolean)

data class ServerDetailsUiState(
    val loading: Boolean = true,
    val name: String = "",
    val version: String? = null,
    val addresses: List<ServerAddressRow> = emptyList(),
    val profileCount: Int = 0,
    val confirmRemove: Boolean = false,
    val removed: Boolean = false,
) {
    /** A server keeps at least one address; remove the server instead. */
    val canRemoveAddress: Boolean get() = addresses.size > 1
    val removeHelp: String get() = Strings.removeServerHelp(profileCount)
    val removeTitle: String get() = Strings.removeServerTitle(name)
    val removeMessage: String get() = Strings.removeServerMessage(profileCount)
}

class ServerDetailsViewModel(
    private val serverId: String,
    private val servers: ServerRepository,
    accounts: AccountRepository,
    private val removal: ServerRemoval,
) : ViewModel() {
    private data class Local(val confirmRemove: Boolean = false, val removing: Boolean = false)

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<ServerDetailsUiState> =
        combine(servers.servers, accounts.observeProfileCounts(), local) { saved, counts, local ->
            val server = saved.find { it.id == serverId }
                ?: return@combine ServerDetailsUiState(loading = false, removed = true)
            ServerDetailsUiState(
                loading = false,
                name = server.name,
                version = server.version,
                addresses = server.addresses.mapIndexed { index, address -> ServerAddressRow(address, lastWorked = index == 0) },
                profileCount = counts[serverId] ?: 0,
                confirmRemove = local.confirmRemove,
                removed = local.removing,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerDetailsUiState())

    fun onRemoveAddress(address: String) {
        viewModelScope.launch { servers.removeAddress(serverId, address) }
    }

    fun onRemoveServer() = local.update { it.copy(confirmRemove = true) }

    fun onDismissRemove() = local.update { it.copy(confirmRemove = false) }

    fun onConfirmRemove() {
        local.update { it.copy(confirmRemove = false, removing = true) }
        viewModelScope.launch { removal.remove(serverId) }
    }
}
