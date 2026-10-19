package com.liamgleeson.vibeyfin.ios

import com.liamgleeson.vibeyfin.domain.model.Profile
import com.liamgleeson.vibeyfin.ui.whoswatching.WhoIsWatchingUiState
import com.liamgleeson.vibeyfin.ui.whoswatching.WhoIsWatchingViewModel
import org.koin.core.parameter.parametersOf

/** The "Who's watching?" screen for the server [serverId], as SwiftUI needs it. */
class WhoIsWatchingModel(serverId: String) {
    private val host = ViewModelHost(WhoIsWatchingViewModel::class) {
        it.get<WhoIsWatchingViewModel> { parametersOf(serverId) }
    }

    fun observe(onChange: (WhoIsWatchingUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onProfileTapped(profile: Profile) = host.viewModel.onProfileTapped(profile)

    fun onOtherUserTapped() = host.viewModel.onOtherUserTapped()

    fun onSubmitPassword(password: String) = host.viewModel.onSubmitPassword(password)

    fun onSubmitOtherUser(username: String, password: String) = host.viewModel.onSubmitOtherUser(username, password)

    fun onDismissSheet() = host.viewModel.onDismissSheet()

    fun refresh() = host.viewModel.refresh()

    fun close() = host.close()
}
