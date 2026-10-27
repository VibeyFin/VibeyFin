import Foundation
import Shared

/// Lets SwiftUI watch the shared Downloads state and send it events. `DownloadsModel` calls back on the main thread.
final class DownloadsObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: DownloadsUiState?

    private let model = DownloadsModel()
    private var started = false

    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func act(_ download: Download, _ action: DownloadAction) { model.onAction(download: download, action: action) }

    func removeShow(_ show: DownloadedShow) { model.onRemoveShow(show: show) }

    func removeAll() { model.onRemoveAll() }

    func confirmRemove() { model.onConfirmRemove() }

    func dismissRemoval() { model.onDismissRemoval() }

    deinit {
        model.close()
    }
}
