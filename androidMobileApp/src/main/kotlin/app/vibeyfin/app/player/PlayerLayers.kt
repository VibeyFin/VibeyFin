package app.vibeyfin.app.player

import android.view.LayoutInflater
import android.view.View
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import app.vibeyfin.R

/** Which side of the picture a double-tap skips. */
internal enum class SkipSide { Back, Forward }

/**
 * The picture. Inflated, because the surface type can only be set from XML. SDR video is drawn on a TextureView, which
 * slides and fades with the screen (player_view_texture.xml); HDR and Dolby Vision need a SurfaceView to reach the
 * display (player_view_surface.xml), which does not, so it is [hidden] as the screen is left. Kept across streams of the
 * same kind, so the next episode doesn't flash. Nothing is drawn until [hdrVideo] is known.
 */
@Composable
internal fun PlayerVideoSurface(hdrVideo: Boolean?, player: Player, hidden: Boolean, subtitleStyle: SubtitleViewStyle) {
    val hdr = hdrVideo ?: return

    key(hdr) {
        AndroidView(
            factory = { viewContext ->
                val layout = if (hdr) R.layout.player_view_surface else R.layout.player_view_texture
                (LayoutInflater.from(viewContext).inflate(layout, null) as PlayerView).also {
                    it.player = player
                    it.setKeepContentOnPlayerReset(true)
                }
            },
            update = {
                it.visibility = if (hidden && hdr) View.INVISIBLE else View.VISIBLE
                it.subtitleView?.apply(subtitleStyle)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Covers the picture and catches its taps: one tap asks for the controls to show or hide, a double tap on the left
 * third skips back and on the right third forward, and the middle does nothing. A vertical swipe changes the volume or the
 * brightness, through [swipe].
 */
@Composable
internal fun PlayerTapLayer(onTap: () -> Unit, onDoubleTap: (SkipSide) -> Unit, swipe: Modifier = Modifier) {
    // The gesture handler is set up once, so it reads the callbacks through these
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)

    Box(
        Modifier.fillMaxSize().then(swipe).pointerInput(Unit) {
            detectTapGestures(
                onTap = { currentOnTap() },
                onDoubleTap = { at ->
                    val side = when {
                        at.x < size.width / 3f -> SkipSide.Back
                        at.x > size.width * 2f / 3f -> SkipSide.Forward
                        else -> null
                    }
                    if (side != null) currentOnDoubleTap(side)
                },
            )
        },
    )
}
