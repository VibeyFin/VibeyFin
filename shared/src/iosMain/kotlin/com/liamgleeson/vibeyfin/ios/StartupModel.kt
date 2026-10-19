package com.liamgleeson.vibeyfin.ios

import com.liamgleeson.vibeyfin.domain.StartDestination
import com.liamgleeson.vibeyfin.ui.startup.StartupViewModel

/** Works out where the app opens. [observe] calls back once, on the main thread, when it knows. */
class StartupModel {
    private val host = ViewModelHost(StartupViewModel::class) { it.get<StartupViewModel>() }

    fun observe(onDecided: (StartDestination) -> Unit) =
        host.observe(host.viewModel.destination) { destination -> destination?.let(onDecided) }

    fun close() = host.close()
}
