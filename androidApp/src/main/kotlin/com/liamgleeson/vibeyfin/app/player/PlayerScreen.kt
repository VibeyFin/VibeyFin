package com.liamgleeson.vibeyfin.app.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.liamgleeson.vibeyfin.R
import com.liamgleeson.vibeyfin.app.components.LoadError
import com.liamgleeson.vibeyfin.app.theme.screenMargin
import com.liamgleeson.vibeyfin.ui.home.HomeExit
import com.liamgleeson.vibeyfin.ui.playback.PlaybackStream
import com.liamgleeson.vibeyfin.ui.playback.PlaybackViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.concurrent.thread
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import com.liamgleeson.vibeyfin.ui.playback.UpNext

/** How long the controls stay up after the last touch while the video plays. */
private const val CONTROLS_HIDE_MS = 3_000L

/** How often the scrubber moves, and the position is handed to the shared code. */
private const val POSITION_TICK_MS = 500L

private const val TIMING_TAG = "VibeyFinTiming"

@Composable
fun PlayerRoute(
    itemId: String,
    startOver: Boolean,
    onBack: () -> Unit,
    onExit: (HomeExit) -> Unit,
    viewModel: PlaybackViewModel = koinViewModel(parameters = { parametersOf(itemId, startOver) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val window = rememberPlayerWindow()

    // Leaving: the controls go at once, the player pauses, the screen starts turning upright and is
    // popped with the app's own transition, which carries the paused video along with it.
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(state.exit) {
        // The session ended: the app leaves for another screen, which should not appear sideways either.
        state.exit?.let {
            window?.restore()
            onExit(it)
        }
    }
    LaunchedEffect(state.finished) {
        if (state.finished) leaving = true
    }

    val context = LocalContext.current
    val http = remember { DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(CONNECT_TIMEOUT_MS) }
    val player = remember { buildPlayer(context, http) }

    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    val close = {
        if (!leaving) {
            leaving = true
            viewModel.onClosed(player.currentPosition)
            // Stopping now lets the decoder be released while the screen slides away, so releasing
            // the player at the end, which waits for that, is quick. The last frame stays up.
            player.stop()
        }
    }
    BackHandler(onBack = close)
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        // Nothing to wait for: the video leaves with the screen. Turning the screen upright re-lays out
        // everything and would stall the slide, so that waits until the screen is gone (see PlayerWindow).
        onBack()
    }

    // The shared ViewModel is the one that talks to the server; the player only says what happens.
    // Timing for the slow-load and slow-seek reports: screen opened, stream handed over, first frame; seek to ready.
    val openedAt = remember { SystemClock.elapsedRealtime() }
    var seekAt by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                Log.d(TIMING_TAG, "first frame ${SystemClock.elapsedRealtime() - openedAt} ms after the screen opened")
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playing = playWhenReady
                if (playWhenReady) viewModel.onPlaying(player.currentPosition) else viewModel.onPaused(player.currentPosition)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY && seekAt != 0L) {
                    Log.d(TIMING_TAG, "seek ready after ${SystemClock.elapsedRealtime() - seekAt} ms")
                    seekAt = 0L
                }
                if (player.duration != C.TIME_UNSET) durationMs = player.duration
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
            // Covers a screen that goes away without the close button, such as the activity being destroyed.
            viewModel.onClosed(player.currentPosition)
            player.removeListener(listener)
            player.release()
        }
    }

    // A new stream: the first one, a retry, or the transcode fallback that carries on from where it was.
    val stream = state.stream
    LaunchedEffect(stream?.id) {
        if (stream == null) {
            player.stop()
        } else {
            Log.d(TIMING_TAG, "stream ready ${SystemClock.elapsedRealtime() - openedAt} ms after the screen opened (negotiation done)")
            open(player, http, stream)
            viewModel.onPlaying(stream.startPositionMs)
        }
    }

    // Audio and subtitles: show what the state says is selected, now and whenever either changes. The
    // player's own tracks only exist once it has read the file, so it also runs when those change.
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
    var showTracks by remember { mutableStateOf(false) }
    // A new stream (the next episode) takes the track sheet with it: it listed the old one's tracks.
    LaunchedEffect(stream?.id) { showTracks = false }

    LaunchedEffect(player) {
        while (isActive) {
            positionMs = player.currentPosition
            if (player.duration != C.TIME_UNSET) durationMs = player.duration
            if (player.isPlaying || player.playbackState == Player.STATE_READY) viewModel.onPosition(positionMs)
            delay(POSITION_TICK_MS)
        }
    }

    // Leaving the app pauses the video and tells the server it stopped; the user plays on when back.
    // ON_PAUSE, not ON_STOP: the stop can arrive seconds late, and by then the video surface is gone
    // and a player still decoding into it fails.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        player.pause()
        viewModel.onBackgrounded(player.currentPosition)
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    val screenReaderOn = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }
    LaunchedEffect(controlsVisible, interactions, playing, scrubbing, showTracks) {
        if (controlsVisible && playing && !scrubbing && !showTracks && !screenReaderOn) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Inflated, because the surface type can only be set from XML. See the layout for why a TextureView.
        AndroidView(
            factory = { viewContext ->
                (LayoutInflater.from(viewContext).inflate(R.layout.player_view_texture, null) as PlayerView).also {
                    it.player = player
                    it.setKeepContentOnPlayerReset(true)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // A tap anywhere on the video shows or hides the controls.
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures {
                    controlsVisible = !controlsVisible
                    interactions++
                }
            },
        )
        if (!leaving) {
            AnimatedVisibility(visible = controlsVisible || state.message != null, enter = fadeIn(), exit = fadeOut()) {
                PlayerControls(
                    title = state.title,
                    subtitle = state.subtitle,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    playing = playing,
                    hasTrackChoices = state.hasTrackChoices && state.message == null,
                    onClose = close,
                    onTracks = { showTracks = true },
                    onTogglePlay = {
                        player.playWhenReady = !player.playWhenReady
                        interactions++
                    },
                    onScrubbing = {
                        scrubbing = it
                        interactions++
                    },
                    onSeek = {
                        seekAt = SystemClock.elapsedRealtime()
                        // The scrubber shows this until the next tick, so it doesn't fall back to the old position.
                        positionMs = it
                        player.seekTo(it)
                    },
                )
            }
        }
        if (!leaving && (state.loading || (buffering && state.stream != null))) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showTracks && !leaving) {
            TrackSheet(
                audio = state.audioTracks,
                subtitles = state.subtitleTracks,
                onSelectAudio = {
                    viewModel.onSelectAudio(it)
                    showTracks = false
                },
                onSelectSubtitle = {
                    viewModel.onSelectSubtitle(it)
                    showTracks = false
                },
                onDismiss = { showTracks = false },
            )
        }
        // The next episode, in the last seconds of this one. It grows in and fades like a screen opening, and goes the
        // same way; it keeps the last card while it fades out, because the state is already empty by then. Part of the
        // same Box as the video so it travels with it when the screen leaves, and gated on `leaving` like the controls.
        var lastUpNext by remember { mutableStateOf<UpNext?>(null) }
        state.upNext?.let { lastUpNext = it }
        AnimatedVisibility(
            visible = state.upNext != null && !leaving && state.message == null,
            enter = scaleIn(tween(UP_NEXT_MS), initialScale = UP_NEXT_START_SCALE) + fadeIn(tween(UP_NEXT_MS)),
            exit = scaleOut(tween(UP_NEXT_EXIT_MS), targetScale = UP_NEXT_START_SCALE) + fadeOut(tween(UP_NEXT_EXIT_MS)),
            modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = screenMargin(), bottom = 72.dp),
        ) {
            lastUpNext?.let { upNext ->
                UpNextCard(upNext = upNext, onPlayNow = viewModel::onPlayNow, onWatchCredits = viewModel::onWatchCredits)
            }
        }
        state.message?.let { message ->
            Column(Modifier.align(Alignment.Center).safeDrawingPadding().padding(horizontal = screenMargin())) {
                LoadError(message = message, onRetry = viewModel::onRetry)
            }
        }
    }
}

