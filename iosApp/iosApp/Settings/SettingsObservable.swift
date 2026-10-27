import Foundation
import Shared

/// Lets SwiftUI watch the shared settings state. `SettingsModel` calls back on the main thread. One instance serves every page of the
/// Settings tree, so the pages share a single ViewModel.
final class SettingsObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: SettingsUiState?

    /// The last server the session was connected to. After Switch profile or Sign out the state no longer has one, and
    /// "Who's watching?" opens on this server.
    private(set) var lastServerId = ""

    private let model = SettingsModel()
    private var started = false

    /// Starts watching. Called when a page appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            if !state.serverId.isEmpty { self?.lastServerId = state.serverId }
            self?.state = state
            self?.followNotifications(state)
        }
    }

    /// Whether the Notifications toggle was on in the last state; nil before the first.
    private var notificationsOn: Bool?

    /// Asks for notification permission when the shared state says to, and schedules or drops the background check on a real change:
    /// after the permission flow when the user switched it on, and when it goes off (by the user, or because permission was refused).
    /// Opening Settings is not a change: the first states go from the default off to the saved value, so only on-to-off counts.
    private func followNotifications(_ state: SettingsUiState) {
        if state.requestNotificationPermission {
            // Acknowledged at once, so the next state doesn't ask a second time while the prompt is up.
            let model = self.model
            model.onNotificationPermissionRequested()
            NotificationPermission.request { granted in
                if granted { NewTitlesScheduler.refresh() } else { model.onNotificationPermissionDenied() }
            }
        }
        let toggle = state.pages[SettingsPageId.notifications]?.sections
            .flatMap { $0.items }
            .first { $0.key == SettingKey.notifications } as? ToggleItem
        let on = toggle?.checked ?? false
        if notificationsOn == true && !on { NewTitlesScheduler.refresh() }
        notificationsOn = on
    }

    /// The page with this id, only its visible rows; nil while the state is loading or the tree has no such page yet.
    func page(_ id: SettingsPageId) -> SettingsPage? {
        state?.pages[id]?.visibleOnly()
    }

    /// A section's rows without the links to pages the tree doesn't have yet (Notifications, Kids and PIN).
    func items(of section: SettingsSection) -> [SettingItem] {
        section.items.filter { item in
            guard let link = item as? LinkItem else { return true }
            return state?.pages[link.target] != nil
        }
    }

    /// The page the iPad opens Settings on; nil while loading or when nothing has anything to set.
    func firstSettablePage() -> SettingsPageId? {
        model.firstSettablePage()
    }

    /// The links the sidebar draws, in order.
    func rootLinks() -> [LinkItem] {
        guard let root = page(SettingsPageId.root) else { return [] }
        return root.sections.flatMap { items(of: $0) }.compactMap { $0 as? LinkItem }
    }

    /// The first link row that opens the first settable page.
    func defaultLinkKey() -> SettingKey? {
        guard let first = firstSettablePage() else { return nil }
        return rootLinks().first { $0.target == first }?.key
    }

    func toggle(_ key: SettingKey, _ value: Bool) {
        model.onToggle(key: key, value: value)
    }

    func choose(_ key: SettingKey, _ value: Int32) {
        model.onChoose(key: key, value: value)
    }

    func perform(_ key: SettingKey) {
        model.onAction(key: key)
    }

    /// The page the state asked to close was closed.
    func pageClosed() {
        model.onPageClosed()
    }

    /// What is typed in a page's search field; the shared state comes back with the page filtered.
    func search(_ id: SettingsPageId, _ query: String) {
        model.onSearch(page: id, query: query)
    }

    /// A page appeared; the Kids and PIN page reads the server's limits then.
    func pageOpened(_ id: SettingsPageId) {
        model.onPageOpened(page: id)
    }

    func submitPin(_ pin: String) { model.onSubmitPin(pin: pin) }

    func forgotPin() { model.onForgotPin() }

    func submitPassword(_ password: String) { model.onSubmitPassword(password: password) }

    func dismissPin() { model.onDismissPin() }

    func confirmSignOut() {
        model.onConfirmSignOut()
    }

    func dismissSignOut() {
        model.onDismissSignOut()
    }

    deinit {
        model.close()
    }
}
