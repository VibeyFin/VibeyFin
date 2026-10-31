import Combine
import Foundation
import SwiftUI
import Shared

/// Lets SwiftUI watch the long-press menu of a poster and send it events. `TileMenuModel` calls back on the main thread.
final class TileMenuObservable: ObservableObject {
    struct Overlay: Equatable {
        var prompt: DownloadPrompt?
        var removal: RemovePrompt?
        var message: String?

        static func == (lhs: Overlay, rhs: Overlay) -> Bool {
            lhs.message == rhs.message && same(lhs.prompt, rhs.prompt) && same(lhs.removal, rhs.removal)
        }

        private static func same(_ lhs: AnyObject?, _ rhs: AnyObject?) -> Bool {
            lhs === rhs || (lhs?.isEqual(rhs) ?? false)
        }
    }

    /// Nil until the shared ViewModel reports its first state.
    /// Not published: a redraw while a finger is down would cancel the press.
    private(set) var state: TileMenuUiState?
    /// As last heard; a poster's rows are built before its own state arrives.
    private(set) var canDownload = true
    let stateChanges = PassthroughSubject<TileMenuUiState, Never>()
    /// What shows over the tabs: the download questions and a refused My List change.
    @Published private(set) var overlay = Overlay()

    /// Set by the shell, which owns the navigation and the player.
    var handlers = TileMenuHandlers()

    private let model = TileMenuModel()
    private var started = false

    /// Starts watching. Called when the tab shell appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            guard let self else { return }
            self.state = state
            canDownload = state.canDownload
            stateChanges.send(state)
            let next = Overlay(prompt: state.downloadPrompt, removal: state.removePrompt, message: state.actionMessage)
            if next != overlay { overlay = next }
        }
    }

    func open(_ item: MediaItem) { model.onOpen(item: item) }

    func close() { model.onClose() }

    func dismissMessage() { model.onDismissMessage() }

    func toggleFavorite() { model.onToggleFavorite() }

    func toggleFavorite(_ item: MediaItem) {
        if state?.item?.id != item.id { open(item) }
        toggleFavorite()
    }

    func download(_ item: MediaItem) {
        if state?.item?.id != item.id { open(item) }
        download()
    }

    func download() { model.onDownload() }

    func act(_ action: DownloadAction) { model.onDownloadAction(action: action) }

    func toggleUnwatchedOnly() { model.onToggleUnwatchedOnly() }

    func confirmDownload() { model.onConfirmDownload() }

    func dismissDownloadPrompt() { model.onDismissDownloadPrompt() }

    func confirmRemove() { model.onConfirmRemove() }

    func dismissRemoval() { model.onDismissRemoval() }

    deinit {
        model.close()
    }
}

private struct TileMenuKey: EnvironmentKey {
    static let defaultValue: TileMenuObservable? = nil
}

extension EnvironmentValues {
    /// The menu the tab shell owns, so a poster anywhere can open it with a long press.
    var tileMenu: TileMenuObservable? {
        get { self[TileMenuKey.self] }
        set { self[TileMenuKey.self] = newValue }
    }
}
