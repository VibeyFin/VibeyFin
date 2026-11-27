package app.vibeyfin.app.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.PlaybackDiagnostics
import app.vibeyfin.domain.TrickplayPreviewSize
import app.vibeyfin.ui.common.Strings

/**
 * What the stream is and how it is arriving, in a compact panel on the scrim so it reads over any
 * video. It only draws: it takes no touches, so taps still reach the player underneath.
 */
@Composable
fun StatsOverlay(diagnostics: PlaybackDiagnostics, modifier: Modifier = Modifier) {
    val none = Strings.statsUnavailable
    val rows = buildList {
        add(Strings.statsLabelMethod to (diagnostics.method?.let(Strings::statsMethod) ?: none))

        if (diagnostics.transcodeReasons.isNotEmpty()) {
            add(Strings.statsLabelReasons to diagnostics.transcodeReasons.joinToString("\n"))
        }

        add(Strings.statsLabelVideo to (diagnostics.videoCodec ?: none))
        add(Strings.statsLabelAudio to (diagnostics.audioCodec ?: none))
        add(
            Strings.statsLabelResolution to
                if (diagnostics.width != null && diagnostics.height != null) {
                    Strings.statsResolution(diagnostics.width!!, diagnostics.height!!)
                } else {
                    none
                },
        )
        add(Strings.statsLabelFrameRate to (diagnostics.frameRate?.let(Strings::statsFrameRate) ?: none))
        add(Strings.statsLabelBitrate to (diagnostics.bitrateKbps?.let(Strings::statsBitrate) ?: none))
        add(Strings.statsLabelDroppedFrames to (diagnostics.droppedFrames?.toString() ?: none))
        add(Strings.statsLabelThroughput to (diagnostics.throughputKbps?.let(Strings::statsBitrate) ?: none))
        add(Strings.statsLabelBuffer to (diagnostics.bufferMs?.let(Strings::statsBuffer) ?: none))
    }

    Column(
        modifier = modifier
            .widthIn(max = 260.dp)
            .background(VibeyFinTokens.Colors.scrim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEach { (label, value) ->
            // One spoken phrase per row, "Bitrate, 4.5 Mbps", instead of two separate texts
            Row(
                Modifier.clearAndSetSemantics { contentDescription = Strings.labelled(label, value) },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = VibeyFinTokens.Colors.inkMuted,
                    modifier = Modifier.widthIn(min = 88.dp),
                )
                Text(text = value, style = MaterialTheme.typography.labelSmall, color = VibeyFinTokens.Colors.ink)
            }
        }
    }
}

/**
 * "Skip intro", "Skip credits". Shown for the whole segment whether or not the controls are up.
 * The label comes from the shared state.
 */
@Composable
fun SkipButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(VibeyFinTokens.Size.touch),
        colors = ButtonDefaults.filledTonalButtonColors(contentColor = VibeyFinTokens.Colors.ink),
    ) {
        Text(label)
    }
}

/**
 * "Are you still watching?" over the paused video, after several episodes in a row that started by
 * themselves. It covers the picture and takes every touch, so nothing underneath is pressed by accident.
 */
@Composable
fun StillWatchingPrompt(onContinue: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VibeyFinTokens.Colors.scrim)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4)) {
            Text(
                text = Strings.stillWatchingPrompt,
                style = MaterialTheme.typography.headlineSmall,
                color = VibeyFinTokens.Colors.ink,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
                FilledTonalButton(onClick = onStop, modifier = Modifier.height(VibeyFinTokens.Size.touch)) {
                    Text(Strings.stillWatchingStop)
                }

                Button(onClick = onContinue, modifier = Modifier.height(VibeyFinTokens.Size.touch)) {
                    Text(Strings.stillWatchingContinue)
                }
            }
        }
    }
}

/**
 * The thumbnail above the scrubber's thumb with the time under it, as the design draws it: no box,
 * the picture at [size] with the pop shadow and a hairline. [fraction] is where the thumb is along the
 * bar; the preview is centered on it but kept inside the bar's width, so it never leaves the screen at
 * either end. It takes no touches.
 */
@Composable
fun ScrubPreview(thumbnail: ImageBitmap, time: String, fraction: Float, size: TrickplayPreviewSize, modifier: Modifier = Modifier) {
    val pictureShape: RoundedCornerShape = RoundedCornerShape(VibeyFinTokens.Radius.artwork)

    Column(
        modifier = modifier.layout { measurable, constraints ->
            // The slider's height must not clamp the picture
            val placeable: Placeable = measurable.measure(Constraints())
            // Sits on top of the bar without taking room in it
            layout(0, 0) {
                val centred: Int = (constraints.maxWidth * fraction - placeable.width / 2f).toInt()
                placeable.place(centred.coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0)), -placeable.height)
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size.width.dp, size.height.dp)
                .shadow(PreviewShadowElevation, pictureShape, ambientColor = VibeyFinTokens.Colors.scrimStrong, spotColor = VibeyFinTokens.Colors.scrimStrong)
                .clip(pictureShape)
                .border(1.dp, VibeyFinTokens.Colors.overlayWeak, pictureShape),
        )
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFeatureSettings = "tnum",
                shadow = Shadow(VibeyFinTokens.Colors.scrimStrong, blurRadius = TIME_SHADOW_BLUR),
            ),
            color = VibeyFinTokens.Colors.ink,
            modifier = Modifier.padding(top = VibeyFinTokens.Space.s1),
        )
    }
}

private val PreviewShadowElevation = 12.dp
private const val TIME_SHADOW_BLUR: Float = 2f
