import Foundation
import Shared

/// Lets SwiftUI watch the shared settings state. `SettingsModel` calls back on the main thread.
final class SettingsObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: SettingsUiState?

    private let model = SettingsModel()
    private var started = false

    /// Starts watching. Called when the screen appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func setAlwaysAsk(_ value: Bool) {
        model.onAlwaysAskChanged(value: value)
    }

    func setAllowTranscoding(_ value: Bool) {
        model.onAllowTranscodingChanged(value: value)
    }

    deinit {
        model.close()
    }
}
