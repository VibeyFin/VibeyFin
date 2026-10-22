package app.vibeyfin.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.SkeletonLine
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.vibeyFinTextFieldColors
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.CategoryButton
import app.vibeyfin.ui.home.FilterDropdown

/**
 * Shows, Movies, New and Categories as one scrolling row of M3 chips. Every chip opens something: the
 * first three open their page; Categories carries a chevron and opens a dropdown with search first, which is
 * how it differs. None of them stays selected, because Home itself is never filtered. Aqua is not used here.
 * Margins come from [contentPadding] so the row can run to the screen edge.
 */
@Composable
fun CategoryButtonRow(
    buttons: List<CategoryButton>,
    dropdown: FilterDropdown?,
    contentPadding: PaddingValues,
    onTap: (CategoryButton) -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.semantics { contentDescription = Strings.filterHome },
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        items(buttons, key = { it.kind }) { button ->
            // The dropdown hangs from its own chip, so each chip is its own anchor.
            Box {
                AssistChip(
                    onClick = { onTap(button) },
                    label = { Text(button.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = if (button.opensDropdown) {
                        { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = VibeyFinTokens.Colors.inkMuted,
                        trailingIconContentColor = VibeyFinTokens.Colors.inkMuted,
                    ),
                    border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = VibeyFinTokens.Colors.lineStrong),
                    modifier = if (button.opensDropdown) {
                        Modifier.semantics { contentDescription = Strings.opensList(button.label) }
                    } else {
                        Modifier
                    },
                )
                if (dropdown != null && dropdown.kind == button.kind) {
                    FilterDropdownMenu(dropdown, onPick, onDismiss, onRetry)
                }
            }
        }
    }
}

/**
 * The Categories list as a dropdown under its chip, with a search field that narrows the list as you
 * type. Picking an entry opens that genre's page. Loading is grey lines, an empty list says so, a
 * failure offers Try again.
 */
@Composable
private fun FilterDropdownMenu(dropdown: FilterDropdown, onPick: (String) -> Unit, onDismiss: () -> Unit, onRetry: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(dropdown.options, query) {
        dropdown.options.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    VibeyFinMenu(expanded = true, onDismiss = onDismiss) {
        Column(Modifier.widthIn(min = 240.dp, max = 320.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(Strings.filterSearch) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                colors = vibeyFinTextFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = VibeyFinTokens.Space.s3, vertical = VibeyFinTokens.Space.s2),
            )
            when {
                dropdown.loading -> Column(
                    Modifier.padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s3),
                    verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
                ) { repeat(4) { SkeletonLine(width = 160.dp, style = MaterialTheme.typography.bodyLarge) } }
                dropdown.message != null -> LoadError(dropdown.message!!, onRetry, Modifier.padding(horizontal = VibeyFinTokens.Space.s4))
                dropdown.isEmpty -> EmptyLine(dropdown.emptyText)
                shown.isEmpty() -> EmptyLine(Strings.nothingFound)
                else -> Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    shown.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = { onPick(option.id) },
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.heightIn(min = 52.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s3),
    )
}
