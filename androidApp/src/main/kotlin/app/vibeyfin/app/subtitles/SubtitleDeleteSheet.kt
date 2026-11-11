package app.vibeyfin.app.subtitles

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.vibeyfin.app.components.VibeyFinSheet
import app.vibeyfin.app.theme.MenuPressIndication
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.item.DeletableSubtitle

/** The title's external subtitle files; choosing one asks before it is deleted. */
@Composable
fun SubtitleDeleteSheet(tracks: List<DeletableSubtitle>, onChoose: (Int) -> Unit, onDismiss: () -> Unit) {
    VibeyFinSheet(onDismiss) {
        CompositionLocalProvider(LocalIndication provides MenuPressIndication) {
            Column(Modifier.padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s2), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1)) {
                Text(Strings.deleteSubtitles, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(Strings.deleteSubtitlesHelp, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Column(Modifier.navigationBarsPadding()) {
                tracks.forEach { track ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = VibeyFinTokens.Size.touch)
                            .clickable(role = Role.Button) { onChoose(track.streamIndex) }
                            .padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s3),
                    ) {
                        Text(track.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = VibeyFinTokens.Colors.danger)
                    }
                }
            }
        }
    }
}
