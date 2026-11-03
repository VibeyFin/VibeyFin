import Foundation
import Shared

/// Lets SwiftUI watch Settings › Quick Connect's shared state and send it events. `ApproveQuickConnectModel` calls back on the main
/// thread.
final class ApproveQuickConnectObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: ApproveQuickConnectUiState?

    private let model = ApproveQuickConnectModel()
    private var started = false

    /// Starts watching when the screen appears, not from `init`, as SwiftUI may build this while it updates a view.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func setCode(_ code: String) {
        model.onCodeChange(code: code)
    }

    func approve() {
        model.onApprove()
    }

    deinit {
        model.close()
    }
}