private const val CONNECT_TIMEOUT_MS = 15_000

/** The same grow-in as a screen opening (`VibeyFinNavHost`), a little quicker for a card. */
private const val UP_NEXT_MS = 260
private const val UP_NEXT_EXIT_MS = 180
private const val UP_NEXT_START_SCALE = 0.92f

/**
 * The phone's own decoders come first. The ffmpeg decoder (a Jellyfin build of Media3's extension) is
 * the fallback for audio they lack, such as DTS and TrueHD, so the server needn't convert it.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun renderersFactory(context: Context) =
    DefaultRenderersFactory(context).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

@androidx.annotation.OptIn(UnstableApi::class)
private fun buildPlayer(context: Context, http: DefaultHttpDataSource.Factory): ExoPlayer =
    ExoPlayer.Builder(context, renderersFactory(context))
        .setMediaSourceFactory(DefaultMediaSourceFactory(http))
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            // A seek lands on the nearest keyframe, instead of decoding from the one before the target.
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            // Subtitles are not offered yet, so no track is shown even if the file marks one as default.
            trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        }

@androidx.annotation.OptIn(UnstableApi::class)
private fun open(player: ExoPlayer, http: DefaultHttpDataSource.Factory, stream: PlaybackStream) {
    // The factory is read when the player opens the URL, so the headers go in before the item does.
    http.setDefaultRequestProperties(stream.headers)
    player.setMediaItem(stream.toMediaItem(), stream.startPositionMs)
    player.prepare()
    player.playWhenReady = true
}

/**
 * Full screen while the player is up: system bars hidden (a swipe from the edge brings them back for
 * a moment) and, on a phone, landscape. [restore] puts both back as they were, which also leaves the
 * user's rotation lock alone. It is called as the player starts to leave, so the screen underneath
 * is turning upright while the app's slide transition shows it, and again (harmlessly) on the way out.
 */
