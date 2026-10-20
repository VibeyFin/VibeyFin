package com.liamgleeson.vibeyfin.ios

import com.liamgleeson.vibeyfin.domain.LiveDiagnostics
import com.liamgleeson.vibeyfin.ui.playback.PlaybackFailure
import com.liamgleeson.vibeyfin.ui.playback.PlaybackUiState
import com.liamgleeson.vibeyfin.ui.playback.PlaybackViewModel
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

    fun onSeekHandled() = host.viewModel.onSeekHandled()

    fun onEnded(positionMs: Long) = host.viewModel.onEnded(positionMs)

    fun onBackgrounded(positionMs: Long) = host.viewModel.onBackgrounded(positionMs)

    fun onClosed(positionMs: Long) = host.viewModel.onClosed(positionMs)

    fun onFailed(failure: PlaybackFailure) = host.viewModel.onFailed(failure)

    fun onSelectAudio(index: Int) = host.viewModel.onSelectAudio(index)

    /** [index] is a track's index, or -1 for no subtitles. */
    fun onSelectSubtitle(index: Int) = host.viewModel.onSelectSubtitle(index)

    fun onPlayNow() = host.viewModel.onPlayNow()

    fun onWatchCredits() = host.viewModel.onWatchCredits()

    fun diagnostics(live: LiveDiagnostics) = host.viewModel.onDiagnostics(live)

    fun onRetry() = host.viewModel.onRetry()

    fun close() = host.close()
}
