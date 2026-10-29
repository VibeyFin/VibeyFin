package app.vibeyfin.ios

import app.vibeyfin.ui.banner.AppBannerViewModel
import app.vibeyfin.ui.banner.Banner

/** The connection banner's [AppBannerViewModel] as SwiftUI needs it: called with null when there is no banner, or no toast. */
class AppBannerModel {
    private val host = ViewModelHost(AppBannerViewModel::class) { it.get<AppBannerViewModel>() }

    fun observe(onChange: (Banner?) -> Unit) = host.observe(host.viewModel.banner, onChange)

    /** The toast as the app goes offline: its words once, then null after [onToastShown]. */
    fun observeToast(onChange: (String?) -> Unit) = host.observe(host.viewModel.toast, onChange)

    fun onToastShown() = host.viewModel.onToastShown()

    fun onAction() = host.viewModel.onAction()

    fun close() = host.close()
}
