package com.liamgleeson.vibeyfin.app.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.liamgleeson.vibeyfin.R
import com.liamgleeson.vibeyfin.app.components.LoadError
import com.liamgleeson.vibeyfin.app.theme.screenMargin
import com.liamgleeson.vibeyfin.domain.LiveDiagnostics
import com.liamgleeson.vibeyfin.domain.DynamicRange
import com.liamgleeson.vibeyfin.ui.common.Strings
import com.liamgleeson.vibeyfin.ui.home.HomeExit
import com.liamgleeson.vibeyfin.ui.playback.PlaybackStream
import com.liamgleeson.vibeyfin.ui.playback.PlaybackViewModel
import com.liamgleeson.vibeyfin.ui.playback.TrackOption
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

/** Keeps the stats panel below the controls' top bar, so it doesn't jump when the controls come and go. */
private val STATS_TOP_INSET = 72.dp

/** How far the skip button rises while the controls show, to clear the scrubber and the times. */
private val SKIP_LIFT_WITH_CONTROLS = 80.dp

private const val TIMING_TAG = "VibeyFinTiming"
private const val HDR_TAG = "VibeyFinHdr"

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

    val stillWatchingNow by rememberUpdatedState(state.stillWatching)
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
                // Play and pause from the picture-in-picture window, a headset or a media key count as the viewer being there;
                // the pause for the "still watching?" question is ours, not theirs.
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST && !stillWatchingNow) viewModel.onUserInteraction()
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
    // The scrub-preview tiles belong to the item: a new track (the next episode) starts with an empty cache,
    // and leaving the screen drops it.
    val trickplayTrack = state.trickplay
    val trickplayHeaders = stream?.headers
    val trickplay = remember(trickplayTrack, trickplayHeaders) {
        if (trickplayTrack != null && trickplayHeaders != null && trickplayTrack.tilesUrl.isNotEmpty()) {
            TrickplayTiles(context.applicationContext, trickplayTrack, trickplayHeaders)
        } else {
            null
        }
    }
    // Which kind of view the picture needs, known once the first stream arrives; it stays until a stream of the
    // other kind replaces it.
    var hdrVideo by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(stream?.id) {
        val range = stream?.dynamicRange ?: return@LaunchedEffect
        hdrVideo = range != DynamicRange.Sdr
        Log.d(HDR_TAG, "picture is $range: ${if (range != DynamicRange.Sdr) "SurfaceView layout" else "TextureView layout"}; ${displayHdrTypes(context)}")
    }
    LaunchedEffect(stream?.id) {
        if (stream == null) {
            player.stop()
        } else {
            Log.d(TIMING_TAG, "stream ready ${SystemClock.elapsedRealtime() - openedAt} ms after the screen opened (negotiation done)")
            open(player, http, stream, state.title, state.subtitle)
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
    var showSpeeds by remember { mutableStateOf(false) }
    // The chosen speed, now and for each stream the player opens (a retry, the next episode).
    LaunchedEffect(stream?.id, state.speed) { player.setPlaybackSpeed(state.speed) }
    // A new stream (the next episode) takes the track sheet with it: it listed the old one's tracks.
    LaunchedEffect(stream?.id) {
        showTracks = false
        showSpeeds = false
    }

    // Playback stats: what the player measures goes to the shared code, only while the overlay is on and
    // only when it changed. Dropped frames and bandwidth arrive as events; the rest is read off the player.
    val statsOn by rememberUpdatedState(state.showDiagnostics)
    val stats = remember(player) { StatsFeed(player, viewModel::onDiagnostics) { statsOn } }
    DisposableEffect(player) {
        player.addAnalyticsListener(stats)
        onDispose { player.removeAnalyticsListener(stats) }
    }
    LaunchedEffect(stream?.id) { stats.reset() }
    LaunchedEffect(statsOn) { if (statsOn) stats.publish() }

    // A skip: the shared code asks for a seek; it goes through the same path as the scrubber, so the
    // player's seek event reports it to the server.
    LaunchedEffect(state.pendingSeekMs) {
        state.pendingSeekMs?.let {
            seekAt = SystemClock.elapsedRealtime()
            positionMs = it
            player.seekTo(it)
            viewModel.onSeekHandled()
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            positionMs = player.currentPosition
            if (player.duration != C.TIME_UNSET) durationMs = player.duration
            if (player.isPlaying || player.playbackState == Player.STATE_READY) viewModel.onPosition(positionMs)
            stats.publish()
            delay(POSITION_TICK_MS)
        }
    }

    rememberPlayerSession(player)

    // "Are you still watching?" pauses the video; "Keep watching" plays on, "Stop" leaves.
    var pausedForPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(state.stillWatching) {
        if (state.stillWatching) {
            pausedForPrompt = player.playWhenReady
            player.pause()
        } else if (pausedForPrompt) {
            pausedForPrompt = false
            player.play()
        }
    }

    // Leaving the app turns the screen into a picture-in-picture window while something plays. The video
    // carries on and so does the session; closing the window ends it, expanding it brings the screen back.
    // A paused video (by the viewer, or for the still-watching question, which a window would hide) stops
    // as before instead.
    val videoRatio = state.diagnostics?.let { d -> if ((d.width ?: 0) > 0 && (d.height ?: 0) > 0) d.width!!.toFloat() / d.height!! else null } ?: (16f / 9f)
    val pip = rememberPlayerPip(
        enabled = state.stream != null && state.message == null && !leaving && playing && !state.stillWatching,
        aspectRatio = videoRatio,
        onEntered = { viewModel.onEnteredPictureInPicture(player.currentPosition) },
        onLeft = { closed ->
            viewModel.onLeftPictureInPicture(player.currentPosition, closed)
            if (closed) {
                // The session is over; the screen goes with the window.
                leaving = true
                player.stop()
            }
        },
    )
    // The video ended in the window: there is nothing left to show in it, so it goes away.
    LaunchedEffect(state.finished, pip.active) {
        if (state.finished && pip.active) context.findActivity()?.moveTaskToBack(true)
    }

    // Leaving the app pauses the video and tells the server it stopped; the user plays on when back.
    // ON_PAUSE, not ON_STOP: the stop can arrive seconds late, and by then the video surface is gone
    // and a player still decoding into it fails.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        // Not while it turns into a picture-in-picture window, which plays on.
        if (context.findActivity()?.isInPictureInPictureMode == true) return@LifecycleEventEffect
        player.pause()
        viewModel.onBackgrounded(player.currentPosition)
    }

    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(pip.active) { if (pip.active) controlsVisible = false }
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
        // Inflated, because the surface type can only be set from XML. SDR video is drawn on a TextureView, which
        // slides and fades with the screen (player_view_texture.xml); HDR and Dolby Vision need a SurfaceView to
        // reach the display (player_view_surface.xml), which does not, so it is hidden as the screen is left.
        // Kept across streams of the same kind, so the next episode doesn't flash.
        hdrVideo?.let { surface ->
            key(surface) {
                AndroidView(
                    factory = { viewContext ->
                        val layout = if (surface) R.layout.player_view_surface else R.layout.player_view_texture
                        (LayoutInflater.from(viewContext).inflate(layout, null) as PlayerView).also {
                            it.player = player
                            it.setKeepContentOnPlayerReset(true)
                        }
                    },
                    update = { it.visibility = if (leaving && surface) View.INVISIBLE else View.VISIBLE },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // A tap anywhere on the video shows or hides the controls.
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures {
                    viewModel.onUserInteraction()
                    controlsVisible = !controlsVisible
                    interactions++
                }
            },
        )
        if (!leaving && !pip.active) {
            AnimatedVisibility(visible = controlsVisible || state.message != null, enter = fadeIn(), exit = fadeOut()) {
                PlayerControls(
                    title = state.title,
                    subtitle = state.subtitle,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    playing = playing,
                    hasTrackChoices = state.hasTrackChoices && state.message == null,
                    badge = state.badge,
                    speedLabel = Strings.speedValue(state.speed),
                    trickplay = trickplay,
                    onClose = close,
                    onTracks = {
                        viewModel.onUserInteraction()
                        showTracks = true
                    },
                    onSpeed = {
                        viewModel.onUserInteraction()
                        showSpeeds = true
                    },
                    onPip = if (pip.available && state.stream != null) {
                        {
                            viewModel.onUserInteraction()
                            pip.enter()
                        }
                    } else {
                        null
                    },
                    onTogglePlay = {
                        viewModel.onUserInteraction()
                        player.playWhenReady = !player.playWhenReady
                        interactions++
                    },
                    onScrubbing = {
                        viewModel.onUserInteraction()
                        scrubbing = it
                        interactions++
                    },
                    onSeek = {
                        viewModel.onUserInteraction()
                        seekAt = SystemClock.elapsedRealtime()
                        // The scrubber shows this until the next tick, so it doesn't fall back to the old position.
                        positionMs = it
                        player.seekTo(it)
                    },
                )
            }
        }
        // Stays up when the controls hide, but not under an error, which fills the same part of the screen.
        // It takes no touches, so taps still reach the player.
        val diagnostics = state.diagnostics
        if (!leaving && !pip.active && state.showDiagnostics && diagnostics != null && state.message == null) {
            StatsOverlay(
                diagnostics,
                Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(start = screenMargin(), top = STATS_TOP_INSET),
            )
        }
        // Offered for the whole segment, controls up or not; above the scrubber while that shows.
        val skip = state.skipOffer
        if (!leaving && !pip.active && skip != null && state.message == null) {
            val lift by animateDpAsState(if (controlsVisible) SKIP_LIFT_WITH_CONTROLS else 0.dp, label = "skipLift")
            SkipButton(
                label = skip.label,
                onClick = viewModel::onSkip,
                modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = screenMargin(), bottom = 16.dp + lift),
            )
        }
        if (!leaving && (state.loading || (buffering && state.stream != null))) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.stillWatching && !leaving && !pip.active) {
            StillWatchingPrompt(onContinue = viewModel::onContinueWatching, onStop = viewModel::onStopWatching)
        }
        if (showSpeeds && !leaving && !pip.active) {
            SpeedSheet(
                speeds = state.speeds.mapIndexed { index, speed -> TrackOption(index, Strings.speedValue(speed), speed == state.speed) },
                onSelect = {
                    viewModel.onSelectSpeed(state.speeds[it])
                    showSpeeds = false
                },
                onDismiss = { showSpeeds = false },
            )
        }
        if (showTracks && !leaving && !pip.active) {
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
            visible = state.upNext != null && !leaving && !pip.active && state.message == null,
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

/** Collects the player's measurements and hands them on when they change. */
@androidx.annotation.OptIn(UnstableApi::class)
private class StatsFeed(
    private val player: ExoPlayer,
    private val onChanged: (LiveDiagnostics) -> Unit,
    private val enabled: () -> Boolean,
) : AnalyticsListener {
    private var droppedFrames = 0
    private var bandwidthBps = 0L
    private var last: LiveDiagnostics? = null

    /** A new stream starts its own count. */
    fun reset() {
        droppedFrames = 0
        publish()
    }

    fun publish() {
        if (!enabled()) return
        val size = player.videoSize
        val now = liveDiagnostics(
            video = player.videoFormat,
            audio = player.audioFormat,
            width = size.width,
            height = size.height,
            droppedFrames = droppedFrames,
            bandwidthBps = bandwidthBps,
            bufferMs = player.totalBufferedDuration,
        )
        if (now == last) return
        last = now
        onChanged(now)
    }

    override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) = publish()

    override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) = publish()

    override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) = publish()

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        this.droppedFrames += droppedFrames
        publish()
    }

    override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
        bandwidthBps = bitrateEstimate
        publish()
    }
}

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
private fun open(player: ExoPlayer, http: DefaultHttpDataSource.Factory, stream: PlaybackStream, title: String, subtitle: String?) {
    // The factory is read when the player opens the URL, so the headers go in before the item does.
    http.setDefaultRequestProperties(stream.headers)
    player.setMediaItem(stream.toMediaItem(title, subtitle), stream.startPositionMs)
    player.prepare()
    player.playWhenReady = true
}

