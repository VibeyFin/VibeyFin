import Foundation
import SwiftUI
import Shared

/// Lets SwiftUI watch the long-press menu of a poster and send it events. `TileMenuModel` calls back on the main thread.
final class TileMenuObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: TileMenuUiState?

    private let model = TileMenuModel()
    private var started = false

    /// Starts watching. Called when the tab shell appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func open(_ item: MediaItem) { model.onOpen(item: item) }

    func close() { model.onClose() }

    func toggleFavorite() { model.onToggleFavorite() }

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
