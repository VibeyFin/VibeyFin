package com.liamgleeson.vibeyfin.app.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.liamgleeson.vibeyfin.app.theme.VibeyFinTokens
import com.liamgleeson.vibeyfin.ui.common.Strings
import com.liamgleeson.vibeyfin.ui.playback.TrackOption

/**
 * Audio and subtitles in a bottom sheet, as the design asks for the Android player. The two sections
 * sit side by side, each with its own scroll, so a long list in one never pushes the other out of
 * reach. A section lists its options with a check on the one in use; one with nothing to choose is
 * left out and the other takes the whole width. The check is `ink`, not aqua, which is kept for the
 * one primary action and playback progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(
    audio: List<TrackOption>,
    subtitles: List<TrackOption>,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Opens at full height, so the lists have room to be read in landscape.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = VibeyFinTokens.Space.s2, vertical = VibeyFinTokens.Space.s2),
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
        ) {
            if (audio.isNotEmpty()) {
                Section(Strings.audio, audio, onSelectAudio, Modifier.weight(1f))
            }
            if (subtitles.isNotEmpty()) {
                Section(Strings.subtitles, subtitles, onSelectSubtitle, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Section(title: String, options: List<TrackOption>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s2),
        )
        options.forEach { option ->
            ListItem(
                headlineContent = {
                    // Half the width each, so a long server name may need a third line.
                    Text(option.label, maxLines = 3, overflow = TextOverflow.Ellipsis)
                },
                trailingContent = {
                    // Only the chosen row has a mark; the others stay blank, so the state is not carried by colour.
                    if (option.selected) Icon(Icons.Filled.Check, contentDescription = null, tint = VibeyFinTokens.Colors.ink)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.selectable(selected = option.selected, role = Role.RadioButton, onClick = { onSelect(option.index) }),
            )
        }
    }
}
