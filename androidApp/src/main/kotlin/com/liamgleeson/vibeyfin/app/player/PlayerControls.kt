package com.liamgleeson.vibeyfin.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liamgleeson.vibeyfin.R
import com.liamgleeson.vibeyfin.app.components.BackButton
import com.liamgleeson.vibeyfin.app.components.ChipButton
import com.liamgleeson.vibeyfin.app.components.ChipInset
import com.liamgleeson.vibeyfin.app.theme.VibeyFinTokens
import com.liamgleeson.vibeyfin.app.theme.screenMargin
import com.liamgleeson.vibeyfin.ui.common.Strings

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
    durationMs: Long,
    playing: Boolean,
    /** Whether there are audio or subtitle tracks to choose from, which adds the button for them. */
    hasTrackChoices: Boolean,
    onClose: () -> Unit,
    onTracks: () -> Unit,
    onTogglePlay: () -> Unit,
    /** The user is dragging the scrubber, or has let go (false); the controls must not hide meanwhile. */
    onScrubbing: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        TopBar(title, subtitle, hasTrackChoices, onClose, onTracks, Modifier.align(Alignment.TopStart))
        PlayPauseButton(playing, onTogglePlay, Modifier.align(Alignment.Center))
        Scrubber(positionMs, durationMs, onScrubbing, onSeek, Modifier.align(Alignment.BottomCenter))
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
    onClose: () -> Unit,
    onTracks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(scrimBand(atTop = true))
            .safeDrawingPadding()
            .padding(start = screenMargin() - ChipInset, end = screenMargin(), top = VibeyFinTokens.Space.s2, bottom = VibeyFinTokens.Space.s5),
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
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeyFinTokens.Colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hasTrackChoices) {
            Spacer(Modifier.width(VibeyFinTokens.Space.s3))
            ChipButton(painterResource(R.drawable.ic_subtitles), Strings.audioAndSubtitles, onTracks)
        }
    }
}

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

@Composable
private fun Scrubber(
    positionMs: Long,
    durationMs: Long,
    onScrubbing: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // While the thumb is held, show where it is, not where the video is.
    var dragged by remember { mutableStateOf<Float?>(null) }
    val known = durationMs > 0
    val shown = (dragged ?: positionMs.toFloat()).coerceIn(0f, if (known) durationMs.toFloat() else 1f)
    val shownMs = shown.toLong()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scrimBand(atTop = false))
            .safeDrawingPadding()
            .padding(horizontal = screenMargin(), vertical = VibeyFinTokens.Space.s2)
            .padding(top = VibeyFinTokens.Space.s4),
    ) {
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
            colors = SliderDefaults.colors(
                thumbColor = VibeyFinTokens.Colors.accent,
                activeTrackColor = VibeyFinTokens.Colors.accent,
                inactiveTrackColor = VibeyFinTokens.Colors.progressTrack,
                disabledThumbColor = VibeyFinTokens.Colors.progressTrack,
                disabledActiveTrackColor = VibeyFinTokens.Colors.progressTrack,
                disabledInactiveTrackColor = VibeyFinTokens.Colors.progressTrack,
            ),
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = Strings.playbackPosition
                stateDescription = Strings.positionDescription(shownMs, durationMs)
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Strings.clock(shownMs), style = TimeStyle, color = VibeyFinTokens.Colors.ink)
            if (known) Text(Strings.timeLeft(shownMs, durationMs), style = TimeStyle, color = VibeyFinTokens.Colors.ink)
        }
    }
}

/** Tabular numerals, so the times don't jitter as the digits change. */
private val TimeStyle
    @Composable get() = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
