package app.vibeyfin.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.vibeyfin.design.VibeyFinTokens

private val CHIP_SIZE = 40.dp
private val CHIP_ICON = 20.dp

/** How far the chip's edge sits inside its touch area; screens pull the chip out by this much to line it up with the margin. */
val ChipInset = (VibeyFinTokens.Size.touch - CHIP_SIZE) / 2

/**
 * A glyph on a round chip over the content, on the scrim so it reads over any artwork or video, with no
 * label; [description] is what a screen reader says. 48 dp to touch, 40 dp to look at. The back button,
 * the "⋮" menu and the player's track button are all this.
 */
@Composable
fun ChipButton(icon: Painter, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(VibeyFinTokens.Size.touch).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(CHIP_SIZE).clip(CircleShape).background(VibeyFinTokens.Colors.scrim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = description,
                tint = VibeyFinTokens.Colors.ink,
                modifier = Modifier.size(CHIP_ICON),
            )
        }
    }
}
