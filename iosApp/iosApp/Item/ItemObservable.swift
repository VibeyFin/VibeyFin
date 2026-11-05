import Foundation
import Shared

/// Lets SwiftUI watch the shared state of one item's page and send it events. `ItemModel` calls
/// back on the main thread.
final class ItemObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: ItemUiState?

    private let model: ItemModel
    private var started = false

    init(itemId: String) {
        model = ItemModel(itemId: itemId)
    }

    /// Starts watching. Called when the screen appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func retry() {
        model.onRetry()
    }

    func subtitleAdded(_ added: SubtitleAdded) {
        model.onSubtitleAdded(added: added)
    }

    func dismissNotice() {
        model.onDismissNotice()
    }

    func togglePlayed() {
        model.onTogglePlayed()
    }

    func retryExtras() { model.onRetryExtras() }

    func toggleFavorite() {
        model.onToggleFavorite()
    }

    func selectSeason(_ id: String) {
        model.onSelectSeason(seasonId: id)
    }

    func toggleEpisodePlayed(_ id: String) {
        model.onToggleEpisodePlayed(episodeId: id)
    }

    func retrySimilar() {
        model.onRetrySimilar()
    }

    func retryCollections() {
        model.onRetryCollections()
    }

    func retrySeasons() {
        model.onRetrySeasons()
    }

    func retryEpisodes() {
        model.onRetryEpisodes()
    }

    func retryAction() {
        model.onRetryAction()
    }

    func dismissMessage() {
        model.onDismissMessage()
    }

    // Downloads.

    func download() { model.onDownload() }

    func downloadEpisode(_ id: String) { model.onDownloadEpisode(episodeId: id) }

    func downloadSeries() { model.onDownloadSeries() }

    func downloadSeason(_ id: String) { model.onDownloadSeason(seasonId: id) }

    func downloadAction(_ id: String, _ action: DownloadAction) { model.onDownloadAction(itemId: id, action: action) }

    func removeSeriesDownloads() { model.onRemoveSeriesDownloads() }

    func toggleUnwatchedOnly() { model.onToggleUnwatchedOnly() }

    func confirmDownload() { model.onConfirmDownload() }

    func dismissDownloadPrompt() { model.onDismissDownloadPrompt() }

    func confirmRemove() { model.onConfirmRemove() }

    func dismissRemoval() { model.onDismissRemoval() }

    deinit {
        model.close()
    }
}
