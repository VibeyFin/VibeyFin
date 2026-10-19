import Foundation
import Shared

/// Lets SwiftUI watch the shared list state of a library, folder, series or season and send it
/// events. `ChildrenModel` calls back on the main thread.
final class ChildrenObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: ChildrenUiState?

    private let model: ChildrenModel
    private var started = false

    init(parentId: String) {
        model = ChildrenModel(parentId: parentId)
    }

    /// Starts watching. Called when the screen appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func loadMore() {
        model.onLoadMore()
    }

    func retry() {
        model.onRetry()
    }

    deinit {
        model.close()
    }
}
