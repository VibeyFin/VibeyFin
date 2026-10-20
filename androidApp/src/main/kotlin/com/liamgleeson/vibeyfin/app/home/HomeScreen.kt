package com.liamgleeson.vibeyfin.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liamgleeson.vibeyfin.app.theme.screenMargin
import com.liamgleeson.vibeyfin.app.components.ActionSnackbar
import com.liamgleeson.vibeyfin.app.components.ArtworkTile
import com.liamgleeson.vibeyfin.app.components.ContinueCard
import com.liamgleeson.vibeyfin.app.components.ContinueSkeletonCards
import com.liamgleeson.vibeyfin.app.components.LoadError
import com.liamgleeson.vibeyfin.app.theme.VibeyFinTokens
import com.liamgleeson.vibeyfin.domain.model.MediaItem
import com.liamgleeson.vibeyfin.ui.common.Strings
import com.liamgleeson.vibeyfin.ui.home.ContinueCard as ContinueCardState
import com.liamgleeson.vibeyfin.ui.home.ContinueRow
import com.liamgleeson.vibeyfin.ui.home.HomeExit
import com.liamgleeson.vibeyfin.ui.home.HomeUiState
import com.liamgleeson.vibeyfin.ui.home.HomeViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeRoute(
    onExit: (HomeExit) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onPlay: (String) -> Unit,
    onDetails: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exit) {
        state.exit?.let(onExit)
    }
    // Back from another screen, or from the background: something may have been watched elsewhere.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onForeground() }
    HomeScreen(
        state = state,
        onOpen = onOpen,
        onPlay = onPlay,
        onDetails = onDetails,
        onMarkWatched = viewModel::onMarkWatched,
        onRetryAction = viewModel::onRetryAction,
        onDismissMessage = viewModel::onDismissMessage,
        onRefresh = viewModel::onRefresh,
        onRetryContinue = viewModel::onRetryContinue,
        onRetryLibraries = viewModel::onRetryLibraries,
        onSwitchProfile = viewModel::onSwitchProfile,
        onSwitchServer = viewModel::onSwitchServer,
        onSettings = onSettings,
        onSignOut = viewModel::onSignOut,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpen: (MediaItem) -> Unit,
    onPlay: (String) -> Unit,
    onDetails: (String) -> Unit,
    onMarkWatched: (String) -> Unit,
    onRetryAction: () -> Unit,
    onDismissMessage: () -> Unit,
    onRefresh: () -> Unit,
    onRetryContinue: () -> Unit,
    onRetryLibraries: () -> Unit,
    onSwitchProfile: () -> Unit,
    onSwitchServer: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    val margin = screenMargin()
    // The card whose actions are showing, after a long press.
    var menuCard by remember { mutableStateOf<ContinueCardState?>(null) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // No side margin on the column: the Continue watching row runs to the screen edge, so the
        // header and the grid each apply the margin themselves.
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = margin, top = margin, end = margin),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.userName?.let { Strings.greeting(it) } ?: Strings.home,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    state.serverName?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ProfileMenu(state.userName, onSwitchProfile, onSwitchServer, onSettings, onSignOut)
            }
            if (state.serverId != null) {
                Content(state, margin, onOpen, onPlay, { menuCard = it }, onRefresh, onRetryContinue, onRetryLibraries)
            }
        }
        Box(Modifier.fillMaxSize()) {
            ActionSnackbar(state.actionMessage, onRetry = onRetryAction, onDismiss = onDismissMessage)
        }
    }
    menuCard?.let { card ->
        ModalBottomSheet(onDismissRequest = { menuCard = null }) {
            Column(Modifier.padding(bottom = VibeyFinTokens.Space.s5)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = margin, vertical = VibeyFinTokens.Space.s2),
                )
                SheetAction(Strings.details) { menuCard = null; onDetails(card.id) }
                SheetAction(Strings.markWatched) { menuCard = null; onMarkWatched(card.id) }
            }
        }
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = screenMargin(), vertical = VibeyFinTokens.Space.s4),
    )
}

