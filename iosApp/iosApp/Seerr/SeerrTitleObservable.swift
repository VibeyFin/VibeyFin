import Foundation
import Shared

/// Lets SwiftUI watch a Seerr title (its page, its poster menu, the season picker) and send it events. `SeerrTitleModel` calls back
/// on the main thread.
final class SeerrTitleObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: SeerrTitleUiState?

    private let model: SeerrTitleModel
    private var started = false

    init(type: SeerrMediaType, tmdbId: Int32) {
        model = SeerrTitleModel(type: type, tmdbId: tmdbId)
    }

    /// Starts watching. Called when the view appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func retry() { model.onRetry() }
    func selectSeason(_ number: Int32) { model.onSelectSeason(number: number) }
    func retryEpisodes() { model.onRetryEpisodes() }
    func request() { model.onRequest() }
    func request4k() { model.onRequest4k() }
    func pickerAllMissing(_ on: Bool) { model.onPickerAllMissing(on: on) }
    func pickerSeason(_ number: Int32) { model.onPickerSeason(number: number) }
    func pickerQuality(_ is4k: Bool) { model.onPickerQuality(is4k: is4k) }
    func pickerProfile(_ id: Int32) { model.onPickerProfile(id: id) }
    func pickerConfirm() { model.onPickerConfirm() }
    func pickerDismiss() { model.onPickerDismiss() }
    func cancelRequest() { model.onCancelRequest() }
    func retryRequest() { model.onRetryRequest() }
    func noticeShown() { model.onNoticeShown() }
    func itemOpened() { model.onItemOpened() }

    deinit {
        model.close()
    }
}
