package app.vibeyfin.ios

import app.vibeyfin.ui.theme.ThemeViewModel

/** The signed-in profile's theme id for SwiftUI. */
class ThemeModel {
    private val host = ViewModelHost(ThemeViewModel::class) { it.get<ThemeViewModel>() }

    fun observe(onChange: (String) -> Unit): Unit = host.observe(host.viewModel.themeId, onChange)

    fun close(): Unit = host.close()
}
