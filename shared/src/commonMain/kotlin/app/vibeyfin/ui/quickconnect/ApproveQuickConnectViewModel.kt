package app.vibeyfin.ui.quickconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.authorizeQuickConnect
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.whoswatching.SignInMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val QUICK_CONNECT_CODE_LENGTH = 6

data class ApproveQuickConnectUiState(
    /** What is typed: digits only, at most [QUICK_CONNECT_CODE_LENGTH]. */
    val code: String = "",
    /** The signed-in profile, whom the other device signs in as. */
    val profileName: String = "",
    val approving: Boolean = false,
    /** Said under the field once a code was approved, until another is typed. */
    val done: String? = null,
    val error: String? = null,
) {
    val canApprove: Boolean get() = code.length == QUICK_CONNECT_CODE_LENGTH && !approving
}

/**
 * Settings › Quick Connect: the signed-in profile approves the code another device shows, which signs that device in as this profile.
 * It asks first whether the server has Quick Connect on, because Jellyfin answers an approval with 401 while it is off, and a 401 would
 * end this profile's session.
 */
class ApproveQuickConnectViewModel(private val connection: ConnectionManager) : ViewModel() {
    private data class Local(val code: String = "", val approving: Boolean = false, val done: String? = null, val error: String? = null)

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<ApproveQuickConnectUiState> = combine(local, connection.state) { local, connected ->
        ApproveQuickConnectUiState(
            code = local.code,
            profileName = (connected as? ConnectionState.Connected)?.account?.name.orEmpty(),
            approving = local.approving,
            done = local.done,
            error = local.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApproveQuickConnectUiState())

    /** Keeps the digits, so a pasted "123 456" works, and drops what was said about the last code. */
    fun onCodeChange(code: String) {
        val digits = code.filter { it.isDigit() }.take(QUICK_CONNECT_CODE_LENGTH)
        local.update { if (it.approving) it else it.copy(code = digits, done = null, error = null) }
    }

    fun onApprove() {
        val current = local.value
        if (current.approving || current.code.length != QUICK_CONNECT_CODE_LENGTH) return
        val connected = connection.state.value as? ConnectionState.Connected ?: return
        local.update { it.copy(approving = true, done = null, error = null) }
        viewModelScope.launch {
            val error = approve(connected, current.code)
            local.update {
                if (error == null) {
                    Local(done = Strings.quickConnectApprovedFor(connected.account.name))
                } else {
                    it.copy(approving = false, error = error)
                }
            }
        }
    }

    // Null when approved, else what to tell the user
    private suspend fun approve(connected: ConnectionState.Connected, code: String): String? {
        val api = connected.api
        when (val enabled = api.quickConnectEnabled()) {
            is ApiResult.Success -> if (!enabled.value) return SignInMessages.QUICK_CONNECT_APPROVE_OFF
            is ApiResult.Failure -> return failure(enabled.error)
        }
        return when (val approved = api.authorizeQuickConnect(code, connected.account.userId)) {
            is ApiResult.Success -> if (approved.value) null else SignInMessages.QUICK_CONNECT_APPROVE_FAILED
            is ApiResult.Failure -> failure(approved.error)
        }
    }

    private fun failure(error: ApiError): String = when (error) {
        ApiError.NotFound -> SignInMessages.QUICK_CONNECT_CODE_UNKNOWN
        ApiError.Network -> SignInMessages.UNREACHABLE
        else -> SignInMessages.QUICK_CONNECT_APPROVE_FAILED
    }
}