private class PlayerWindow(private val activity: Activity) {
    private val previousOrientation = activity.requestedOrientation
    private val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    private var applied = false

    private val previousRotationAnimation = activity.window.attributes.rotationAnimation

    fun apply() {
        if (applied) return
        applied = true
        // Turning the phone as the player opens or closes cross-fades instead of spinning a snapshot.
        setRotationAnimation(WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE)
        if (activity.resources.configuration.smallestScreenWidthDp < TABLET_WIDTH_DP) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    /**
     * Puts the bars and the orientation back. The orientation is a call into the system that can take
     * hundreds of milliseconds and makes everything lay out again, so it is made off the main thread,
     * and it is done once the screen is gone rather than while it slides away.
     */
    fun restore() {
        if (!applied) return
        applied = false
        controller.show(WindowInsetsCompat.Type.systemBars())
        thread(name = "restore-orientation") { activity.requestedOrientation = previousOrientation }
    }

    /** Back to the window's own rotation animation, once the screen has turned back. */
    fun release() {
        setRotationAnimation(previousRotationAnimation)
    }

    private fun setRotationAnimation(animation: Int) {
        activity.window.attributes = activity.window.attributes.apply { rotationAnimation = animation }
    }
}

@Composable
private fun rememberPlayerWindow(): PlayerWindow? {
    val context = LocalContext.current
    val window = remember(context) { context.findActivity()?.let(::PlayerWindow) }
    DisposableEffect(window) {
        window?.apply()
        onDispose {
            window?.restore()
            window?.release()
        }
    }
    return window
}

/** Tablets keep the orientation they are held in. */
private const val TABLET_WIDTH_DP = 600

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
