package app.vibeyfin.ios

import app.vibeyfin.ui.theme.ThemeViewModel

/** The signed-in profile's theme id as SwiftUI needs it: called with the current id now and with every change. */
class ThemeModel {
    private val host = ViewModelHost(ThemeViewModel::class) { it.get<ThemeViewModel>() }

    fun observe(onChange: (String) -> Unit) = host.observe(host.viewModel.themeId, onChange)

    fun close() = host.close()
}
