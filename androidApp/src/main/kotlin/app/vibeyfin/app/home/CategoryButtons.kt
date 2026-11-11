package app.vibeyfin.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.vibeyfin.app.components.FilterChipButton
import app.vibeyfin.app.components.OptionRow
import app.vibeyfin.app.components.PickerSheet
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.CategoryButton
import app.vibeyfin.ui.home.FilterSheet

/**
 * Shows, Movies, New and Categories as one scrolling row of M3 chips. Every chip opens something: the first three
 * open their page; Categories carries a dropdown arrow and opens a list of genres first, which is how it differs.
 * None of them stays selected, because Home itself is never filtered. Margins come from
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
            FilterChipButton(
                button.label,
                onClick = { onTap(button) },
                opensList = button.opensSheet,
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
