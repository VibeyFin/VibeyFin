import Foundation
import Shared

/// Lets SwiftUI watch the shared state of a category page (the list, the Sort and Genre buttons and the picker one of
/// them opened) and send it events. `FilteredListModel` calls back on the main thread.
final class FilteredListObservable: ObservableObject {
    @Published private(set) var state: FilteredListUiState

    private let model: FilteredListModel
    private var started = false

    init(filter: HomeFilter) {
        model = FilteredListModel(filter: filter)
        state = model.state
    }

    /// The name at the top of the page.
    var title: String { model.title }

    /// Starts watching. Called when the screen appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func loadMore() { model.onLoadMore() }

    func retry() { model.onRetry() }

    func chip(_ kind: ListChipKind) { model.onChip(kind: kind) }

    func sheetQuery(_ text: String) { model.onSheetQuery(text: text) }

    func pick(_ id: String) { model.onPick(id: id) }

    func dismissSheet() { model.onDismissSheet() }

    func retrySheet() { model.onRetrySheet() }

    deinit {
        model.close()
    }
}
