package app.vibeyfin.app.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vibeyfin.R
import app.vibeyfin.app.components.BackButton
import app.vibeyfin.app.components.ChipButton
import app.vibeyfin.app.components.ChipInset
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.launch

/**
 * The overlay above the video: back and title at the top, play or pause in the middle, the scrubber
 * and the times at the bottom. Everything sits on the scrim, because the video behind can be any
 * colour. It draws what it is given and reports what the user does; the player is not known here.
 */
@Composable
fun PlayerControls(
    title: String,
    subtitle: String?,
    positionMs: Long,
    /** How far the player has loaded ahead; drawn as a lighter segment on the scrubber. */
    bufferedMs: Long,
    durationMs: Long,
    playing: Boolean,
    /** Whether there are audio or subtitle tracks to choose from, which adds the button for them. */
    hasTrackChoices: Boolean,
    /** The picture's one badge ("4K", "HDR10"); null shows none. */
    badge: String?,
    /** Whether the back, play and forward buttons show: only when there is a stream to control and nothing else is in the middle. */
    showTransport: Boolean,
    /** The chosen playback speed as a label ("1.5x"), which is also the speed button. */
    speedLabel: String,
    onClose: () -> Unit,
    onTracks: () -> Unit,
    onSpeed: () -> Unit,
    /** Turns the video into a picture-in-picture window; null when the device can't. */
    onPip: (() -> Unit)?,
    onTogglePlay: () -> Unit,
    /** The profile's skip lengths, which pick the numbers on the two skip buttons. */
    skipBackSeconds: Int,
    skipForwardSeconds: Int,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    /** The user is dragging the scrubber, or has let go (false); the controls must not hide meanwhile. */
    onScrubbing: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    /** The thumbnails to preview while scrubbing; null shows the timestamp alone. */
    trickplay: TrickplayTiles? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        TopBar(title, subtitle, hasTrackChoices, badge, speedLabel, onClose, onTracks, onSpeed, onPip, Modifier.align(Alignment.TopStart)
        )
        if (showTransport) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s6)) {
                TransportSkipButton(R.drawable.ic_replay, skipBackSeconds, Strings.skipBackLabel(skipBackSeconds), forward = false, onSkipBack)
                PlayPauseButton(playing, onTogglePlay)
                TransportSkipButton(R.drawable.ic_forward, skipForwardSeconds, Strings.skipForwardLabel(skipForwardSeconds), forward = true, onSkipForward)
            }
        }
        Scrubber(
            positionMs,
            bufferedMs,
            durationMs,
            trickplay,
            onScrubbing,
            onSeek,
            Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** Solid scrim behind the content, fading out at the edge nearest the video, so text over bright video keeps its contrast. */
private fun scrimBand(atTop: Boolean): Brush {
    val scrim = VibeyFinTokens.Colors.scrim
    return if (atTop) {
        Brush.verticalGradient(0f to scrim, 0.7f to scrim, 1f to Color.Transparent)
    } else {
        Brush.verticalGradient(0f to Color.Transparent, 0.3f to scrim, 1f to scrim)
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String?,
    hasTrackChoices: Boolean,
    badge: String?,
    speedLabel: String,
    onClose: () -> Unit,
    onTracks: () -> Unit,
    onSpeed: () -> Unit,
    onPip: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(scrimBand(atTop = true))
            .safeDrawingPadding()
            .padding(
                start = screenMargin() - ChipInset,
                end = screenMargin(),
                top = VibeyFinTokens.Space.s2,
                bottom = VibeyFinTokens.Space.s5
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onClick = onClose)
        Spacer(Modifier.width(VibeyFinTokens.Space.s3))
        Column(Modifier.weight(1f)) {
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = VibeyFinTokens.Colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // One meta line: the subtitle, then the format badge as plain text.
            Strings.metaLine(subtitle, badge)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeyFinTokens.Colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(VibeyFinTokens.Space.s3))
        TextChipButton(speedLabel, Strings.speed, onSpeed)
        if (onPip != null) {
            Spacer(Modifier.width(VibeyFinTokens.Space.s3))
            ChipButton(R.drawable.ic_pip, Strings.pictureInPicture, onPip)
        }
        if (hasTrackChoices) {
            Spacer(Modifier.width(VibeyFinTokens.Space.s3))
            ChipButton(
                painterResource(R.drawable.ic_subtitles),
                Strings.audioAndSubtitles,
                onTracks
            )
        }
    }
}

/** [ChipButton] with a word in place of a glyph: 48 dp to touch, 40 dp to look at, wider for longer text. */
@Composable
private fun TextChipButton(text: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(40.dp)
                .widthIn(min = 40.dp)
                .clip(CircleShape)
                .background(VibeyFinTokens.Colors.scrim)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = VibeyFinTokens.Colors.ink,
                modifier = Modifier.semantics { contentDescription = Strings.labelled(description, text) },
            )
        }
    }
}

