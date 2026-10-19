package com.liamgleeson.vibeyfin.app.children

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liamgleeson.vibeyfin.app.components.ArtworkTile
import com.liamgleeson.vibeyfin.app.components.BackButtonSpace
import com.liamgleeson.vibeyfin.app.components.FloatingBackButton
import com.liamgleeson.vibeyfin.app.components.LoadError
import com.liamgleeson.vibeyfin.domain.model.MediaItem
import com.liamgleeson.vibeyfin.ui.children.ChildrenUiState
import com.liamgleeson.vibeyfin.ui.children.ChildrenViewModel
import com.liamgleeson.vibeyfin.ui.home.HomeExit
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
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
                when {
                    state.loading -> {
                        Title(title)
                        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    state.message != null -> {
                        Title(title)
                        LoadError(state.message!!, onRetry)
                    }
                    state.isEmpty -> {
                        Title(title)
                        Text(
                            text = "There's nothing in here yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> ItemGrid(title, state, onOpen, onLoadMore, onRetry)
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
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ItemGrid(
    title: String,
    state: ChildrenUiState,
    onOpen: (MediaItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val gridState = rememberLazyGridState()
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
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(130.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // The title is part of the list, beneath the button, and scrolls up under it.
        item(span = { GridItemSpan(maxLineSpan) }, key = "title") { Title(title) }
        items(state.items, key = { it.id }) { item ->
            ArtworkTile(item, onClick = { onOpen(item) })
        }
        if (state.loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "loading-more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.loadMoreFailed) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "load-more-failed") {
                LoadError("Couldn't load the rest of this list. Check your connection and try again.", onRetry)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
    }
}
