package app.vibeyfin.app.components

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import app.vibeyfin.app.theme.VibeyFinTokens

/** One row of an [OptionList]: [key] is handed back when it is chosen. */
data class OptionRow(val key: String, val label: String, val selected: Boolean = false)

/**
 * A titled list of options for a bottom sheet, with a check on the chosen row (the player's audio, subtitle and
 * speed pickers, and Home's Categories). Only the chosen row has a mark, so the state is not carried by colour.
 */
@Composable
fun OptionList(title: String, options: List<OptionRow>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    // Lazy, so a file with dozens of subtitle tracks only builds the rows on screen as the sheet slides up.
    // No stretch at the ends of the list.
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
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
                    headlineContent = { Text(option.label, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        if (option.selected) Icon(Icons.Filled.Check, contentDescription = null, tint = VibeyFinTokens.Colors.ink)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.selectable(selected = option.selected, role = Role.RadioButton, onClick = { onSelect(option.key) }),
                )
            }
        }
    }
}

/**
 * Scrolling past the end of a list must not pass on to the sheet, which would move with it. Leftovers
 * towards the end stop here; pulling down at the top still reaches the sheet, so a swipe down closes it.
 */
private val KeepAtListEnd = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) =
        if (available.y < 0f) available else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity) =
        if (available.y < 0f) available else Velocity.Zero
}
