package app.vibeyfin.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.domain.AccountRepository
import app.vibeyfin.domain.DiscoveredServer
import app.vibeyfin.domain.LanDiscovery
import app.vibeyfin.domain.ServerRemoval
import app.vibeyfin.domain.ServerRepository
import app.vibeyfin.domain.model.Server
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerRow(val server: Server, val profileCount: Int) {
    val meta: String get() = Strings.serverMeta(server.version, server.addresses.size, profileCount)
}

data class ServersUiState(
    /** True until the saved servers have been read, so the empty state doesn't flash first. */
    val loading: Boolean = true,
    val servers: List<ServerRow> = emptyList(),
    /** Servers that answered the search and aren't saved yet. */
    val discovered: List<DiscoveredServer> = emptyList(),
    val searching: Boolean = false,
    /** False where the platform can't search, so the screen shows no search at all. */
    val canSearch: Boolean = false,
    val confirmRemove: ServerRow? = null,
)

class ServersViewModel(
    servers: ServerRepository,
    accounts: AccountRepository,
    private val discovery: LanDiscovery,
    private val removal: ServerRemoval,
) : ViewModel() {
    private val found = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    private val searching = MutableStateFlow(false)
    private val confirmRemoveId = MutableStateFlow<String?>(null)
    private var searchJob: Job? = null

    val uiState: StateFlow<ServersUiState> =
        combine(servers.servers, accounts.observeProfileCounts(), found, searching, confirmRemoveId) { saved, counts, found, searching, removeId ->
            val savedIds = saved.mapTo(mutableSetOf()) { it.id }
            val rows = saved.map { ServerRow(it, counts[it.id] ?: 0) }
            ServersUiState(
                loading = false,
                servers = rows,
                discovered = found.filter { it.id !in savedIds },
                searching = searching,
                canSearch = discovery.isSupported,
                confirmRemove = rows.find { it.server.id == removeId },
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ServersUiState(canSearch = discovery.isSupported),
        )

    init {
        search()
    }

    fun onSearchAgain() = search()

    fun onRemoveServer(serverId: String) {
        confirmRemoveId.value = serverId
    }

    fun onDismissRemove() {
        confirmRemoveId.value = null
    }

    fun onConfirmRemove() {
        val serverId = confirmRemoveId.value ?: return
        confirmRemoveId.value = null
        viewModelScope.launch { removal.remove(serverId) }
    }

    private fun search() {
        if (!discovery.isSupported) return
        searchJob?.cancel()
        found.value = emptyList()
        searching.value = true
        val job = viewModelScope.launch {
            discovery.discover()
                .catch { /* Searching is best effort; adding by address still works. */ }
                .collect { server ->
                    found.update { list -> if (list.any { it.id == server.id }) list else list + server }
                }
        }
        searchJob = job
        // A search that was replaced must not switch off the indicator of the one that replaced it.
        job.invokeOnCompletion { if (searchJob === job) searching.value = false }
    }
}
