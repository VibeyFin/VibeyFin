package app.vibeyfin.di

import app.vibeyfin.data.platform.DeviceInfo
import app.vibeyfin.ui.addserver.AddServerViewModel
import app.vibeyfin.ui.children.ChildrenViewModel
import app.vibeyfin.ui.person.PersonViewModel
import app.vibeyfin.ui.search.SearchViewModel
import app.vibeyfin.ui.tile.TileMenuViewModel
import app.vibeyfin.ui.filtered.FilteredListViewModel
import app.vibeyfin.ui.home.HomeViewModel
import app.vibeyfin.ui.item.ItemViewModel
import app.vibeyfin.ui.playback.PlaybackViewModel
import app.vibeyfin.ui.servers.ServersViewModel
import app.vibeyfin.ui.settings.SettingsViewModel
import app.vibeyfin.ui.startup.StartupViewModel
import app.vibeyfin.ui.whoswatching.WhoIsWatchingViewModel
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
    viewModel { params -> PersonViewModel(params.get(), get(), get()) }
    viewModelOf(::SearchViewModel)
    viewModelOf(::TileMenuViewModel)
    viewModel { params -> FilteredListViewModel(params.get(), get(), get()) }
    viewModel { params -> ItemViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { params -> PlaybackViewModel(params.get(), get(), get(), get(), get(), get(), get(), get(), get(), startOver = params.getOrNull() ?: false) }
    viewModelOf(::StartupViewModel)
    viewModel { SettingsViewModel(get(), get(), get<DeviceInfo>().appVersion) }
}
