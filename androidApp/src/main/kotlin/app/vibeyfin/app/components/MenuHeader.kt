package app.vibeyfin.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.theme.VibeyFinTokens

/** The top of every long-press sheet: the title's picture, its name and a line about it, so the sheet says what it is for. */
@Composable
fun MenuHeader(title: String, subtitle: String?, imageUrl: String?, aspectRatio: Float = 2f / 3f, pictureWidth: Dp = 40.dp) {
    Row(
        Modifier.padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
    ) {
        Box(Modifier.width(pictureWidth).aspectRatio(aspectRatio).clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork))) {
            Artwork(url = imageUrl, title = title, aspectRatio = aspectRatio, modifier = Modifier.fillMaxWidth())
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
