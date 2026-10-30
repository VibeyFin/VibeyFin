package app.vibeyfin.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.ui.banner.Banner

/**
 * The line at the top, its color running up behind the status bar, that says the app is offline, or that the server cannot be reached, with the button that ends it
 * (when one does). A quiet strip in the page's own surface colors: it is not an error and nothing here is aqua. Announced to a
 * screen reader when it appears.
 */
@Composable
fun AppBanner(banner: Banner, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(VibeyFinTokens.Colors.surface3)
            .statusBarsPadding()
            .padding(horizontal = screenMargin(), vertical = VibeyFinTokens.Space.s1)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
    ) {
        Column(Modifier.weight(1f)) {
            Text(banner.title, style = MaterialTheme.typography.labelLarge, color = VibeyFinTokens.Colors.ink)
            Text(banner.help, style = MaterialTheme.typography.bodySmall, color = VibeyFinTokens.Colors.inkMuted)
        }

        banner.actionLabel?.let { label ->
            TextButton(onClick = onAction) { Text(label, color = VibeyFinTokens.Colors.ink) }
        }
    }
}