/** Continue watching and the libraries in one scrolling grid, with a pull to refresh over it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    state: HomeUiState,
    margin: Dp,
    onOpen: (MediaItem) -> Unit,
    onPlay: (String) -> Unit,
    onLongPress: (ContinueCardState) -> Unit,
    onRefresh: () -> Unit,
    onRetryContinue: () -> Unit,
    onRetryLibraries: () -> Unit,
) {
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        state = refreshState,
        modifier = Modifier.fillMaxSize(),
        // Aqua is for the primary action and playback progress, so the spinner is neutral.
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = state.refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = VibeyFinTokens.Colors.surface3,
                color = VibeyFinTokens.Colors.ink,
            )
        },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = margin, top = VibeyFinTokens.Space.s5, end = margin, bottom = margin),
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Always laid out, empty or not, so the libraries never move when a card turns up.
            fullWidth(key = "continue") { ContinueWatching(state.continueRow, margin, onPlay, onLongPress, onRetryContinue) }
            fullWidth(key = "libraries-title") { SectionTitle(Strings.libraries) }
            libraries(state, onOpen, onRetryLibraries)
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The row of what the user is in the middle of. Its cards run past the grid's margins to the screen edge. */
@Composable
private fun ContinueWatching(row: ContinueRow, margin: Dp, onPlay: (String) -> Unit, onLongPress: (ContinueCardState) -> Unit, onRetry: () -> Unit) {
    Column(Modifier.padding(bottom = VibeyFinTokens.Space.s2), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        Box(Modifier.alpha(if (row.isVisible) 1f else 0f)) { SectionTitle(Strings.continueWatching) }
        when {
            // Nothing to show: the same space, unseen, so the libraries stay where they are.
            !row.isVisible -> ContinueSkeletonCards(Modifier.bleed(margin).padding(horizontal = margin).alpha(0f).semantics { invisibleToUser() })
            row.loading -> ContinueSkeletonCards(Modifier.bleed(margin).padding(horizontal = margin))
            row.message != null -> LoadError(row.message!!, onRetry)
            else -> {
                // A card that goes (marked watched) or comes back (the save failed) at the front would
                // leave the list anchored on the card after it; start from the front again instead.
                val listState = rememberLazyListState()
                LaunchedEffect(row.cards.firstOrNull()?.id) { listState.scrollToItem(0) }
                LazyRow(
                state = listState,
                modifier = Modifier.bleed(margin),
                contentPadding = PaddingValues(horizontal = margin),
                horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
            ) {
                items(row.cards, key = { it.id }) { card -> ContinueCard(card, onClick = { onPlay(card.id) }, onLongClick = { onLongPress(card) }) }
                }
            }
        }
    }
}

/** Lets a child use [by] more width on each side than its parent gives it, to run under the parent's padding. */
private fun Modifier.bleed(by: Dp) = layout { measurable, constraints ->
    val extra = by.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

private fun LazyGridScope.libraries(state: HomeUiState, onOpen: (MediaItem) -> Unit, onRetry: () -> Unit) {
    when {
        state.loadingLibraries -> fullWidth(key = "libraries-state") {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.librariesMessage != null -> fullWidth(key = "libraries-state") { LoadError(state.librariesMessage!!, onRetry) }
        state.libraries.isEmpty() -> fullWidth(key = "libraries-state") {
            Text(
                text = Strings.noLibraries,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> items(state.libraries, key = { it.id }) { library ->
            ArtworkTile(library, onClick = { onOpen(library) })
        }
    }
}

/** The profile button at the top right and the menu it opens. */
@Composable
private fun ProfileMenu(
    userName: String?,
    onSwitchProfile: () -> Unit,
    onSwitchServer: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(VibeyFinTokens.Size.touch)
                .clip(CircleShape)
                .background(VibeyFinTokens.Colors.surface3)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = userName?.take(1)?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = VibeyFinTokens.Colors.ink,
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            MenuItem(Strings.switchProfile) { open = false; onSwitchProfile() }
            MenuItem(Strings.switchServer) { open = false; onSwitchServer() }
            MenuItem(Strings.settings) { open = false; onSettings() }
            MenuItem(Strings.signOut) { open = false; onSignOut() }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick)
}
