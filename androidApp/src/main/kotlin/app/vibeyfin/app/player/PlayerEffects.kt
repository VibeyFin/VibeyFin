package app.vibeyfin.app.player

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import app.vibeyfin.domain.DynamicRange
import app.vibeyfin.domain.TrickplayTrack
import app.vibeyfin.ui.playback.PlaybackStream
import app.vibeyfin.ui.playback.PlaybackUiState
import app.vibeyfin.ui.playback.PlaybackViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// Keeps the viewer's view model and the clock in step with what the player does
@Composable
internal fun PlayerEventsEffect(player: ExoPlayer, viewModel: PlaybackViewModel, clock: PlaybackClock, stillWatching: Boolean, openedAt: Long) {
    val stillWatchingNow by rememberUpdatedState(stillWatching)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                Log.d(TIMING_TAG, "first frame ${SystemClock.elapsedRealtime() - openedAt} ms after the screen opened")
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // Play and pause from the picture-in-picture window, a headset or a media key count as the viewer being there;
                // the pause for the "still watching?" question is ours, not theirs.
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST && !stillWatchingNow) viewModel.onUserInteraction()

                clock.playing = playWhenReady

                if (playWhenReady) viewModel.onPlaying(player.currentPosition) else viewModel.onPaused(player.currentPosition)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                clock.buffering = playbackState == Player.STATE_BUFFERING

                if (playbackState == Player.STATE_READY && clock.seekAt != 0L) {
                    Log.d(TIMING_TAG, "seek ready after ${SystemClock.elapsedRealtime() - clock.seekAt} ms")

                    clock.seekAt = 0L
                }

                if (player.duration != C.TIME_UNSET) clock.durationMs = player.duration
                if (playbackState == Player.STATE_ENDED) viewModel.onEnded(player.duration.coerceAtLeast(0))
            }

            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) viewModel.onSeeked(new.positionMs)
            }

            override fun onPlayerError(error: PlaybackException) {
                viewModel.onFailed(error.toFailure())
            }
        }

        player.addListener(listener)
        onDispose {
            // Covers a screen that goes away without the close button, such as the activity being destroyed
            viewModel.onClosed(player.currentPosition)
            player.removeListener(listener)
            player.release()
        }
    }
}

// The buffer length is fixed when the player is built, so the profile's value must be known by then. The state starts
// with the default and gets the saved value a moment later, before any stream arrives; until the first stream is
// there the player follows it (nothing is loaded yet, so a rebuild costs nothing), after that it stays as built.
// A changed setting therefore applies from the next video.
@Composable
internal fun rememberPlayer(context: Context, http: DefaultHttpDataSource.Factory, savedBufferAheadSeconds: Int, hasStream: Boolean): ExoPlayer {
    var bufferAheadSeconds by remember { mutableIntStateOf(savedBufferAheadSeconds) }
    var streamSeen by remember { mutableStateOf(false) }

    if (hasStream) streamSeen = true
    if (!streamSeen) bufferAheadSeconds = savedBufferAheadSeconds

    return remember(bufferAheadSeconds) { buildPlayer(context, http, bufferAheadSeconds) }
}

// Which kind of view the picture needs, known once the first stream arrives; it stays until a stream of the other kind replaces it
@Composable
internal fun rememberHdrVideo(stream: PlaybackStream?): Boolean? {
    val context = LocalContext.current
    var hdrVideo by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(stream?.id) {
        val range = stream?.dynamicRange ?: return@LaunchedEffect

        hdrVideo = range != DynamicRange.Sdr

        Log.d(HDR_TAG, "picture is $range: ${if (range != DynamicRange.Sdr) "SurfaceView layout" else "TextureView layout"}; ${displayHdrTypes(context)}")
    }

    return hdrVideo
}

// A new stream: the first one, a retry, or the transcode fallback that carries on from where it was
@Composable
internal fun OpenStreamEffect(player: ExoPlayer, http: DefaultHttpDataSource.Factory, state: PlaybackUiState, viewModel: PlaybackViewModel, openedAt: Long) {
    val stream = state.stream

    LaunchedEffect(stream?.id) {
        if (stream == null) {
            player.stop()
        } else {
            Log.d(TIMING_TAG, "stream ready ${SystemClock.elapsedRealtime() - openedAt} ms after the screen opened (negotiation done)")
            open(player, http, stream, state.title, state.subtitle, state.artworkUrl)
            viewModel.onPlaying(stream.startPositionMs)
        }
    }
}

