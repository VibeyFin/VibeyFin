package app.vibeyfin.app.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.vibeyfin.ui.playback.PlaybackStream

/** How much must be buffered before playback starts, and before it carries on after running dry. */
internal const val PLAYBACK_START_BUFFER_MS = 2_500
internal const val PLAYBACK_RESTART_BUFFER_MS = 5_000

internal const val CONNECT_TIMEOUT_MS = 15_000

/**
 * The phone's own decoders come first. The ffmpeg decoder (a Jellyfin build of Media3's extension) is
 * the fallback for audio they lack, such as DTS and TrueHD, so the server needn't convert it.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun renderersFactory(context: Context) = DefaultRenderersFactory(context).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

@androidx.annotation.OptIn(UnstableApi::class)
internal fun buildPlayer(context: Context, http: DefaultHttpDataSource.Factory, bufferAheadSeconds: Int): ExoPlayer = ExoPlayer.Builder(context, renderersFactory(context))
    .apply {
        // 0 is Auto: ExoPlayer's own load control. Otherwise it keeps this many seconds ahead of the position.
        if (bufferAheadSeconds > 0) {
            val ms = bufferAheadSeconds * 1000

            setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(ms, ms, PLAYBACK_START_BUFFER_MS, PLAYBACK_RESTART_BUFFER_MS)
                    .build(),
            )
        }
    }
    // A file on the device (a download) opens through the same factory as a stream from the server: it picks the source by the address
    .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
    .setAudioAttributes(
        AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
        /* handleAudioFocus = */
        true,
    )
    .setHandleAudioBecomingNoisy(true)
    .build()
    .apply {
        // A seek lands on the nearest keyframe, instead of decoding from the one before the target
        setSeekParameters(SeekParameters.CLOSEST_SYNC)

        // Subtitles are not offered yet, so no track is shown even if the file marks one as default
        trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    }

@androidx.annotation.OptIn(UnstableApi::class)
internal fun open(player: ExoPlayer, http: DefaultHttpDataSource.Factory, stream: PlaybackStream, title: String, subtitle: String?, artworkUrl: String?) {
    // The factory is read when the player opens the URL, so the headers go in before the item does
    http.setDefaultRequestProperties(stream.headers)
    player.setMediaItem(stream.toMediaItem(title, subtitle, artworkUrl), stream.startPositionMs)
    player.prepare()

    player.playWhenReady = true
}

/** What the display says it can show, for the log: HDR types by name, or "none". */
@Suppress("DEPRECATION")
internal fun displayHdrTypes(context: Context): String {
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
