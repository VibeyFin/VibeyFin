package app.vibeyfin.app.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.theme.VibeyFinTokens

/**
 * The app's dropdown menu: opens just below the control that owns it, with rounded corners and a soft
 * shadow to lift it off the artwork behind it.
 */
@Composable
fun VibeyFinMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 8.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = VibeyFinTokens.Colors.surface2,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        content = content,
    )
}

/**
 * A menu row with its icon on the left and a comfortable touch height. A row without an [icon] keeps the
 * icon's space, so the labels in a menu line up.
 */
@Composable
fun VibeyFinMenuItem(label: String, icon: ImageVector?, selected: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        // The chosen entry (a setting's value) is lit as a soft pill instead of carrying a tick.
        text = { Text(label, fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else null) },
        colors = androidx.compose.material3.MenuDefaults.itemColors(
            textColor = if (selected) VibeyFinTokens.Colors.onAccentSoft else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
        ),
        leadingIcon = {
            if (icon != null) Icon(icon, contentDescription = null) else Spacer(Modifier.size(24.dp))
        },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .then(if (selected) Modifier.background(VibeyFinTokens.Colors.accentSoft) else Modifier)
            .heightIn(min = 52.dp),
    )
}
