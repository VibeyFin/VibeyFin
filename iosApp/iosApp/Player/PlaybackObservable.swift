import Foundation
import Shared

/// Lets SwiftUI watch the shared playback state of one item and pass on what the player does.
/// `PlaybackModel` calls back on the main thread; every position is in milliseconds.
final class PlaybackObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: PlaybackUiState?

    private let model: PlaybackModel
    private var started = false
    private let itemId: String

    init(itemId: String, startOver: Bool) {
        self.itemId = itemId
        model = PlaybackModel(itemId: itemId, startOver: startOver)
    }

    /// Starts watching. Called when the screen appears, not from `init`, because SwiftUI may build
    /// the object while it is updating a view and must not be sent state changes then.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            guard let self else { return }
            self.state = state
        }
    }

    func toggleStats() {
        model.onToggleDiagnostics()
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

    /// The picture-in-picture window appeared.
    func enteredPictureInPicture(_ positionMs: Int64) {
        model.onEnteredPictureInPicture(positionMs: positionMs)
    }

    /// It went away: dismissed (`closed`, which ends the session) or expanded back into the app.
    func leftPictureInPicture(_ positionMs: Int64, closed: Bool) {
        model.onLeftPictureInPicture(positionMs: positionMs, closed: closed)
    }

    /// The viewer touched or pressed something: the count towards "Are you still watching?" starts over.
    func userInteraction() {
        model.onUserInteraction()
    }

    /// "Keep watching" on the prompt.
    func continueWatching() {
        model.onContinueWatching()
    }

    /// "Stop watching" on the prompt: the same as closing the player.
    func stopWatching() {
        model.onStopWatching()
    }

    /// "Play now" on the next-episode card, or the Next episode button.
    func playNow() {
        model.onPlayNow()
    }

    /// The Previous episode button.
    func playPrevious() {
        model.onPlayPrevious()
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

    /// Plays at `speed`, one of the state's `speeds`.
    func selectSpeed(_ speed: Float) {
        model.onSelectSpeed(speed: speed)
    }

    /// Shows the subtitle track with this `TrackOption.index`; the "Off" row's index turns them off.
    func selectSubtitle(_ index: Int32) {
        model.onSelectSubtitle(index: index)
    }

    func subtitleAdded(_ added: SubtitleAdded) {
        model.onSubtitleAdded(added: added)
    }

    func notice(_ text: String) {
        model.onNotice(text: text)
    }

    func retry() {
        model.onRetry()
    }

    /// The viewer tapped the skip button; the model answers with a seek to the segment's end.
    func skip() {
        model.onSkip()
    }

    /// A skip button or double-tap: the model answers with a seek by the chosen seconds.
    func skipBack(_ positionMs: Int64) {
        model.skipBack(positionMs: positionMs)
    }

    func skipForward(_ positionMs: Int64, durationMs: Int64) {
        model.skipForward(positionMs: positionMs, durationMs: durationMs)
    }

    /// The seek the model asked for has been handed to the player.
    func seekHandled() {
        model.onSeekHandled()
    }

    deinit {
        model.close()
    }
}
