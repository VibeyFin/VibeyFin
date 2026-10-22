package app.vibeyfin.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.R
import app.vibeyfin.app.components.ActionSnackbar
import app.vibeyfin.app.components.ArtworkTile
import app.vibeyfin.app.components.ContinueCard
import app.vibeyfin.app.components.ContinueSkeletonCards
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.skeletonTiles
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.ui.home.CategoryButton
import app.vibeyfin.ui.home.CategoryKind
import app.vibeyfin.ui.home.ContinueCard as ContinueCardState
import app.vibeyfin.ui.home.ContinueRow
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.home.HomeUiState
import app.vibeyfin.ui.home.HomeViewModel
import org.koin.androidx.compose.koinViewModel

/** The app logo at the top of Home. */
private val LOGO_SIZE = 32.dp

@Composable
fun HomeRoute(
    onExit: (HomeExit) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onPlay: (String) -> Unit,
    onDetails: (String) -> Unit,
    onSettings: () -> Unit,
    onOpenList: (HomeFilter) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exit) {
        state.exit?.let(onExit)
    }
    // Back from another screen, or from the background: something may have been watched elsewhere.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onForeground() }
    // A category button or a genre in the sheet was chosen: its page opens over Home.
    LaunchedEffect(state.openList) {
        state.openList?.let {
            onOpenList(it)
            viewModel.onListOpened()
        }
    }
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
        onCategory = viewModel::onCategory,
        onPickOption = viewModel::onPickOption,
        onDismissSheet = viewModel::onDismissSheet,
        onRetrySheet = viewModel::onRetrySheet,
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
    onCategory: (CategoryKind) -> Unit,
    onPickOption: (String) -> Unit,
    onDismissSheet: () -> Unit,
    onRetrySheet: () -> Unit,
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
                // The app's logo with the name of the page next to it, as in Netflix; the profile is at the right.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1),
                    modifier = Modifier.weight(1f).heightIn(min = VibeyFinTokens.Size.touch),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = Strings.appName,
                        tint = VibeyFinTokens.Colors.ink,
                        modifier = Modifier.size(LOGO_SIZE),
                    )
                    Text(
                        text = Strings.home,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                ProfileMenu(state.userName, onSwitchProfile, onSwitchServer, onSettings, onSignOut)
            }
            if (state.serverId != null) {
                Content(
                    state, margin, onOpen, onPlay, { menuCard = it }, onRefresh, onRetryContinue, onRetryLibraries,
                    onTap = { onCategory(it.kind) },
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            ActionSnackbar(state.actionMessage, onRetry = onRetryAction, onDismiss = onDismissMessage)
        }
    }
    state.sheet?.let { sheet -> CategorySheet(sheet, onPickOption, onDismissSheet, onRetrySheet) }
    menuCard?.let { card ->
        ModalBottomSheet(onDismissRequest = { menuCard = null }) {
            Column(Modifier.padding(bottom = VibeyFinTokens.Space.s5)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = margin, vertical = VibeyFinTokens.Space.s2),
                )
                SheetAction(Strings.details, Icons.Default.Info) { menuCard = null; onDetails(card.id) }
                SheetAction(Strings.markWatched, Icons.Default.Check) { menuCard = null; onMarkWatched(card.id) }
            }
        }
    }
}

@Composable
private fun SheetAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = screenMargin(), vertical = VibeyFinTokens.Space.s4),
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
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
    onTap: (CategoryButton) -> Unit,
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
            // The category buttons scroll with the content and run to the screen edge. Home itself is never
            // filtered: a button opens a page of its own.
            fullWidth(key = "categories") {
                CategoryButtonRow(
                    buttons = state.buttons,
                    contentPadding = PaddingValues(horizontal = margin),
                    onTap = onTap,
                    modifier = Modifier.bleed(margin),
                )
            }
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
    Text(text = text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
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
        state.loadingLibraries -> skeletonTiles(count = 4, aspectRatio = 16f / 9f)
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
                .clickable(role = Role.Button) { open = true }
                // Named for the person, as on iOS; the letter on it would only be read as "A".
                .semantics { contentDescription = userName.orEmpty() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = userName?.take(1)?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = VibeyFinTokens.Colors.ink,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        VibeyFinMenu(expanded = open, onDismiss = { open = false }) {
            VibeyFinMenuItem(Strings.switchProfile, Icons.Filled.Person) { open = false; onSwitchProfile() }
            VibeyFinMenuItem(Strings.switchServer, Icons.Filled.Refresh) { open = false; onSwitchServer() }
            VibeyFinMenuItem(Strings.settings, Icons.Filled.Settings) { open = false; onSettings() }
            VibeyFinMenuItem(Strings.signOut, Icons.AutoMirrored.Filled.ExitToApp) { open = false; onSignOut() }
        }
    }
}
