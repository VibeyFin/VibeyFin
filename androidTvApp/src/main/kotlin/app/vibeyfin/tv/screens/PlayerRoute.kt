package app.vibeyfin.tv.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.playback.PlaybackFailure
import app.vibeyfin.ui.playback.PlaybackViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Spike: Media3's PlayerView with its built-in remote controls; no tracks, skip or up-next yet. */
@OptIn(UnstableApi::class)
@Composable
fun PlayerRoute(
    itemId: String,
    onBack: () -> Unit,
    onExit: (HomeExit) -> Unit,
    viewModel: PlaybackViewModel = koinViewModel(parameters = { parametersOf(itemId, false) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val http = remember { DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true) }
    val player = remember {
        ExoPlayer.Builder(context, DefaultRenderersFactory(context).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON))
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .build()
    }

    LaunchedEffect(state.exit) { state.exit?.let(onExit) }
    LaunchedEffect(state.finished) { if (state.finished) onBack() }
    BackHandler {
        viewModel.onClosed(player.currentPosition)
        player.stop()
        onBack()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) viewModel.onPlaying(player.currentPosition) else viewModel.onPaused(player.currentPosition)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) viewModel.onEnded(player.duration.coerceAtLeast(0))
            }

            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) viewModel.onSeeked(new.positionMs)
            }

            override fun onPlayerError(error: PlaybackException) = viewModel.onFailed(error.toFailure())
        }
        player.addListener(listener)
        onDispose {
            viewModel.onClosed(player.currentPosition)
            player.removeListener(listener)
            player.release()
        }
    }

    val stream = state.stream
    LaunchedEffect(stream?.id) {
        if (stream == null) return@LaunchedEffect
        http.setDefaultRequestProperties(stream.headers)
        player.setMediaItem(
            MediaItem.Builder().setUri(stream.url).setMediaMetadata(MediaMetadata.Builder().setTitle(state.title).setSubtitle(state.subtitle).build()).build(),
            stream.startPositionMs,
        )
        player.prepare()
        player.playWhenReady = true
        viewModel.onPlaying(stream.startPositionMs)
    }
    LaunchedEffect(player) {
        while (true) {
            if (player.isPlaying) viewModel.onPosition(player.currentPosition)
            delay(1_000)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 4_000
                    // PlayerView needs focus for the remote's keys to reach its controller.
                    isFocusable = true
                    requestFocus()
                }
            },
        )
        state.message?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@OptIn(UnstableApi::class)
private fun PlaybackException.toFailure(): PlaybackFailure = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_TIMEOUT,
    -> PlaybackFailure.Network
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> when (httpStatus()) {
        401 -> PlaybackFailure.Unauthorized
        in 500..599 -> PlaybackFailure.Network
        else -> PlaybackFailure.Unsupported
    }
    else -> PlaybackFailure.Unsupported
}

@OptIn(UnstableApi::class)
private fun PlaybackException.httpStatus(): Int? =
    generateSequence(cause) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
