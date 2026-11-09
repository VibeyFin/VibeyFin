package app.vibeyfin.app.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import app.vibeyfin.app.theme.MenuPressIndication
import app.vibeyfin.app.theme.VibeyFinTokens

/** One row of an [OptionList]: [key] is handed back when it is chosen. */
data class OptionRow(
    val key: String,
    val label: String,
    val selected: Boolean = false,
    /** Small text at the end of the row, such as how many titles it holds. */
    val detail: String? = null,
    /** What a screen reader says for the row, when the label alone is not enough. */
    val description: String? = null,
    /** A row that does something rather than choosing a value: drawn before the label. */
    val icon: ImageVector? = null,
)

/**
 * A titled list of options for a bottom sheet, the chosen row lit as a soft pill (the player's audio, subtitle and
 * speed pickers, Home's Categories and a category page's filters). The row is also marked selected for a screen reader.
 */
@Composable
fun OptionList(title: String, options: List<OptionRow>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    // Lazy, so a file with dozens of subtitle tracks only builds the rows on screen as the sheet slides up.
    // No stretch at the ends of the list.
    CompositionLocalProvider(LocalOverscrollFactory provides null, LocalIndication provides MenuPressIndication) {
        LazyColumn(modifier.nestedScroll(remember { KeepAtListEnd })) {
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s2),
                )
            }

            items(options, key = { it.key }) { option ->
                ListItem(
                    leadingContent = option.icon?.let { icon -> { Icon(icon, contentDescription = null) } },
                    headlineContent = {
                        Text(
                            option.label,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (option.selected) FontWeight.SemiBold else null,
                        )
                    },
                    trailingContent = if (option.detail != null) {
                        {
                            Row(horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
                                option.detail?.let {
                                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        null
                    },
                    // The chosen row is lit as a soft rounded pill in the accent tint, with its label in that tint: no tick
                    colors = ListItemDefaults.colors(
                        containerColor = if (option.selected) VibeyFinTokens.Colors.accentSoft else Color.Transparent,
                        headlineColor = if (option.selected) VibeyFinTokens.Colors.onAccentSoft else MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .padding(horizontal = VibeyFinTokens.Space.s2)
                        .clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md))
                        .selectable(selected = option.selected, role = Role.RadioButton, onClick = { onSelect(option.key) })
                        .semantics { option.description?.let { contentDescription = it } },
                )
            }
        }
    }
}

// Scrolling past the list's end must not move the sheet; pulling down at the top still reaches it, so a swipe down closes it
internal val KeepAtListEnd = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = if (available.y < 0f) available else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = if (available.y < 0f) available else Velocity.Zero
}
