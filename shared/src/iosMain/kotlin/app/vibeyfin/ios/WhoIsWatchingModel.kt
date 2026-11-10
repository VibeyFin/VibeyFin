package app.vibeyfin.ios

import app.vibeyfin.domain.model.Profile
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.ui.common.PinSheetControls
import app.vibeyfin.ui.whoswatching.WhoIsWatchingUiState
import app.vibeyfin.ui.whoswatching.WhoIsWatchingViewModel
import org.koin.core.parameter.parametersOf

/**
 * The "Who's watching?" screen for the server [serverId], as SwiftUI needs it. [pinFor] comes from
 * `StartDestination.WhoIsWatching`: the profile whose PIN the screen asks for first.
 */
class WhoIsWatchingModel(serverId: String, pinFor: ProfileRef?) : PinSheetModel {
    constructor(serverId: String) : this(serverId, null)

    private val host = ViewModelHost(WhoIsWatchingViewModel::class) {
        it.get<WhoIsWatchingViewModel> { parametersOf(serverId, pinFor) }
    }

    override val pinSheet: PinSheetControls get() = host.viewModel

    fun observe(onChange: (WhoIsWatchingUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onProfileTapped(profile: Profile) = host.viewModel.onProfileTapped(profile)

    fun onOtherUserTapped() = host.viewModel.onOtherUserTapped()

    /** Starts Quick Connect, or starts it over after it stopped. */
    fun onQuickConnectTapped() = host.viewModel.onQuickConnectTapped()

    fun onSubmitOtherUser(username: String, password: String) = host.viewModel.onSubmitOtherUser(username, password)

    fun onDismissSheet() = host.viewModel.onDismissSheet()

    fun refresh() = host.viewModel.refresh()

    fun onAppResumed() = host.viewModel.onAppResumed()

    fun close() = host.close()
}
