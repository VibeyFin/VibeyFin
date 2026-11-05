import Foundation
import Shared

/// Lets SwiftUI watch the shared "Search for subtitles" state and send it events. `SubtitleSearchModel` calls back on the main thread.
final class SubtitleSearchObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: SubtitleSearchUiState?

    private let model = SubtitleSearchModel()
    private var started = false

    /// Starts watching. Called when the screen appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func open(_ itemId: String) { model.onOpen(itemId: itemId) }

    func selectLanguage(_ code: String) { model.onSelectLanguage(code: code) }

    func searchLanguages(_ query: String) { model.onSearchLanguages(query: query) }

    func choose(_ id: String) { model.onChoose(id: id) }

    func retry() { model.onRetry() }

    func dismissMessage() { model.onDismissMessage() }

    func addedHandled() { model.onAddedHandled() }

    deinit {
        model.close()
    }
}
