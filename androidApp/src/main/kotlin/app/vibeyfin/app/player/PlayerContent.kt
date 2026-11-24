package app.vibeyfin.app.player

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.subtitles.SubtitleSearch
import app.vibeyfin.app.theme.FixedColors
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.pageMargin
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.playback.PlaybackUiState
import app.vibeyfin.ui.playback.PlaybackViewModel
import app.vibeyfin.ui.playback.TrackOption
import app.vibeyfin.ui.playback.UpNext

private const val UP_NEXT_MS = 260
private const val UP_NEXT_EXIT_MS = 180
private const val UP_NEXT_START_SCALE = 0.92f

// Everything the screen draws: the picture, the touch layer, then the controls and the overlays on top of it
@Composable
internal fun PlayerContent(
    state: PlaybackUiState,
    player: ExoPlayer,
    clock: PlaybackClock,
    chrome: PlayerChrome,
    turn: PlayerTurnSequence,
    pip: PlayerPip,
    swipe: PlayerSwipe,
    viewModel: PlaybackViewModel,
    hdrVideo: Boolean?,
    subtitleStyle: SubtitleViewStyle,
    trickplay: TrickplayTiles?,
    playable: Boolean,
    screenReaderOn: Boolean,
    onClose: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
) {
    val overlaysShown = !chrome.leaving && !pip.active
    // A run begun on the skip buttons keeps the controls up, and so does TalkBack; one begun on the video hides them
    val seeking: Boolean = state.skipRun?.fromControls == false && !screenReaderOn

    Box(
        Modifier.fillMaxSize().background(FixedColors.videoBackground).onGloballyPositioned {
            val b = it.boundsInWindow()

            onBoundsChanged(Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()))
        },
    ) {
        PlayerVideoSurface(hdrVideo, player, hidden = turn.pictureHidden, subtitleStyle)
        PlayerTapLayer(
            seeking = state.skipRun != null,
            swipe = Modifier.swipeForLevels(swipe, enabled = state.swipeGestures && overlaysShown) {
                viewModel.onUserInteraction()
                chrome.touched()
            },
            onTap = {
                viewModel.onUserInteraction()
                chrome.controlsVisible = !chrome.controlsVisible
                chrome.touched()
            },
            onSkip = { side ->
                if (playable) {
                    viewModel.onUserInteraction()
                    if (side == SkipSide.Back) viewModel.onSkipBack(clock.positionMs) else viewModel.onSkipForward(clock.positionMs, clock.durationMs)
                    chrome.touched()
                }
            },
        )

        if (overlaysShown) {
            SeekIndicator(state.skipRun)
            ControlsLayer(state, player, clock, chrome, pip, viewModel, trickplay, playable, seeking, onClose)
            StatusOverlays(state, chrome, seeking, viewModel)
        }

        val waiting = state.loading || (clock.buffering && state.stream != null)

        if (!chrome.leaving && waiting) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (overlaysShown) PromptAndSheets(state, chrome, viewModel)

        UpNextOverlay(state, visible = overlaysShown && state.message == null && !seeking, viewModel)

        // Over everything else on the video, so the up next card or the skip button never covers it
        if (overlaysShown) SwipeLevelIndicator(swipe)

        state.message?.let { message ->
            Column(Modifier.align(Alignment.Center).safeDrawingPadding().padding(horizontal = pageMargin())) {
                LoadError(message = message, onRetry = viewModel::onRetry)
            }
        }

        if (overlaysShown) {
            PlayerDimOverlay(state.screenDimmed) {
                viewModel.onUserInteraction()
                chrome.controlsVisible = true
                chrome.touched()
            }
        }
    }
}

@Composable
private fun ControlsLayer(
    state: PlaybackUiState,
    player: ExoPlayer,
    clock: PlaybackClock,
    chrome: PlayerChrome,
    pip: PlayerPip,
    viewModel: PlaybackViewModel,
    trickplay: TrickplayTiles?,
    playable: Boolean,
    seeking: Boolean,
    onClose: () -> Unit,
) {
    // Every control counts as the viewer being there and restarts the hide timer
    fun touched() {
        viewModel.onUserInteraction()
        chrome.touched()
    }

    val visible: Boolean = chrome.controlsVisible || state.message != null || seeking
    var seekingWhenShown: Boolean by remember { mutableStateOf(false) }

    // Fading out after a run, the controls still draw as seeking, or the top bar would flash up
    if (visible) seekingWhenShown = seeking

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        PlayerControls(
            title = state.title,
            subtitle = state.subtitle,
            positionMs = clock.positionMs,
            bufferedMs = clock.bufferedMs,
            durationMs = clock.durationMs,
            playing = clock.playing,
            hasTrackChoices = state.hasTrackChoices && state.message == null,
            badge = state.badge,
            showTransport = playable,
            seeking = seekingWhenShown,
            speedLabel = Strings.speedValue(state.speed),
            trickplay = trickplay,
            episodeControls = state.episodeControls,
            previousEpisode = state.previousEpisode,
            nextEpisode = state.nextEpisode,
            canPlayPrevious = state.canPlayPrevious,
            canPlayNext = state.canPlayNext,
            onPreviousEpisode = {
                viewModel.onUserInteraction()
                viewModel.onPlayPrevious()
            },
            onNextEpisode = {
                viewModel.onUserInteraction()
                viewModel.onPlayNow()
            },
            onToggleStats = {
                touched()
                viewModel.onToggleDiagnostics()
            },
            onClose = onClose,
            onTracks = {
                viewModel.onUserInteraction()
                chrome.showTracks = true
            },
            onSpeed = {
                viewModel.onUserInteraction()
                chrome.showSpeeds = true
            },
            onPip = if (state.pictureInPicture && pip.available && state.stream != null) {
                {
                    viewModel.onUserInteraction()
                    pip.enter()
                }
            } else {
                null
            },
            onTogglePlay = {
                touched()
                player.playWhenReady = !player.playWhenReady
            },
            onScrubbing = {
                touched()
                chrome.scrubbing = it
            },
            skipBackSeconds = state.skipBackSeconds,
            skipForwardSeconds = state.skipForwardSeconds,
            onSkipBack = {
                touched()
                viewModel.onSkipBack(clock.positionMs, fromControls = true)
            },
            onSkipForward = {
                touched()
                viewModel.onSkipForward(clock.positionMs, clock.durationMs, fromControls = true)
            },
            onSeek = {
                viewModel.onUserInteraction()
                clock.startSeek(it)
                player.seekTo(it)
            },
        )
    }
}

