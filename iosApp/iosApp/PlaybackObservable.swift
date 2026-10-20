import Foundation
import Shared

/// Lets SwiftUI watch the shared playback state of one item and pass on what the player does.
/// `PlaybackModel` calls back on the main thread; every position is in milliseconds.
final class PlaybackObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: PlaybackUiState?

    private let model: PlaybackModel
    private var started = false

    init(itemId: String, startOver: Bool) {
        model = PlaybackModel(itemId: itemId, startOver: startOver)
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

    func playing(_ positionMs: Int64) {
        model.onPlaying(positionMs: positionMs)
    }

    func paused(_ positionMs: Int64) {
        model.onPaused(positionMs: positionMs)
    }

    func seeked(_ positionMs: Int64) {
        model.onSeeked(positionMs: positionMs)
    }

    func position(_ positionMs: Int64) {
        model.onPosition(positionMs: positionMs)
    }

    func ended(_ positionMs: Int64) {
        model.onEnded(positionMs: positionMs)
    }

    func backgrounded(_ positionMs: Int64) {
        model.onBackgrounded(positionMs: positionMs)
    }

    func closed(_ positionMs: Int64) {
        model.onClosed(positionMs: positionMs)
    }

    /// "Play now" on the next-episode card.
    func playNow() {
        model.onPlayNow()
    }

    /// "Watch credits" on the next-episode card: it goes, and the episode plays out.
    func watchCredits() {
        model.onWatchCredits()
    }

    func failed(_ failure: PlaybackFailure) {
        model.onFailed(failure: failure)
    }

    /// What the player measures about the stream, for the stats overlay.
    func diagnostics(_ live: LiveDiagnostics) {
        model.diagnostics(live: live)
    }

    /// Plays the audio track with this `TrackOption.index`.
    func selectAudio(_ index: Int32) {
        model.onSelectAudio(index: index)
    }

    /// Shows the subtitle track with this `TrackOption.index`; the "Off" row's index turns them off.
    func selectSubtitle(_ index: Int32) {
        model.onSelectSubtitle(index: index)
    }

    func retry() {
        model.onRetry()
    }

    /// The viewer tapped the skip button; the model answers with a seek to the segment's end.
    func skip() {
        model.onSkip()
    }

    /// The seek the model asked for has been handed to the player.
    func seekHandled() {
        model.onSeekHandled()
    }

    deinit {
        model.close()
    }
}
