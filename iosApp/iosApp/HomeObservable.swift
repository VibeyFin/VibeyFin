import Foundation
import Shared

/// Lets SwiftUI watch the shared Home state and send it events. `HomeModel` calls back on the main thread.
final class HomeObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: HomeUiState?

    private let model = HomeModel()
    private var started = false

    /// Starts watching. Called when the screen appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func switchProfile() {
        model.onSwitchProfile()
    }

    func switchServer() {
        model.onSwitchServer()
    }

    func signOut() {
        model.onSignOut()
    }

    deinit {
        model.close()
    }
}
