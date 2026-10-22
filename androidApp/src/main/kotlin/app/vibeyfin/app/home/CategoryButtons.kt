package app.vibeyfin.app.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.SkeletonLine
import app.vibeyfin.app.components.vibeyFinTextFieldColors
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.CategoryButton
import app.vibeyfin.ui.home.FilterSheet

/** A list this long gets a search field. */
private const val SEARCH_FROM = 15

/**
 * Shows, Movies, New, Studios and Categories as one scrolling row of M3 filter chips. The active one is
 * `accent-soft` with a close icon that clears it; aqua is not used here. Margins come from [contentPadding]
 * so the row can run to the screen edge.
 */
@Composable
fun CategoryButtonRow(
    buttons: List<CategoryButton>,
    contentPadding: PaddingValues,
    onTap: (CategoryButton) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selected = buttons.indexOfFirst { it.selected }
    // The active button comes into view, whichever way it was picked.
    LaunchedEffect(selected) { if (selected >= 0) listState.animateScrollToItem(selected) }
    LazyRow(
        state = listState,
        modifier = modifier.semantics { contentDescription = Strings.filterHome },
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        items(buttons, key = { it.kind }) { button ->
            FilterChip(
                selected = button.selected,
                onClick = { onTap(button) },
                label = { Text(button.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = if (button.selected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = Strings.clearFilter(button.label),
                            modifier = Modifier
                                .size(FilterChipDefaults.IconSize)
                                .clickable(role = Role.Button, onClick = onClear),
                        )
                    }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = VibeyFinTokens.Colors.inkMuted,
                    selectedContainerColor = VibeyFinTokens.Colors.accentSoft,
                    selectedLabelColor = VibeyFinTokens.Colors.onAccentSoft,
                    selectedTrailingIconColor = VibeyFinTokens.Colors.onAccentSoft,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = button.selected,
                    borderColor = VibeyFinTokens.Colors.lineStrong,
                    selectedBorderColor = VibeyFinTokens.Colors.accentSoft,
                ),
            )
        }
    }
}

/**
 * The Studios or Categories list in a bottom sheet: a plain list of names, the picked one checked, a search
 * field when the list is long. Loading is grey lines, an empty list says so, a failure offers Try again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheetView(sheet: FilterSheet, onPick: (String) -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = VibeyFinTokens.Space.s4)) {
            Text(
                text = sheet.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s2),
            )
            when {
                sheet.loading -> Column(
                    Modifier.padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s3),
                    verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
                ) { repeat(5) { SkeletonLine(width = 160.dp, style = MaterialTheme.typography.bodyLarge) } }
                sheet.message != null -> LoadError(sheet.message!!, onRetry, Modifier.padding(horizontal = VibeyFinTokens.Space.s4))
                sheet.isEmpty -> Text(
                    text = sheet.emptyText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s3),
                )
                else -> OptionList(sheet, onPick)
            }
        }
    }
}

@Composable
private fun OptionList(sheet: FilterSheet, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(sheet.options, query) {
        sheet.options.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    if (sheet.options.size >= SEARCH_FROM) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(Strings.filterSearch) },
            singleLine = true,
            colors = vibeyFinTextFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s2),
        )
    }
    LazyColumn(Modifier.heightIn(max = 480.dp)) {
        items(shown, key = { it.id }) { option ->
            val picked = option.id == sheet.pickedId
            ListItem(
                headlineContent = { Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                // The check is ink, not aqua: aqua is for the one primary action and playback progress.
                trailingContent = if (picked) {
                    { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                } else {
                    null
                },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.selectable(selected = picked, role = Role.RadioButton, onClick = { onPick(option.id) }),
            )
        }
    }
}
