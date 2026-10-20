package com.liamgleeson.vibeyfin.di

import com.liamgleeson.vibeyfin.ui.addserver.AddServerViewModel
import com.liamgleeson.vibeyfin.ui.children.ChildrenViewModel
import com.liamgleeson.vibeyfin.ui.home.HomeViewModel
import com.liamgleeson.vibeyfin.ui.item.ItemViewModel
import com.liamgleeson.vibeyfin.ui.playback.PlaybackViewModel
import com.liamgleeson.vibeyfin.ui.servers.ServersViewModel
import com.liamgleeson.vibeyfin.ui.settings.SettingsViewModel
import com.liamgleeson.vibeyfin.ui.startup.StartupViewModel
import com.liamgleeson.vibeyfin.ui.whoswatching.WhoIsWatchingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Common bindings. [initKoin] adds [platformModule] next to it.
 *
 * A function, not a `val`: Koin keeps singletons inside the module object, so a shared module
 * would hand the same instances to every Koin application that loads it, tests included.
 */
fun appModule() = module {
    includes(dataModule(), apiModule())
    viewModelOf(::ServersViewModel)
    viewModel { params -> AddServerViewModel(params.getOrNull(), get(), get()) }
    viewModel { params -> WhoIsWatchingViewModel(params.get(), get(), get(), get(), get()) }
    viewModelOf(::HomeViewModel)
    viewModel { params -> ChildrenViewModel(params.get(), get(), get()) }
    viewModel { params -> ItemViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { params -> PlaybackViewModel(params.get(), get(), get(), get(), get(), get(), startOver = params.getOrNull() ?: false) }
    viewModelOf(::StartupViewModel)
    viewModelOf(::SettingsViewModel)
}
