package app.vibeyfin.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.PickerSheet
import app.vibeyfin.app.components.OptionRow
import app.vibeyfin.app.components.SkeletonLine
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.CategoryButton
import app.vibeyfin.ui.home.FilterSheet

/**
 * Shows, Movies, New and Categories as one scrolling row of M3 chips. Every chip opens something: the first three
 * open their page; Categories carries a dropdown arrow and opens a list of genres first, which is how it differs.
 * None of them stays selected, because Home itself is never filtered. Aqua is not used here. Margins come from
 * [contentPadding] so the row can run to the screen edge.
 */
@Composable
fun CategoryButtonRow(
    buttons: List<CategoryButton>,
    contentPadding: PaddingValues,
    onTap: (CategoryButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.semantics { contentDescription = Strings.filterHome },
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        items(buttons, key = { it.kind }) { button ->
            AssistChip(
                onClick = { onTap(button) },
                label = { Text(button.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = if (button.opensSheet) {
                    { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) }
                } else {
                    null
                },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = VibeyFinTokens.Colors.inkMuted,
                    trailingIconContentColor = VibeyFinTokens.Colors.inkMuted,
                ),
                border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = VibeyFinTokens.Colors.lineStrong),
                modifier = if (button.opensSheet) {
                    Modifier.semantics { contentDescription = Strings.opensList(button.label) }
                } else {
                    Modifier
                },
            )
        }
    }
}

/**
 * The genres in a bottom sheet, the same list as the player's audio and subtitle pickers, each with how many titles it
 * holds. Nothing is checked, because picking a genre opens its page.
 */
@Composable
fun CategorySheet(sheet: FilterSheet, onPick: (String) -> Unit, onDismiss: () -> Unit, onRetry: () -> Unit) {
    val rows = remember(sheet.options) {
        sheet.options.map { option ->
            OptionRow(
                key = option.id,
                label = option.name,
                detail = option.count?.toString(),
                description = option.count?.let { Strings.titleCount(option.name, it) },
            )
        }
    }
    PickerSheet(
        title = sheet.title,
        rows = rows,
        loading = sheet.loading,
        message = sheet.message,
        emptyText = if (sheet.isEmpty) sheet.emptyText else null,
        onPick = onPick,
        onDismiss = onDismiss,
        onRetry = onRetry,
    )
}
