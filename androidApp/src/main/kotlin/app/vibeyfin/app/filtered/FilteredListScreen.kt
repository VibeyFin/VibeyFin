package app.vibeyfin.app.filtered

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.children.ChildrenScreen
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.filtered.FilteredListViewModel
import app.vibeyfin.ui.home.HomeExit
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The page a category button or a dropdown entry opens, titled with its name ("Shows", "A24", "Action"). It is
 * drawn exactly like a folder's list, so it is that screen with this list's state.
 */
@Composable
fun FilteredListRoute(
    filter: HomeFilter,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onExit: (HomeExit) -> Unit,
    viewModel: FilteredListViewModel = koinViewModel(parameters = { parametersOf(filter) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exit) {
        state.exit?.let(onExit)
    }
    ChildrenScreen(
        title = viewModel.title,
        state = state,
        onBack = onBack,
        onOpen = onOpen,
        onLoadMore = viewModel::onLoadMore,
        onRetry = viewModel::onRetry,
    )
}
