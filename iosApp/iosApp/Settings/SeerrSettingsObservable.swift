import Foundation
import Shared

/// Lets SwiftUI watch Settings › Seerr and send it events. `SeerrSettingsModel` calls back on the main thread.
final class SeerrSettingsObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: SeerrSettingsUiState?

    private let model = SeerrSettingsModel()
    private var started = false

    /// Starts watching. Called when the screen appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func setAddress(_ text: String) { model.onAddressChange(text: text) }

    func connect() { model.onConnect() }

    func changeAddress() { model.onChangeAddress() }

    func retry() { model.onRetry() }

    func signInWithPassword() { model.onSignInWithPassword() }

    func submitPassword(_ password: String) { model.onSubmitPassword(password: password) }

    func dismissPassword() { model.onDismissPassword() }

    func disconnect() { model.onDisconnect() }

    func confirmDisconnect() { model.onConfirmDisconnect() }

    func dismissDisconnect() { model.onDismissDisconnect() }

    deinit {
        model.close()
    }
}
