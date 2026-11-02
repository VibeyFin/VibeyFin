import Foundation
import Shared

/// Lets SwiftUI watch the shared Servers screen state. `ServersModel` calls back on the main thread.
final class ServersObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: ServersUiState?

    private let model = ServersModel()
    private var started = false

    /// Starts watching. Called when the screen appears, not from `init`, because SwiftUI may build
    /// the object while it is updating a view and must not be sent state changes then.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func searchAgain() {
        model.searchAgain()
    }

    func removeServer(_ serverId: String) {
        model.removeServer(serverId: serverId)
    }

    func confirmRemove() {
        model.confirmRemove()
    }

    func dismissRemove() {
        model.dismissRemove()
    }

    deinit {
        model.close()
    }
}
