package app.vibeyfin.app.filtered

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.children.ChildrenScreen
import app.vibeyfin.app.components.FilterChipButton
import app.vibeyfin.app.components.OptionRow
import app.vibeyfin.app.components.PickerSearch
import app.vibeyfin.app.components.PickerSheet
import app.vibeyfin.design.VibeyFinTokens
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.filtered.FilteredListViewModel
import app.vibeyfin.ui.filtered.ListChip
import app.vibeyfin.ui.filtered.ListSheet
import app.vibeyfin.ui.home.HomeExit
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The page a category button or the Categories sheet opens, titled with its name ("Shows", "New", "Action"). It is
 * drawn like a folder's list, with Sort and Genre buttons under the title that open the player's picker sheet.
 */
@Composable
fun FilteredListRoute(
    filter: HomeFilter,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onExit: (HomeExit) -> Unit,
    /** The Library tab's root page: no back button. */
    isRoot: Boolean = false,
    viewModel: FilteredListViewModel = koinViewModel(parameters = { parametersOf(filter) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.list.exit) {
        state.list.exit?.let(onExit)
    }

    ChildrenScreen(
        title = viewModel.title,
        state = state.list,
        isRoot = isRoot,
        onBack = onBack,
        onOpen = onOpen,
        onLoadMore = viewModel::onLoadMore,
        onRetry = viewModel::onRetry,
        controls = {
            ListChips(state.chips) { viewModel.onChip(it.kind) }
        },
    )
    state.sheet?.let { sheet ->
        ListPicker(sheet, onPick = viewModel::onPick, onDismiss = viewModel::onDismissSheet, onRetry = viewModel::onRetrySheet, onQuery = viewModel::onSheetQuery)
    }
}

@Composable
private fun ListChips(chips: List<ListChip>, onTap: (ListChip) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(bottom = VibeyFinTokens.Space.s3),
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        items(chips, key = { it.kind }) { chip ->
            FilterChipButton(chip.label, onClick = { onTap(chip) }, active = chip.active, opensList = chip.opensList)
        }
    }
}

@Composable
private fun ListPicker(sheet: ListSheet, onPick: (String) -> Unit, onDismiss: () -> Unit, onRetry: () -> Unit, onQuery: (String) -> Unit) {
    val rows = remember(sheet.options, sheet.selectedId) {
        sheet.options.map { option ->
            OptionRow(key = option.id, label = option.name, selected = option.id == sheet.selectedId, detail = option.count?.toString())
        }
    }

    val searchFoundNothing = sheet.options.isEmpty() && !sheet.loading && sheet.message == null

    PickerSheet(
        title = sheet.title,
        rows = rows,
        loading = sheet.loading,
        message = sheet.message,
        // A search (People) says what to do or that nobody was found while it has no rows
        emptyText = if (sheet.searchable && searchFoundNothing) sheet.emptyText else null,
        onPick = onPick,
        onDismiss = onDismiss,
        onRetry = onRetry,
        search = if (sheet.searchable) PickerSearch(sheet.query, sheet.hint, onQuery) else null,
    )
}
