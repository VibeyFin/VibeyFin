import Foundation
import Shared

/// Lets SwiftUI watch the shared "Who's watching?" state and send it events. `WhoIsWatchingModel`
/// calls back on the main thread.
final class WhoIsWatchingObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: WhoIsWatchingUiState?

    private let model: WhoIsWatchingModel
    private var started = false

    /// `pinFor` and `openAfterPin` come from the launch destination: the screen opens on that profile's PIN.
    init(serverId: String, pinFor: ProfileRef? = nil, openAfterPin: Bool = false) {
        model = WhoIsWatchingModel(serverId: serverId, pinFor: pinFor, openAfterPin: openAfterPin)
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

    func profileTapped(_ profile: Profile) {
        model.onProfileTapped(profile: profile)
    }

    func otherUserTapped() {
        model.onOtherUserTapped()
    }

    func quickConnectTapped() {
        model.onQuickConnectTapped()
    }

    func submitPassword(_ password: String) {
        model.onSubmitPassword(password: password)
    }

    func submitPin(_ pin: String) { model.onSubmitPin(pin: pin) }

    func forgotPin() { model.onForgotPin() }

    func dismissPin() { model.onDismissPin() }

    func submitOtherUser(username: String, password: String) {
        model.onSubmitOtherUser(username: username, password: password)
    }

    func dismissSheet() {
        model.onDismissSheet()
    }

    func refresh() {
        model.refresh()
    }

    deinit {
        model.close()
    }
}
