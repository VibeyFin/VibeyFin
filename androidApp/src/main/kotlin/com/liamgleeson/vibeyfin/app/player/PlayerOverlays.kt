package com.liamgleeson.vibeyfin.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.liamgleeson.vibeyfin.app.theme.VibeyFinTokens
import com.liamgleeson.vibeyfin.domain.PlaybackDiagnostics
import com.liamgleeson.vibeyfin.ui.common.Strings

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
            // One spoken phrase per row, "Bitrate, 4.5 Mbps", instead of two separate texts.
            Row(
                Modifier.clearAndSetSemantics { contentDescription = "$label, $value" },
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
