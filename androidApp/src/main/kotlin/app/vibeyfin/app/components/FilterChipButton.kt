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
import app.vibeyfin.app.theme.VibeyFinTokens

/**
 * The one filter chip, on Home, a category page and Search (the mockup's FilterChips). One that opens a list ([opensList]) has an arrow.
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
                containerColor = VibeyFinTokens.Colors.sparkSoft,
                labelColor = VibeyFinTokens.Colors.onSparkSoft,
                trailingIconContentColor = VibeyFinTokens.Colors.onSparkSoft,
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
