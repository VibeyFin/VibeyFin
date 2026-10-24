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
        }
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

    func toggle(_ key: SettingKey, _ value: Bool) {
        model.onToggle(key: key, value: value)
    }

    func choose(_ key: SettingKey, _ value: Int32) {
        model.onChoose(key: key, value: value)
    }

    func perform(_ key: SettingKey) {
        model.onAction(key: key)
    }

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
