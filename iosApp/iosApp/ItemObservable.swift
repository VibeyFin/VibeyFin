import Foundation
import Shared

/// Lets SwiftUI watch the shared state of one item's page and send it events. `ItemModel` calls
/// back on the main thread.
final class ItemObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: ItemUiState?

    private let model: ItemModel
    private var started = false

    init(itemId: String) {
        model = ItemModel(itemId: itemId)
    }

    /// Starts watching. Called when the screen appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func retry() {
        model.onRetry()
    }

    deinit {
        model.close()
    }
}
