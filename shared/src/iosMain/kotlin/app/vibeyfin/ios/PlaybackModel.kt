package app.vibeyfin.ios

import app.vibeyfin.domain.LiveDiagnostics
import app.vibeyfin.ui.playback.PlaybackFailure
import app.vibeyfin.ui.playback.PlaybackUiState
import app.vibeyfin.ui.playback.PlaybackViewModel
import app.vibeyfin.ui.subtitles.SubtitleAdded
import org.koin.core.parameter.parametersOf

/**
 * Playing the item [itemId], as SwiftUI needs it. The player is AVPlayer in Swift; it opens the
 * stream the state names and reports what it does through the `on…` functions, with its position in
 * milliseconds.
 */
class PlaybackModel(itemId: String, startOver: Boolean) {
    private val host = ViewModelHost(PlaybackViewModel::class) {
        it.get<PlaybackViewModel> { parametersOf(itemId, startOver) }
    }

    fun observe(onChange: (PlaybackUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onPlaying(positionMs: Long) = host.viewModel.onPlaying(positionMs)

    fun onPaused(positionMs: Long) = host.viewModel.onPaused(positionMs)

    fun onSeeked(positionMs: Long) = host.viewModel.onSeeked(positionMs)

    fun onPosition(positionMs: Long) = host.viewModel.onPosition(positionMs)

    fun onSkip() = host.viewModel.onSkip()

    fun skipBack(positionMs: Long) = host.viewModel.onSkipBack(positionMs)

    fun skipForward(positionMs: Long, durationMs: Long) = host.viewModel.onSkipForward(positionMs, durationMs)

    fun onSeekHandled() = host.viewModel.onSeekHandled()

    fun onEnded(positionMs: Long) = host.viewModel.onEnded(positionMs)

    fun onBackgrounded(positionMs: Long) = host.viewModel.onBackgrounded(positionMs)

    fun onClosed(positionMs: Long) = host.viewModel.onClosed(positionMs)

    fun onEnteredPictureInPicture(positionMs: Long) = host.viewModel.onEnteredPictureInPicture(positionMs)

    /** [closed]: the window was dismissed (the session ends); otherwise it was expanded back. */
    fun onLeftPictureInPicture(positionMs: Long, closed: Boolean) = host.viewModel.onLeftPictureInPicture(positionMs, closed)

    fun onFailed(failure: PlaybackFailure) = host.viewModel.onFailed(failure)

    fun onSelectAudio(index: Int) = host.viewModel.onSelectAudio(index)

    /** [index] is a track's index, or -1 for no subtitles. */
    fun onSelectSubtitle(index: Int) = host.viewModel.onSelectSubtitle(index)

    fun onSubtitleAdded(added: SubtitleAdded) = host.viewModel.onSubtitleAdded(added)

    fun onSelectSpeed(speed: Float) = host.viewModel.onSelectSpeed(speed)

    fun onUserInteraction() = host.viewModel.onUserInteraction()

    fun onContinueWatching() = host.viewModel.onContinueWatching()

    fun onStopWatching() = host.viewModel.onStopWatching()

    fun onPlayNow() = host.viewModel.onPlayNow()

    fun onWatchCredits() = host.viewModel.onWatchCredits()

    fun diagnostics(live: LiveDiagnostics) = host.viewModel.onDiagnostics(live)

    fun onToggleDiagnostics() = host.viewModel.onToggleDiagnostics()

    fun onRetry() = host.viewModel.onRetry()

    fun close() = host.close()
}