/** The back button's twin: a glyph on a round chip over the scrim, 48 dp to touch and 40 dp to look at. */
@Composable
private fun ChipButton(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(VibeyFinTokens.Colors.scrim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = description,
                tint = VibeyFinTokens.Colors.ink,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * A skip button: the arrow circle in `ink` on the scrim with the seconds over it, 48 dp to touch. The
 * number is text rather than one drawable per value, so every setting (15, 45, 60 too) has a glyph.
 */
@Composable
private fun TransportSkipButton(icon: Int, seconds: Int, description: String, forward: Boolean, onClick: () -> Unit) {
    // Each press turns the arrow a little the way it points and springs it back, as Android's own players do.
    val turn = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button) {
                onClick()
                scope.launch {
                    turn.snapTo(if (forward) SKIP_TURN_DEGREES else -SKIP_TURN_DEGREES)
                    turn.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = VibeyFinTokens.Colors.ink,
            modifier = Modifier.size(40.dp).rotate(turn.value),
        )
        Text(
            text = seconds.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = VibeyFinTokens.Colors.ink,
            modifier = Modifier.padding(top = 3.dp).clearAndSetSemantics { },
        )
    }
}

/** How far a skip arrow turns on a press. */
private const val SKIP_TURN_DEGREES = 30f

/** A round chip on the scrim, like the back button: 72 dp to touch, a 36 dp glyph. */
@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(VibeyFinTokens.Colors.scrim)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = if (playing) Strings.pause else Strings.play,
            tint = VibeyFinTokens.Colors.ink,
            modifier = Modifier.size(36.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Scrubber(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    trickplay: TrickplayTiles?,
    onScrubbing: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // While the thumb is held, show where it is, not where the video is.
    var dragged by remember { mutableStateOf<Float?>(null) }
    val known = durationMs > 0
    val shown =
        (dragged ?: positionMs.toFloat()).coerceIn(0f, if (known) durationMs.toFloat() else 1f)
    val shownMs = shown.toLong()
    // The thumbnail for where the thumb is; null until its tile arrives, and when there is none.
    var thumbnail by remember(trickplay) { mutableStateOf<ImageBitmap?>(null) }
    var thumbnailTile by remember(trickplay) { mutableStateOf<Int?>(null) }
    LaunchedEffect(trickplay, dragged != null, shownMs / 1000) {
        // A frame from another tile must never sit under a new timestamp while that tile loads.
        val tile = trickplay?.tileAt(shownMs)
        if (tile != thumbnailTile) thumbnail = null
        thumbnailTile = tile
        thumbnail = if (trickplay != null && dragged != null) trickplay.thumbnail(shownMs) else null
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scrimBand(atTop = false))
            .safeDrawingPadding()
            .padding(horizontal = screenMargin(), vertical = VibeyFinTokens.Space.s2)
            .padding(top = VibeyFinTokens.Space.s4),
    ) {
        Box {
            Slider(
                value = if (known) shown else 0f,
                onValueChange = {
                    if (dragged == null) onScrubbing(true)
                    dragged = it
                },
                onValueChangeFinished = {
                    dragged?.let { onSeek(it.toLong()) }
                    dragged = null
                    onScrubbing(false)
                },
                valueRange = 0f..(if (known) durationMs.toFloat() else 1f),
                enabled = known,
                track = { state ->
                    BufferedTrack(
                        played = fraction(state.value, state.valueRange),
                        buffered = if (known) maxOf(bufferedMs, shownMs).toFloat() / durationMs else 0f,
                        enabled = known,
                    )
                },
                colors = SliderDefaults.colors(
                    thumbColor = VibeyFinTokens.Colors.accent,
                    activeTrackColor = VibeyFinTokens.Colors.accent,
                    inactiveTrackColor = VibeyFinTokens.Colors.progressTrack,
                    disabledThumbColor = VibeyFinTokens.Colors.progressTrack,
                    disabledActiveTrackColor = VibeyFinTokens.Colors.progressTrack,
                    disabledInactiveTrackColor = VibeyFinTokens.Colors.progressTrack,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = Strings.playbackPosition
                        stateDescription = Strings.positionDescription(shownMs, durationMs)
                    },
            )
            val preview = thumbnail
            if (dragged != null && preview != null && known) {
                ScrubPreview(
                    preview,
                    Strings.clock(shownMs),
                    shown / durationMs,
                    Modifier.align(Alignment.TopStart)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Strings.clock(shownMs), style = TimeStyle, color = VibeyFinTokens.Colors.ink)
            if (known) Text(
                Strings.timeLeft(shownMs, durationMs),
                style = TimeStyle,
                color = VibeyFinTokens.Colors.ink
            )
        }
    }
}

/** Tabular numerals, so the times don't jitter as the digits change. */
private val TimeStyle
    @Composable get() = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")

/**
 * The scrubber's bar: the track, then a lighter segment up to what is buffered, then the played part in aqua up to the
 * thumb. The buffered segment never ends before the thumb, so it is never seen behind the played part.
 */
@Composable
private fun BufferedTrack(played: Float, buffered: Float, enabled: Boolean) {
    val track = VibeyFinTokens.Colors.progressTrack
    val bufferedColor = if (enabled) Color.White.copy(alpha = BUFFERED_ALPHA) else track
    val playedColor = if (enabled) VibeyFinTokens.Colors.accent else track
    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
        val corner = CornerRadius(size.height / 2)
        fun bar(fraction: Float, color: Color) {
            val width = size.width * fraction.coerceIn(0f, 1f)
            if (width > 0f) drawRoundRect(color, size = Size(width, size.height), cornerRadius = corner)
        }
        bar(1f, track)
        bar(buffered.coerceAtLeast(played), bufferedColor)
        bar(played, playedColor)
    }
}

private fun fraction(value: Float, range: ClosedFloatingPointRange<Float>): Float =
    if (range.endInclusive > range.start) (value - range.start) / (range.endInclusive - range.start) else 0f

private const val BUFFERED_ALPHA = 0.35f