/**
 * Full screen while the player is up: system bars hidden (a swipe from the edge brings them back for
 * a moment), the screen kept on (no touches arrive while watching, so it would dim and sleep otherwise;
 * Media3's PlayerView leaves that to the app) and, on a phone, landscape. [restore] puts all three back as
 * they were, which also leaves the user's rotation lock alone. It is called as the player starts to leave,
 * so the screen underneath is turning upright while the app's slide transition shows it, and again
 * (harmlessly) on the way out.
 */
private class PlayerWindow(private val activity: Activity) {
    private val previousOrientation = activity.requestedOrientation
    private val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    private var applied = false

    private val previousRotationAnimation = activity.window.attributes.rotationAnimation

    fun apply() {
        if (applied) return
        applied = true
        // For as long as the player is up, paused or not: the window flag only counts while the app is in front.
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** What the display says it can show, for the log: HDR types by name, or "none". */
@Suppress("DEPRECATION")
private fun displayHdrTypes(context: Context): String {
    val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return "display unknown"
    val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        display.mode.supportedHdrTypes
    } else {
        display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
    }
    val names = types.map {
        when (it) {
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
            Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
            Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
            Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
            else -> "type $it"
        }
    }
    return "display HDR: ${names.ifEmpty { listOf("none") }.joinToString()}"
}
