package app.vibeyfin.app.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.vibeyfin.R
import app.vibeyfin.design.VibeyFinTokens
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** What a vertical swipe on the player changes: the brightness on the left half, the volume on the right. */
enum class SwipeLevel { Brightness, Volume }

/**
 * The volume and the brightness a swipe on the player changes. The volume is the media stream's, set without the system's
 * panel so only [SwipeLevelIndicator] shows; the brightness is this window's only, never the system setting, and goes
 * back to the system's with [restoreBrightness].
 */
@Stable
class PlayerSwipe(private val activity: Activity?, private val audio: AudioManager?) {
    /** The level being changed, or the last one while the indicator fades out; null before the first swipe. */
    var kind by mutableStateOf<SwipeLevel?>(null)
        private set

    /**
     * The level shown, 0 to 1. It follows the finger smoothly for the volume too; the stream only has a few steps (often 15),
     * and the pill jumping between them felt rough, so the stream moves a step whenever the level crosses one.
     */
    var level by mutableFloatStateOf(0f)
        private set

    /** The volume is at its lowest step, so nothing is heard; the pill shows the muted glyph. */
    var silent by mutableStateOf(false)
        private set

    /** A finger is on the screen changing [kind]. */
    var dragging by mutableStateOf(false)
        private set

    // Read once a swipe starts, so a drag doesn't ask the audio service on every move.
    private var maxStep = 1
    private var step = 0

    fun start(kind: SwipeLevel) {
        this.kind = kind
        level = when (kind) {
            SwipeLevel.Volume -> audio?.let {
                maxStep = it.getStreamMaxVolume(STREAM).coerceAtLeast(1)
                step = it.getStreamVolume(STREAM)
                step / maxStep.toFloat()
            } ?: 0f

            SwipeLevel.Brightness -> currentBrightness()
        }
        silent = kind == SwipeLevel.Volume && step == 0
        dragging = true
    }

    /** Moves the level by [by], a share of the full range; up is positive. */
    fun drag(by: Float) {
        level = (level + by).coerceIn(0f, 1f)
        when (kind) {
            SwipeLevel.Volume -> setVolume(level)
            SwipeLevel.Brightness -> setBrightness(level)
            null -> Unit
        }
    }

    fun end() {
        dragging = false
    }

    /** Hands the brightness back to the system. */
    fun restoreBrightness() {
        val window = activity?.window ?: return
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
    }

    private fun setVolume(value: Float) {
        val audio = audio ?: return
        val next = (value * maxStep).roundToInt()
        silent = next == 0
        if (next == step) return
        step = next
        // No FLAG_SHOW_UI: the system's volume panel stays away while the indicator shows.
        audio.setStreamVolume(STREAM, next, 0)
    }

    private fun setBrightness(value: Float) {
        val window = activity?.window ?: return
        // Not quite zero, which some devices take as "screen off".
        window.attributes = window.attributes.apply { screenBrightness = value.coerceAtLeast(MIN_BRIGHTNESS) }
    }

