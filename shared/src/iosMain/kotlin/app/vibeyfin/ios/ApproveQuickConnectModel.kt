package app.vibeyfin.ios

import app.vibeyfin.ui.quickconnect.ApproveQuickConnectUiState
import app.vibeyfin.ui.quickconnect.ApproveQuickConnectViewModel

/** Settings › Quick Connect's [ApproveQuickConnectViewModel] as SwiftUI needs it. */
class ApproveQuickConnectModel {
    private val host = ViewModelHost(ApproveQuickConnectViewModel::class) { it.get<ApproveQuickConnectViewModel>() }

    fun observe(onChange: (ApproveQuickConnectUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onCodeChange(code: String) = host.viewModel.onCodeChange(code)

    fun onApprove() = host.viewModel.onApprove()

    fun close() = host.close()
}
