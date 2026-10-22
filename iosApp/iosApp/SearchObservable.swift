import Foundation
import Shared

/// Lets SwiftUI watch the shared state of the Search tab and send it events. `SearchModel` calls back on the main thread.
final class SearchObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: SearchUiState?

    private let model = SearchModel()
    private var started = false

    /// Starts watching. Called when the screen appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func setQuery(_ text: String) { model.onQueryChange(text: text) }

    func setFilter(_ filter: SearchFilter) { model.onFilter(filter: filter) }

    func retry() { model.onRetry() }

    deinit {
        model.close()
    }
}
