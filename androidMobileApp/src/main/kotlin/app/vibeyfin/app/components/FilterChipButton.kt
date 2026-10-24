package app.vibeyfin.app.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.vibeyfin.design.VibeyFinTokens

/**
 * The one filter chip, on Home, a category page and Search (the mockup's FilterChips): filled `surface-2` with
 * `ink-muted`, no outline; [active] fills it with `accent-soft`. One that opens a list ([opensList]) has an arrow.
 * Aqua is never used here.
 */
@Composable
fun FilterChipButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    opensList: Boolean = false,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = if (opensList) {
            { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) }
        } else {
            null
        },
        colors = if (active) {
            AssistChipDefaults.assistChipColors(
                containerColor = VibeyFinTokens.Colors.accentSoft,
                labelColor = VibeyFinTokens.Colors.onAccentSoft,
                trailingIconContentColor = VibeyFinTokens.Colors.onAccentSoft,
            )
        } else {
            AssistChipDefaults.assistChipColors(
                containerColor = VibeyFinTokens.Colors.surface2,
                labelColor = VibeyFinTokens.Colors.inkMuted,
                trailingIconContentColor = VibeyFinTokens.Colors.inkMuted,
            )
        },
        border = null,
        modifier = modifier,
    )
}
