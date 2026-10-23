import Foundation
import Shared

/// Lets SwiftUI watch the shared Add server screen state and send it events. A non-nil
/// `initialAddress` is checked straight away. `AddServerModel` calls back on the main thread.
final class AddServerObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: AddServerUiState?

    private let model: AddServerModel
    private var started = false

    init(initialAddress: String?) {
        model = AddServerModel(initialAddress: initialAddress)
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

    func setAddress(_ address: String) {
        model.onAddressChange(address: address)
    }

    func connect() {
        model.onConnect()
    }

    func confirm() {
        model.onConfirm()
    }

    deinit {
        model.close()
    }
}
