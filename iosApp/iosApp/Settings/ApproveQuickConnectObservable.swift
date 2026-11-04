import Foundation
import Shared

final class ApproveQuickConnectObservable: ObservableObject {
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
