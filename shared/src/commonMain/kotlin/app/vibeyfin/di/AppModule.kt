package app.vibeyfin.di

import app.vibeyfin.data.platform.DeviceInfo
import app.vibeyfin.domain.ServerRemoval
import app.vibeyfin.i18n.AppLocale
import app.vibeyfin.ui.addserver.AddServerViewModel
import app.vibeyfin.ui.banner.AppBannerViewModel
import app.vibeyfin.ui.children.ChildrenViewModel
import app.vibeyfin.ui.downloads.DownloadsViewModel
import app.vibeyfin.ui.filtered.FilteredListViewModel
import app.vibeyfin.ui.home.HomeViewModel
import app.vibeyfin.ui.home.OfflineHomeViewModel
import app.vibeyfin.ui.item.ItemViewModel
import app.vibeyfin.ui.notifications.NotificationOpenViewModel
import app.vibeyfin.ui.person.PersonViewModel
import app.vibeyfin.ui.playback.PlaybackViewModel
import app.vibeyfin.ui.profile.ProfileMenuViewModel
import app.vibeyfin.ui.quickconnect.ApproveQuickConnectViewModel
import app.vibeyfin.ui.search.SearchViewModel
import app.vibeyfin.ui.seerr.MyRequestsViewModel
import app.vibeyfin.ui.seerr.SeerrHomeViewModel
import app.vibeyfin.ui.seerr.SeerrListViewModel
import app.vibeyfin.ui.seerr.SeerrSettingsViewModel
import app.vibeyfin.ui.seerr.SeerrTitleViewModel
import app.vibeyfin.ui.servers.ServerDetailsViewModel
import app.vibeyfin.ui.servers.ServersViewModel
import app.vibeyfin.ui.settings.SettingsViewModel
import app.vibeyfin.ui.startup.StartupViewModel
import app.vibeyfin.ui.subtitles.SubtitleSearchViewModel
import app.vibeyfin.ui.theme.ThemeViewModel
import app.vibeyfin.ui.tile.TileMenuViewModel
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
    includes(dataModule(), apiModule(), seerrModule())
    single { ServerRemoval(get(), get(), get(), get(), get()) }
    viewModelOf(::ServersViewModel)
    viewModel { params -> ServerDetailsViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> AddServerViewModel(params.getOrNull(), get(), get()) }
    viewModel { params ->
        WhoIsWatchingViewModel(params.get(), get(), get(), get(), get(), get(), get(), params.getOrNull(), get())
    }
    viewModelOf(::HomeViewModel)
    viewModelOf(::OfflineHomeViewModel)
    viewModelOf(::ProfileMenuViewModel)
    viewModel { params -> ChildrenViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> PersonViewModel(params.get(), get(), get()) }
    viewModel { SearchViewModel(get(), get(), get(), get(), get(), seerr = get(), seerrRepository = get()) }
    viewModelOf(::TileMenuViewModel)
    viewModelOf(::SubtitleSearchViewModel)
    viewModel { params -> FilteredListViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> ItemViewModel(params.get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { params -> PlaybackViewModel(params.get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), startOver = params.getOrNull() ?: false) }
    viewModelOf(::StartupViewModel)
    viewModelOf(::ThemeViewModel)
    viewModelOf(::NotificationOpenViewModel)
    viewModel {
        val device = get<DeviceInfo>()
        SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(ApplicationScope), device.appVersion, device.subtitleStyleApplies, downloadAlerts = device.downloadAlerts, seerr = get(), appLanguages = if (device.languagePickedInSystem) emptyList() else get<AppLocale>().languages)
    }
    viewModelOf(::AppBannerViewModel)
    viewModelOf(::DownloadsViewModel)
    viewModel { SeerrSettingsViewModel(get(), get(), get(), get(ApplicationScope)) }
    viewModel { ApproveQuickConnectViewModel(get()) }
    viewModel { params -> SeerrTitleViewModel(params.get(), params.get(), get(), get(), get()) }
    viewModel { SeerrHomeViewModel(get(), get(), get()) }
    viewModel { params -> SeerrListViewModel(params.get(), get()) }
    viewModel { MyRequestsViewModel(get(), get()) }
}
