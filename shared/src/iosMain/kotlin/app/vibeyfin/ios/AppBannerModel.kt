package app.vibeyfin.ios

import app.vibeyfin.ui.banner.AppBannerViewModel
import app.vibeyfin.ui.banner.Banner

/** The offline banner's [AppBannerViewModel] as SwiftUI needs it: called with null when there is no banner. */
class AppBannerModel {
    private val host = ViewModelHost(AppBannerViewModel::class) { it.get<AppBannerViewModel>() }

    fun observe(onChange: (Banner?) -> Unit) = host.observe(host.viewModel.banner, onChange)

    fun onAction() = host.viewModel.onAction()

    fun close() = host.close()
}