// The stats and the skip button stay up when the controls hide, but not under an error, which fills the same part of the screen
@Composable
private fun BoxScope.StatusOverlays(state: PlaybackUiState, chrome: PlayerChrome, seeking: Boolean, viewModel: PlaybackViewModel) {
    val noError = state.message == null
    val diagnostics = state.diagnostics.takeIf { state.showDiagnostics }
    val skip = state.skipOffer

    // It takes no touches, so taps still reach the player
    if (noError && diagnostics != null) {
        StatsOverlay(
            diagnostics,
            Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(start = pageMargin(), top = STATS_TOP_INSET),
        )
    }

    state.notice?.let { notice ->
        Text(
            notice,
            style = MaterialTheme.typography.bodyMedium,
            color = VibeyFinTokens.Colors.ink,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(top = STATS_TOP_INSET)
                .background(VibeyFinTokens.Colors.scrimStrong, RoundedCornerShape(VibeyFinTokens.Radius.m3Md))
                .padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s2),
        )
    }

    // Offered for the whole segment, controls up or not; above the scrubber while that shows
    if (noError && skip != null && !seeking) {
        val lift by animateDpAsState(if (chrome.controlsVisible) SKIP_LIFT_WITH_CONTROLS else 0.dp, label = "skipLift")

        SkipButton(
            label = skip.label,
            onClick = viewModel::onSkip,
            modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = pageMargin(), bottom = 16.dp + lift),
        )
    }
}

@Composable
private fun PromptAndSheets(state: PlaybackUiState, chrome: PlayerChrome, viewModel: PlaybackViewModel) {
    if (state.stillWatching) {
        StillWatchingPrompt(onContinue = viewModel::onContinueWatching, onStop = viewModel::onStopWatching)
    }

    if (chrome.showSpeeds) {
        SpeedSheet(
            speeds = state.speeds.mapIndexed { index, speed -> TrackOption(index, Strings.speedValue(speed), speed == state.speed) },
            onSelect = {
                viewModel.onSelectSpeed(state.speeds[it])
                chrome.showSpeeds = false
            },
            onDismiss = { chrome.showSpeeds = false },
        )
    }

    if (chrome.showTracks) {
        TrackSheet(
            audio = state.audioTracks,
            subtitles = state.subtitleChoices,
            onSelectAudio = {
                viewModel.onSelectAudio(it)
                chrome.showTracks = false
            },
            onSelectSubtitle = {
                viewModel.onSelectSubtitle(it)
                chrome.showTracks = false
            },
            onSearchSubtitles = if (state.offersSubtitleSearch) {
                {
                    chrome.showTracks = false
                    chrome.showSubtitleSearch = true
                }
            } else {
                null
            },
            onDismiss = { chrome.showTracks = false },
        )
    }

    LaunchedEffect(state.subtitleSearchItemId) { chrome.showSubtitleSearch = false }

    SubtitleSearch(
        itemId = state.subtitleSearchItemId.orEmpty(),
        visible = chrome.showSubtitleSearch && state.subtitleSearchItemId != null,
        onDismiss = { chrome.showSubtitleSearch = false },
        onAdded = viewModel::onSubtitleAdded,
        onFailedWhileClosed = viewModel::onNotice,
        keepSystemBarsHidden = true,
    )
}

// The next episode, in the last seconds of this one. It grows in and fades like a screen opening, and goes the
// same way; it keeps the last card while it fades out, because the state is already empty by then. Part of the
// same Box as the video so it travels with it when the screen leaves.
@Composable
private fun BoxScope.UpNextOverlay(state: PlaybackUiState, visible: Boolean, viewModel: PlaybackViewModel) {
    var lastUpNext by remember { mutableStateOf<UpNext?>(null) }

    state.upNext?.let { lastUpNext = it }

    AnimatedVisibility(
        visible = state.upNext != null && visible,
        enter = scaleIn(tween(UP_NEXT_MS), initialScale = UP_NEXT_START_SCALE) + fadeIn(tween(UP_NEXT_MS)),
        exit = scaleOut(tween(UP_NEXT_EXIT_MS), targetScale = UP_NEXT_START_SCALE) + fadeOut(tween(UP_NEXT_EXIT_MS)),
        modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = pageMargin(), bottom = 72.dp),
    ) {
        lastUpNext?.let { upNext ->
            UpNextCard(upNext = upNext, onPlayNow = viewModel::onPlayNow, onWatchCredits = viewModel::onWatchCredits)
        }
    }
}
