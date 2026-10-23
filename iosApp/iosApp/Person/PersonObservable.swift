import Foundation
import Shared

/// Lets SwiftUI watch the shared state of a cast member's page and send it events. `PersonModel` calls back on the main thread.
final class PersonObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: PersonUiState?

    private let model: PersonModel
    private var started = false

    init(personId: String) {
        model = PersonModel(personId: personId)
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
