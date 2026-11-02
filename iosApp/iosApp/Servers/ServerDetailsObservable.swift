import Foundation
import Shared

/// Lets SwiftUI watch a saved server's details and send it events. `ServerDetailsModel` calls back on the main thread.
final class ServerDetailsObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: ServerDetailsUiState?

    private let model: ServerDetailsModel
    private var started = false

    init(serverId: String) {
        model = ServerDetailsModel(serverId: serverId)
    }

    /// Starts watching. Called when the screen appears, not from `init`, because SwiftUI may build
    /// the object while it is updating a view and must not be sent state changes then.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func removeAddress(_ address: String) {
        model.removeAddress(address: address)
    }

    func removeServer() {
        model.removeServer()
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
