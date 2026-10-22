package app.vibeyfin.app.children

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.app.components.ArtworkTile
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.skeletonTiles
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.children.ChildrenUiState
import app.vibeyfin.ui.children.ChildrenViewModel
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.HomeExit
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Start loading the next page when this many items or fewer are left below the visible ones. */
private const val LOAD_AHEAD = 6

@Composable
fun ChildrenRoute(
    parentId: String,
    title: String,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onExit: (HomeExit) -> Unit,
    viewModel: ChildrenViewModel = koinViewModel(parameters = { parametersOf(parentId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exit) {
        state.exit?.let(onExit)
    }
    ChildrenScreen(
        title = title,
        state = state,
        onBack = onBack,
        onOpen = onOpen,
        onLoadMore = viewModel::onLoadMore,
        onRetry = viewModel::onRetry,
    )
}

@Composable
fun ChildrenScreen(
    title: String,
    state: ChildrenUiState,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    // Buttons under the title that stay put while the list scrolls, such as a category page's Sort and Year.
    controls: (@Composable () -> Unit)? = null,
) {
    // With controls the title is pinned above them; without, it is the first row of the list and scrolls away.
    val pinned = controls != null
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = screenMargin())) {
                if (controls != null) {
                    Title(title)
                    controls()
                }
                when {
                    state.loading -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(130.dp),
                        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
                        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
                        userScrollEnabled = false,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (!pinned) item(span = { GridItemSpan(maxLineSpan) }, key = "title") { Title(title) }
                        skeletonTiles()
                    }
                    state.message != null -> {
                        if (!pinned) Title(title)
                        LoadError(state.message!!, onRetry)
                    }
                    state.isEmpty -> {
                        if (!pinned) Title(title)
                        Text(
                            text = Strings.emptyFolder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> ItemGrid(title, state, onOpen, onLoadMore, onRetry, showTitle = !pinned)
                }
            }
            FloatingBackButton(onBack)
        }
    }
}

@Composable
private fun Title(title: String) {
    // A Column: as a lazy grid item, several loose children would be drawn on top of each other.
    Column {
        Spacer(Modifier.height(BackButtonSpace))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(VibeyFinTokens.Space.s4))
    }
}

/** Where the subtitle [label] sits among the grid's items: the title (when it is in the list), then every item and subtitle before it. */
private fun headingIndex(state: ChildrenUiState, label: String, showTitle: Boolean): Int {
    var index = if (showTitle) 1 else 0
    for (item in state.items) {
        val heading = state.headings[item.id]
        if (heading != null) {
            if (heading == label) return index
            index++
        }
        index++
    }
    return 0
}

/** The years on the page, each a button that jumps to it. */
@Composable
private fun YearStrip(years: List<String>, onJump: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
        modifier = Modifier.padding(bottom = VibeyFinTokens.Space.s3),
    ) {
        items(years, key = { it }) { year ->
            TextButton(onClick = { onJump(year) }) {
                Text(year, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A subtitle that splits the list, such as a release year. */
@Composable
private fun YearHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun ItemGrid(
    title: String,
    state: ChildrenUiState,
    onOpen: (MediaItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    showTitle: Boolean,
) {
    // Saved with the screen, so coming back from an item's page finds the list where it was left.
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - LOAD_AHEAD
        }
    }
    // Keyed on the count too: a short page can leave the end in view, which needs another page.
    LaunchedEffect(nearEnd, state.items.size, state.hasMore) {
        if (nearEnd && state.hasMore) onLoadMore()
    }
    Column(Modifier.fillMaxSize()) {
        // Pages ordered by year can jump to a year, from the years loaded so far.
        if (state.headings.isNotEmpty()) {
            YearStrip(state.headings.values.toList()) { year ->
                scope.launch { gridState.animateScrollToItem(headingIndex(state, year, showTitle)) }
            }
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(130.dp),
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
            modifier = Modifier.fillMaxSize(),
        ) {
            // The title is part of the list, beneath the button, and scrolls up under it.
            if (showTitle) item(span = { GridItemSpan(maxLineSpan) }, key = "title") { Title(title) }
            state.items.forEach { item ->
                // The "New" page names each release year above its titles.
                state.headings[item.id]?.let { heading ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "heading-${item.id}") { YearHeading(heading) }
                }
                item(key = item.id) { ArtworkTile(item, onClick = { onOpen(item) }) }
            }
            if (state.loadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "loading-more") {
                    Box(Modifier.fillMaxWidth().padding(VibeyFinTokens.Space.s4), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.loadMoreFailed) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "load-more-failed") {
                    LoadError(Strings.loadMoreFailed, onRetry)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "bottom-space") { Spacer(Modifier.height(VibeyFinTokens.Space.s5)) }
        }
    }
}
