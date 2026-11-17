package app.vibeyfin.ui.addserver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.domain.ConnectionIssue
import app.vibeyfin.domain.ResolveResult
import app.vibeyfin.domain.ResolvedServer
import app.vibeyfin.domain.ServerRepository
import app.vibeyfin.domain.ServerResolver
import app.vibeyfin.domain.model.Server
import app.vibeyfin.i18n.localized
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AddServerStep {
    data object Idle : AddServerStep

    data object Checking : AddServerStep

    /** [existing] is the saved entry for this server when it was added before, maybe at another address. */
    data class Found(val server: ResolvedServer, val existing: Server?) : AddServerStep {
        val alreadySaved get() = existing != null
        val addressIsNew get() = existing == null || server.address !in existing.addresses
    }

    data class Failed(val issue: ConnectionIssue, val message: String = issue.message()) : AddServerStep
}

data class AddServerUiState(
    val address: String = "",
    val step: AddServerStep = AddServerStep.Idle,
    val saving: Boolean = false,
    val savedServerId: String? = null,
) {
    val canConnect get() = address.isNotBlank() && step !is AddServerStep.Checking && !saving
}

/** [initialAddress] is checked right away, as when a server found by the search is tapped. */
class AddServerViewModel(
    initialAddress: String?,
    private val resolver: ServerResolver,
    private val servers: ServerRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddServerUiState(address = initialAddress.orEmpty()))

    val uiState: StateFlow<AddServerUiState> = _uiState.localized { state -> state.rendered() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.rendered())

    private var connectJob: Job? = null

    init {
        if (initialAddress != null) onConnect()
    }

    /** Editing the address throws away any result, since it was for the old text. */
    fun onAddressChange(address: String) {
        connectJob?.cancel()
        _uiState.update { it.copy(address = address, step = AddServerStep.Idle) }
    }

    fun onConnect() {
        val state = _uiState.value
        if (!state.canConnect) return
        connectJob?.cancel()
        _uiState.update { it.copy(step = AddServerStep.Checking) }
        connectJob = viewModelScope.launch {
            val step = when (val result = resolver.resolve(state.address)) {
                is ResolveResult.Found -> AddServerStep.Found(result.server, servers.get(result.server.id))
                is ResolveResult.Failed -> AddServerStep.Failed(result.issue)
            }
            _uiState.update { it.copy(step = step) }
        }
    }

    private fun AddServerUiState.rendered(): AddServerUiState = copy(step = step.rendered())

    private fun AddServerStep.rendered(): AddServerStep = if (this is AddServerStep.Failed) AddServerStep.Failed(issue) else this

    fun onConfirm() {
        val state = _uiState.value
        val found = state.step as? AddServerStep.Found ?: return
        if (state.saving) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val server = found.server
            servers.save(server.id, server.name, server.version, server.address, server.otherAddresses)
            _uiState.update { it.copy(saving = false, savedServerId = server.id) }
        }
    }
}