// The scrub-preview tiles belong to the item: a new track (the next episode) starts with an empty cache, and leaving the screen drops it
@Composable
internal fun rememberTrickplay(track: TrickplayTrack?, headers: Map<String, String>?): TrickplayTiles? {
    val context = LocalContext.current

    return remember(track, headers) {
        if (track != null && headers != null && track.tilesUrl.isNotEmpty()) TrickplayTiles(context.applicationContext, track, headers) else null
    }
}

// Audio and subtitles: show what the state says is selected, now and whenever either changes. The
// player's own tracks only exist once it has read the file, so it also runs when those change.
@Composable
internal fun TrackSelectionEffects(player: ExoPlayer, state: PlaybackUiState) {
    val stream = state.stream
    var playerTracks by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                playerTracks++
            }
        }

        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(stream?.id, state.audioTracks, state.subtitleTracks, playerTracks) {
        if (stream != null) player.applyTrackSelection(stream, state.audioTracks, state.subtitleTracks)
    }

    // The chosen speed, now and for each stream the player opens
    LaunchedEffect(stream?.id, state.speed) { player.setPlaybackSpeed(state.speed) }
}

// What the player measures goes to the shared code, only while the overlay is on and only when it changed.
// Dropped frames and bandwidth arrive as events; the rest is read off the player.
@Composable
internal fun rememberStatsFeed(player: ExoPlayer, viewModel: PlaybackViewModel, streamId: Any?, enabled: Boolean): StatsFeed {
    val statsOn by rememberUpdatedState(enabled)
    val stats = remember(player) { StatsFeed(player, viewModel::onDiagnostics) { statsOn } }

    DisposableEffect(player) {
        player.addAnalyticsListener(stats)
        onDispose { player.removeAnalyticsListener(stats) }
    }

    LaunchedEffect(streamId) { stats.reset() }
    LaunchedEffect(statsOn) { if (statsOn) stats.publish() }

    return stats
}

// A skip: the shared code asks for a seek; it goes through the same path as the scrubber, so the player's seek event reports it to the server
@Composable
internal fun PendingSeekEffect(pendingSeekMs: Long?, player: ExoPlayer, clock: PlaybackClock, viewModel: PlaybackViewModel) {
    LaunchedEffect(pendingSeekMs) {
        pendingSeekMs?.let {
            clock.startSeek(it)

            player.seekTo(it)
            viewModel.onSeekHandled()
        }
    }
}

@Composable
internal fun PositionTickerEffect(player: ExoPlayer, viewModel: PlaybackViewModel, clock: PlaybackClock, stats: StatsFeed) {
    LaunchedEffect(player) {
        while (isActive) {
            clock.positionMs = player.currentPosition
            clock.bufferedMs = player.bufferedPosition

            if (player.duration != C.TIME_UNSET) clock.durationMs = player.duration
            if (player.isPlaying || player.playbackState == Player.STATE_READY) viewModel.onPosition(clock.positionMs)

            stats.publish()
            delay(POSITION_TICK_MS)
        }
    }
}

// "Are you still watching?" pauses the video; "Keep watching" plays on
@Composable
internal fun StillWatchingPauseEffect(stillWatching: Boolean, player: ExoPlayer) {
    var pausedForPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(stillWatching) {
        if (stillWatching) {
            pausedForPrompt = player.playWhenReady

            player.pause()
        } else if (pausedForPrompt) {
            pausedForPrompt = false

            player.play()
        }
    }
}

// Held while the track or speed sheet is open; closing one changes a key, so the full delay starts again
@Composable
internal fun ControlsAutoHideEffect(chrome: PlayerChrome, playing: Boolean, screenReaderOn: Boolean) {
    LaunchedEffect(chrome.controlsVisible, chrome.interactions, playing, chrome.scrubbing, chrome.showTracks, chrome.showSpeeds) {
        val keepControlsUp = chrome.scrubbing || chrome.showTracks || chrome.showSpeeds || screenReaderOn

        if (chrome.controlsVisible && playing && !keepControlsUp) {
            delay(CONTROLS_HIDE_MS)

            chrome.controlsVisible = false
        }
    }
}

// Leaving the app pauses the video and tells the server it stopped; the user plays on when back.
// ON_PAUSE, not ON_STOP: the stop can arrive seconds late, and by then the video surface is gone
// and a player still decoding into it fails.
@Composable
internal fun PauseWhenBackgroundedEffect(player: ExoPlayer, viewModel: PlaybackViewModel) {
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        // Not while it turns into a picture-in-picture window, which plays on
        if (context.findActivity()?.isInPictureInPictureMode == true) return@LifecycleEventEffect

        player.pause()
        viewModel.onBackgrounded(player.currentPosition)
    }
}
