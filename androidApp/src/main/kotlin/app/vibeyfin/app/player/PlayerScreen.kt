package app.vibeyfin.app.player

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.datasource.DefaultHttpDataSource
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.playback.PlaybackUiState
import app.vibeyfin.ui.playback.PlaybackViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

internal const val CONTROLS_HIDE_MS = 3_000L

internal const val POSITION_TICK_MS = 500L

internal const val TIMING_TAG = "VibeyFinTiming"

internal const val HDR_TAG = "VibeyFinHdr"

// Keeps the stats below the controls' top bar so they don't jump when the controls come and go
internal val STATS_TOP_INSET = 72.dp

// Clears the scrubber and the times
internal val SKIP_LIFT_WITH_CONTROLS = 80.dp

@Composable
fun PlayerRoute(
    itemId: String,
    startOver: Boolean,
    onBack: () -> Unit,
    onExit: (HomeExit) -> Unit,
    viewModel: PlaybackViewModel = koinViewModel(parameters = { parametersOf(itemId, startOver) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val window = rememberPlayerWindow()
    val turn = rememberPlayerTurnSequence(window)

    val clock = remember { PlaybackClock() }
    val chrome = remember { PlayerChrome() }
    val swipe = rememberPlayerSwipe()
    val openedAt = remember { SystemClock.elapsedRealtime() }
    val http = remember { DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(CONNECT_TIMEOUT_MS) }
    val player = rememberPlayer(context, http, state.bufferAheadSeconds, hasStream = state.stream != null)
    val stream = state.stream

    LeaveEffects(state, chrome, window, turn, onExit, onBack)

    val close = {
        if (!chrome.leaving) {
            chrome.leaving = true

            viewModel.onClosed(player.currentPosition)

            // Stopping now lets the decoder be released while the screen slides away, so releasing
            // the player at the end, which waits for that, is quick. The last frame stays up.
            player.stop()
        }
    }

    BackHandler(onBack = close)

    PlayerEventsEffect(player, viewModel, clock, state.stillWatching, openedAt)
    OpenStreamEffect(player, http, state, viewModel, openedAt)
    TrackSelectionEffects(player, state)
    PendingSeekEffect(state.pendingSeekMs, player, clock, viewModel)
    PositionTickerEffect(player, viewModel, clock, rememberStatsFeed(player, viewModel, stream?.id, state.showDiagnostics))
    StillWatchingPauseEffect(state.stillWatching, player)
    PauseWhenBackgroundedEffect(player, viewModel)
    rememberPlayerSession(player)

    // A new stream (the next episode) takes the track sheet with it: it listed the old one's tracks
    LaunchedEffect(stream?.id) { chrome.closeSheets() }

    var screenBounds by remember { mutableStateOf<Rect?>(null) }
    val videoRatio = state.videoRatio()

    // Leaving the app turns the screen into a picture-in-picture window while something plays. The video
    // carries on and so does the session; closing the window ends it, expanding it brings the screen back.
    // A paused video (by the viewer, or for the still-watching question, which a window would hide) stops
    // as before instead.
    val pip = rememberPlayerPip(
        sourceRect = screenBounds?.let { videoRect(it, videoRatio) },
        enabled = state.pictureInPicture && state.stream != null && state.message == null && !chrome.leaving && clock.playing && !state.stillWatching,
        aspectRatio = videoRatio,
        onEntered = { viewModel.onEnteredPictureInPicture(player.currentPosition) },
        onLeft = { closed ->
            viewModel.onLeftPictureInPicture(player.currentPosition, closed)

            if (closed) {
                chrome.leaving = true
                player.stop()
            }
        },
    )

    // The video ended in the window: there is nothing left to show in it, so it goes away
    LaunchedEffect(state.finished, pip.active) {
        if (state.finished && pip.active) context.findActivity()?.moveTaskToBack(true)
    }

    LaunchedEffect(pip.active) { if (pip.active) chrome.controlsVisible = false }

    val screenReaderOn = remember(context) { context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true }

    ControlsAutoHideEffect(chrome, clock.playing, screenReaderOn)

    val subtitleStyle = remember(state.captionsFollowSystem, state.subtitleSize, state.subtitleTextColor, state.subtitleBackground) {
        subtitleStyle(state.captionsFollowSystem, state.subtitleSize, state.subtitleTextColor, state.subtitleBackground)
    }

    // Whether there is something to seek in: skip buttons and double-taps do nothing while loading, buffering or showing an error
    val playable = state.stream != null && state.message == null && !state.loading && !clock.buffering

    PlayerContent(
        state = state,
        player = player,
        clock = clock,
        chrome = chrome,
        turn = turn,
        pip = pip,
        swipe = swipe,
        viewModel = viewModel,
        hdrVideo = rememberHdrVideo(stream),
        subtitleStyle = subtitleStyle,
        trickplay = rememberTrickplay(state.trickplay, stream?.headers),
        playable = playable,
        screenReaderOn = screenReaderOn,
        onClose = close,
        onBoundsChanged = { screenBounds = it },
    )
}

// The session ended, or the video finished: the screen leaves
@Composable
private fun LeaveEffects(state: PlaybackUiState, chrome: PlayerChrome, window: PlayerWindow?, turn: PlayerTurnSequence, onExit: (HomeExit) -> Unit, onBack: () -> Unit) {
    LaunchedEffect(state.exit) {
        if (state.exit != null) chrome.leaving = true
    }

    LaunchedEffect(state.finished) {
        if (state.finished) chrome.leaving = true
    }

    LaunchedEffect(chrome.leaving) {
        if (chrome.leaving) {
            window?.let { turn.close(it) }
            state.exit?.let(onExit) ?: onBack()
        } else {
            turn.open()
        }
    }
}

// Until the player has measured the picture, a widescreen one is assumed
private fun PlaybackUiState.videoRatio(): Float {
    val width = diagnostics?.width ?: 0
    val height = diagnostics?.height ?: 0

    return if (width > 0 && height > 0) width.toFloat() / height else 16f / 9f
}
