package app.vibeyfin.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.theme.VibeyFinTokens

/** The search field of a [PickerSheet]. */
class PickerSearch(val query: String, val hint: String, val onChange: (String) -> Unit)

/**
 * A list to pick from in a bottom sheet, drawn like the player's audio and subtitle pickers ([OptionList]): a title
 * and one row each. Loading is gray lines, an empty list says so, a failure offers Try again. Used by Home's Categories
 * and by a category page's Sort and Genre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(
    title: String,
    rows: List<OptionRow>,
    loading: Boolean,
    message: String?,
    emptyText: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    /** A search field above the rows (the People filter): what was typed, its placeholder and who hears each change. */
    search: PickerSearch? = null,
) {
    VibeyFinSheet(onDismiss) {
        val modifier = Modifier.navigationBarsPadding().padding(horizontal = VibeyFinTokens.Space.s2, vertical = VibeyFinTokens.Space.s2)
        val plain = modifier.padding(horizontal = VibeyFinTokens.Space.s2)

        search?.let { field ->
            val focus = remember { FocusRequester() }

            LaunchedEffect(Unit) { focus.requestFocus() }
            TextField(
                value = field.query,
                onValueChange = field.onChange,
                singleLine = true,
                placeholder = { Text(field.hint) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = VibeyFinTokens.Colors.surface2,
                    unfocusedContainerColor = VibeyFinTokens.Colors.surface2,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s2).focusRequester(focus),
            )
        }

        when {
            loading -> Column(plain, verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                repeat(5) { SkeletonLine(width = 160.dp, style = MaterialTheme.typography.bodyLarge) }
            }

            message != null -> Column(plain, verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                LoadError(message, onRetry)
            }

            emptyText != null -> Column(plain, verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(emptyText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> OptionList(title, rows, onSelect = onPick, modifier = modifier)
        }
    }
}
