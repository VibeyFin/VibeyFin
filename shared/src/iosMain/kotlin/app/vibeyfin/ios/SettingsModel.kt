package app.vibeyfin.ios

import app.vibeyfin.ui.common.PinSheetControls
import app.vibeyfin.ui.settings.SettingKey
import app.vibeyfin.ui.settings.SettingsPageId
import app.vibeyfin.ui.settings.SettingsUiState
import app.vibeyfin.ui.settings.SettingsViewModel
import app.vibeyfin.ui.settings.firstSettablePage

/** The settings screen's [SettingsViewModel] as SwiftUI needs it. */
class SettingsModel : PinSheetModel {
    private val host = ViewModelHost(SettingsViewModel::class) { it.get<SettingsViewModel>() }

    override val pinSheet: PinSheetControls get() = host.viewModel

    fun observe(onChange: (SettingsUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onToggle(key: SettingKey, value: Boolean) = host.viewModel.onToggle(key, value)

    /** A developer flag's switch was flipped; [id] is the row's `devFlagId`. */
    fun onDevFlag(id: String, value: Boolean) = host.viewModel.onDevFlag(id, value)

    fun onChoose(key: SettingKey, value: Int) = host.viewModel.onChoose(key, value)

    fun onChooseLanguage(key: SettingKey, code: String) = host.viewModel.onChooseLanguage(key, code)

    fun onAction(key: SettingKey) = host.viewModel.onAction(key)

    /** The page the iPad's split view opens on; null when nothing has anything to set. */
    fun firstSettablePage(): SettingsPageId? = firstSettablePage(host.viewModel.uiState.value.pages)

    /** A page was opened; the Kids and PIN page reads the server's limits then. */
    fun onPageOpened(page: SettingsPageId) = host.viewModel.onPageOpened(page)

    /** What is typed in a page's search field. */
    fun onSearch(page: SettingsPageId, query: String) = host.viewModel.onSearch(page, query)

    /** The page the state asked to close was closed. */
    fun onPageClosed() = host.viewModel.onPageClosed()

    fun onConfirmSignOut() = host.viewModel.onConfirmSignOut()

    fun onDismissSignOut() = host.viewModel.onDismissSignOut()

    fun onConfirmPurgeDownloads() = host.viewModel.onConfirmPurgeDownloads()

    fun onDismissPurgeDownloads() = host.viewModel.onDismissPurgeDownloads()

    /** The one-shot permission request in the state was shown: acknowledge it. */
    fun onNotificationPermissionRequested() = host.viewModel.onNotificationPermissionRequested()

    /** The OS refused notification permission: the setting goes back off. */
    fun onNotificationPermissionDenied() = host.viewModel.onNotificationPermissionDenied()

    fun close() = host.close()
}