    // This window's brightness if a swipe set it, otherwise the system's, which the first swipe starts from
    private fun currentBrightness(): Float {
        val activity = activity ?: return DEFAULT_BRIGHTNESS
        val own = activity.window.attributes.screenBrightness
        if (own >= 0f) return own
        val system = runCatching { Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull()
        return system?.let { (it / MAX_SYSTEM_BRIGHTNESS).coerceIn(0f, 1f) } ?: DEFAULT_BRIGHTNESS
    }

    private companion object {
        const val STREAM = AudioManager.STREAM_MUSIC
        const val MIN_BRIGHTNESS = 0.01f
        const val DEFAULT_BRIGHTNESS = 0.5f
        const val MAX_SYSTEM_BRIGHTNESS = 255f
    }
}

/** The swipe of this player; the brightness goes back to the system's when the player leaves. */
@Composable
fun rememberPlayerSwipe(): PlayerSwipe {
    val context = LocalContext.current
    val swipe = remember(context) {
        val audio = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.takeUnless { it.isVolumeFixed }
        PlayerSwipe(context.findActivity(), audio)
    }
    DisposableEffect(swipe) { onDispose { swipe.restoreBrightness() } }
    return swipe
}

/**
 * A vertical swipe changes the brightness on the left half and the volume on the right; up raises it. Swipes that start in
 * the system's gesture areas (screen edges, status bar) are left alone so they don't fight the back gesture or the shade.
 * Taps and double taps still reach the detector after this one. [onStart] runs when a swipe takes hold.
 */
@Composable
fun Modifier.swipeForLevels(swipe: PlayerSwipe, enabled: Boolean, onStart: () -> Unit): Modifier {
    if (!enabled) return this
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.systemGestures.union(WindowInsets.mandatorySystemGestures)
    val excluded by rememberUpdatedState(
        Edges(insets.getLeft(density, direction), insets.getTop(density), insets.getRight(density, direction), insets.getBottom(density)),
    )
    val started by rememberUpdatedState(onStart)
    return pointerInput(swipe) {
        var kind: SwipeLevel? = null
        detectVerticalDragGestures(
            onDragStart = { at ->
                kind = if (excluded.contains(at, size)) {
                    null
                } else if (at.x < size.width / 2f) {
                    SwipeLevel.Brightness
                } else {
                    SwipeLevel.Volume
                }
                kind?.let {
                    swipe.start(it)
                    started()
                }
            },
            onDragEnd = { swipe.end() },
            onDragCancel = { swipe.end() },
            onVerticalDrag = { change, dy ->
                if (kind != null) {
                    change.consume()
                    swipe.drag(-dy / (size.height * FULL_SWEEP))
                }
            },
        )
    }
}

/** The system's gesture insets, in pixels, on each side. */
private class Edges(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun contains(at: Offset, size: IntSize) = at.x < left || at.y < top || at.x > size.width - right || at.y > size.height - bottom
}

/**
 * The level a swipe set, on the side being swiped: a vertical pill on `scrim-strong` holding the track, filled from the bottom
 * in `ink` (never aqua), with the glyph at its foot. It shows at once and fades out [HIDE_AFTER_MS] after the finger lifts.
 * TalkBack hears the level as it changes. It takes no touches.
 */
@Composable
fun BoxScope.SwipeLevelIndicator(swipe: PlayerSwipe) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(swipe.dragging) {
        if (swipe.dragging) {
            visible = true
        } else {
            delay(HIDE_AFTER_MS)
            visible = false
        }
    }
    val kind = swipe.kind ?: return
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(0)),
        exit = fadeOut(tween(FADE_MS)),
        modifier = Modifier
            .align(if (kind == SwipeLevel.Volume) Alignment.CenterEnd else Alignment.CenterStart)
            .safeDrawingPadding()
            .padding(horizontal = VibeyFinTokens.Space.s6),
    ) {
        LevelPill(kind, swipe.level, swipe.silent)
    }
}

@Composable
private fun LevelPill(kind: SwipeLevel, level: Float, silent: Boolean) {
    val percent = (level * 100).roundToInt()
    val spoken = if (kind == SwipeLevel.Volume) Strings.volumeLevel(percent) else Strings.brightnessLevel(percent)
    Box(
        Modifier
            .size(PILL_WIDTH, PILL_HEIGHT)
            .clip(CircleShape)
            .background(VibeyFinTokens.Colors.scrimStrong)
            .background(VibeyFinTokens.Colors.progressTrack)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = spoken
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(level).background(VibeyFinTokens.Colors.ink))
        // Dark once the fill has reached the glyph, so it stays readable on either.
        Icon(
            painter = painterResource(levelIcon(kind, level, silent)),
            contentDescription = null,
            tint = if (level >= ICON_COVERED) VibeyFinTokens.Colors.bg else VibeyFinTokens.Colors.ink,
            modifier = Modifier.padding(bottom = VibeyFinTokens.Space.s3).size(ICON_SIZE),
        )
    }
}

@DrawableRes
private fun levelIcon(kind: SwipeLevel, level: Float, silent: Boolean): Int = when (kind) {
    SwipeLevel.Volume -> when {
        silent -> R.drawable.ic_volume_off
        level < 0.5f -> R.drawable.ic_volume_down
        else -> R.drawable.ic_volume_up
    }

    SwipeLevel.Brightness -> if (level < 0.5f) R.drawable.ic_brightness_low else R.drawable.ic_brightness_high
}

// The share of the player's height a swipe covers to go from nothing to full
private const val FULL_SWEEP = 0.6f
private const val HIDE_AFTER_MS = 1_000L
private const val FADE_MS = 300
private val PILL_WIDTH = 44.dp
private val PILL_HEIGHT = 152.dp
private val ICON_SIZE = 22.dp

// The fill's level at which it covers the glyph at the pill's foot
private const val ICON_COVERED = 0.16f
