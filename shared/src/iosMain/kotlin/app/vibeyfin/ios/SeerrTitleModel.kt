package app.vibeyfin.ios

import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.ui.seerr.SeerrTitleUiState
import app.vibeyfin.ui.seerr.SeerrTitleViewModel
import org.koin.core.parameter.parametersOf

/** A title from Seerr ([type], TMDB id [tmdbId]): its page, poster menu and season picker, as SwiftUI needs them. */
class SeerrTitleModel(type: SeerrMediaType, tmdbId: Int) {
    private val host = ViewModelHost(SeerrTitleViewModel::class) {
        it.get<SeerrTitleViewModel> { parametersOf(type, tmdbId) }
    }

    fun observe(onChange: (SeerrTitleUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onRetry() = host.viewModel.onRetry()

    fun onSelectSeason(number: Int) = host.viewModel.onSelectSeason(number)

    fun onRetryEpisodes() = host.viewModel.onRetryEpisodes()

    fun onRequest() = host.viewModel.onRequest()

    fun onRequest4k() = host.viewModel.onRequest4k()

    fun onPickerAllMissing(on: Boolean) = host.viewModel.onPickerAllMissing(on)

    fun onPickerSeason(number: Int) = host.viewModel.onPickerSeason(number)

    fun onPickerQuality(is4k: Boolean) = host.viewModel.onPickerQuality(is4k)

    fun onPickerProfile(id: Int) = host.viewModel.onPickerProfile(id)

    fun onPickerConfirm() = host.viewModel.onPickerConfirm()

    fun onPickerDismiss() = host.viewModel.onPickerDismiss()

    fun onCancelRequest() = host.viewModel.onCancelRequest()

    fun onRetryRequest() = host.viewModel.onRetryRequest()

    fun onNoticeShown() = host.viewModel.onNoticeShown()

    fun onItemOpened() = host.viewModel.onItemOpened()

    fun close() = host.close()
}
