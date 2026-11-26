package app.vibeyfin.app.children

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.R
import app.vibeyfin.app.components.ActionSnackbar
import app.vibeyfin.app.components.ArtworkTile
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.MediaRowSection
import app.vibeyfin.app.components.Skeleton
import app.vibeyfin.app.components.TabHeader
import app.vibeyfin.app.components.bleed
import app.vibeyfin.app.components.placeholderGridCount
import app.vibeyfin.app.components.placeholderTiles
import app.vibeyfin.app.downloads.DownloadConfirmDialog
import app.vibeyfin.app.downloads.RemoveConfirmDialog
import app.vibeyfin.app.downloads.rememberDownloadNotificationPermission
import app.vibeyfin.app.navigation.LocalBottomBarPadding
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.pageInsetsPadding
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaAspect
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.children.ChildrenUiState
import app.vibeyfin.ui.children.ChildrenViewModel
import app.vibeyfin.ui.common.Placeholders
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.home.MediaRow
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private const val LOAD_AHEAD = 6

@Composable
fun ChildrenRoute(
    parentId: String,
    title: String,
    /** The page is known to be a collection's (from the tile or search result opened): its header is up at once. */
    collectionHint: Boolean = false,
    /** The picture of the tile or result opened, so the header's picture starts loading before the collection itself is read. */
    imageHint: String? = null,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    /** A season's list leads back to its show. */
    onOpenSeries: (String) -> Unit = {},
    onExit: (HomeExit) -> Unit,
    viewModel: ChildrenViewModel = koinViewModel(parameters = { parametersOf(parentId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.exit) {
        state.exit?.let(onExit)
    }

    val ensureNotifications = rememberDownloadNotificationPermission()

    Box(Modifier.fillMaxSize()) {
        ChildrenScreen(
            title = title,
            collectionHint = collectionHint,
            imageHint = imageHint,
            state = state,
            onBack = onBack,
            onOpen = onOpen,
            onLoadMore = viewModel::onLoadMore,
            onRetry = viewModel::onRetry,
            onOpenSeries = onOpenSeries,
            onDownloadSeason = viewModel::onDownloadSeason,
            onRemoveSeasonDownloads = viewModel::onRemoveSeasonDownloads,
        )
        ActionSnackbar(state.downloadMessage, onRetry = viewModel::onRetryDownload, onDismiss = viewModel::onDismissDownloadMessage)
    }

    state.downloadPrompt?.let { prompt ->
        DownloadConfirmDialog(
            prompt,
            onToggleUnwatched = viewModel::onToggleUnwatchedOnly,
            onConfirm = {
                ensureNotifications()
                viewModel.onConfirmDownload()
            },
            onDismiss = viewModel::onDismissDownloadPrompt,
        )
    }

    state.removePrompt?.let { RemoveConfirmDialog(it, onConfirm = viewModel::onConfirmRemove, onDismiss = viewModel::onDismissRemoval) }
}

@Composable
fun ChildrenScreen(
    title: String,
    state: ChildrenUiState,
    /** A tab's root page: no back button, and a larger title. */
    isRoot: Boolean = false,
    /** A soft line under the title, such as how many titles the Library holds. */
    subtitle: String? = null,
    collectionHint: Boolean = false,
    imageHint: String? = null,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    controls: (@Composable () -> Unit)? = null,
    onOpenSeries: ((String) -> Unit)? = null,
    onDownloadSeason: (() -> Unit)? = null,
    onRemoveSeasonDownloads: (() -> Unit)? = null,
) {
    val pinned = controls != null
    val collectionPage = state.collection != null || collectionHint
    val wideScreen: Boolean = LocalConfiguration.current.screenWidthDp >= 600
    val tileMinWidth: Dp = posterMinWidth(isRoot, wideScreen)
    val tiles: Int = placeholderGridCount(tileMinWidth, MediaAspect.POSTER, screenMargin())

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            // A collection's header runs under the status bar
            val insets = if (collectionPage) Modifier else Modifier.pageInsetsPadding()

            Column(modifier = Modifier.fillMaxSize().then(insets).padding(horizontal = screenMargin())) {
                if (controls != null) {
                    Title(title, subtitle, isRoot)
                    controls()
                }

                when {
                    state.loading -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(tileMinWidth),
                        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
                        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
                        userScrollEnabled = false,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (collectionPage) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "collection") {
                                Box(Modifier.bleed(screenMargin())) { CollectionHeader(title, state.collection, emptyList(), screenMargin(), imageHint) }
                            }
                        } else if (!pinned) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "title") { Title(title, subtitle, isRoot) }
                        }

                        if (collectionPage) {
                            // A collection's members are rows of posters, so gray rows stand in, not a grid
                            item(span = { GridItemSpan(maxLineSpan) }, key = "members-skeleton") {
                                Skeleton {
                                    MediaRowSection(Placeholders.mediaRow(0).copy(loading = true), screenMargin(), onOpen = {}, onSeeAll = null, onRetry = {}, tileWidth = if (wideScreen) 168.dp else 132.dp)
                                }
                            }
                        } else {
                            placeholderTiles(tiles, "skeleton") { index ->
                                ArtworkTile(Placeholders.posterTile.copy(id = "placeholder-$index"), onClick = {})
                            }
                        }
                    }

                    state.message != null -> {
                        if (!pinned) Title(title, subtitle, isRoot)

                        LoadError(state.message!!, onRetry)
                    }

                    state.isEmpty -> {
                        if (!pinned) Title(title, subtitle, isRoot)

                        Text(
                            text = state.emptyText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> ItemGrid(title, state, collectionPage, imageHint, isRoot, onOpen, onLoadMore, onRetry, showTitle = !pinned, onOpenSeries = onOpenSeries, onDownloadSeason = onDownloadSeason, onRemoveSeasonDownloads = onRemoveSeasonDownloads)
                }
            }

            if (!isRoot) FloatingBackButton(onBack)
        }
    }
}

@Composable
private fun Title(title: String, subtitle: String?, isRoot: Boolean) {
    // The Library tab has the header every tab has; the screen already has the side margin
    if (isRoot) return TabHeader(title, horizontalPadding = 0.dp)

    // A Column: as a lazy grid item, several loose children would be drawn on top of each other
    Column {
        Spacer(Modifier.height(BackButtonSpace))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        subtitle?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(VibeyFinTokens.Space.s4))
    }
}

@Composable
private fun SeasonDownloadRow(state: ChildrenUiState, onDownload: () -> Unit, onRemove: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        modifier = Modifier.fillMaxWidth(),
    ) {
        FilledTonalButton(onClick = onDownload) {
            Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(VibeyFinTokens.Space.s2))
            Text(Strings.downloadSeasonButton)
        }

        if (state.seasonDownloads.isNotEmpty()) {
            Text(
                text = Strings.seasonDownloadedCount(state.seasonDownloaded, state.items.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )

            if (onRemove != null) {
                TextButton(onClick = onRemove) { Text(Strings.removeDownloadsOf(state.seasonDownloads.size), color = VibeyFinTokens.Colors.inkMuted) }
            }
        }
    }
}

/** The narrowest a poster gets in the grid: the library root has the smallest, a tablet the largest */
internal fun posterMinWidth(isRoot: Boolean, wide: Boolean): Dp = when {
    isRoot && wide -> 140.dp
    isRoot -> 104.dp
    wide -> 168.dp
    else -> 130.dp
}

@Composable
private fun ItemGrid(
    title: String,
    state: ChildrenUiState,
    collectionPage: Boolean,
    imageHint: String?,
    isRoot: Boolean,
    onOpen: (MediaItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    showTitle: Boolean,
    onOpenSeries: ((String) -> Unit)?,
    onDownloadSeason: (() -> Unit)?,
    onRemoveSeasonDownloads: (() -> Unit)?,
) {
    // Saved with the screen, so coming back from an item's page finds the list where it was left
    val gridState = rememberLazyGridState()

    val firstId = state.items.firstOrNull()?.id
    var listedFirstId by remember { mutableStateOf(firstId) }
    val margin = screenMargin()
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo

            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - LOAD_AHEAD
        }
    }

    // A new list (a filter changed) starts at its top. Requested before it is measured, or the grid first follows the old top poster's key, then snaps back
    if (firstId != listedFirstId) {
        listedFirstId = firstId
        gridState.requestScrollToItem(0)
    }

    // Keyed on the count too: a short page can leave the end in view, which needs another page
    LaunchedEffect(nearEnd, state.items.size, state.hasMore) {
        if (nearEnd && state.hasMore) onLoadMore()
    }

    LazyVerticalGrid(
        state = gridState,
        // The old list stays, dimmed, until the new one is in: nothing jumps
        modifier = Modifier.fillMaxSize().alpha(if (state.refreshing) 0.45f else 1f),
        columns = GridCells.Adaptive(posterMinWidth(isRoot, wide)),
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current),
    ) {
        // A collection opens on its own header, under the status bar, in place of the plain title
        if (collectionPage) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "collection") {
                Box(Modifier.bleed(margin)) {
                    CollectionHeader(
                        title,
                        state.collection,
                        facts = state.collectionFacts,
                        margin = margin,
                        imageHint = imageHint,
                        scrolled = { if (gridState.firstVisibleItemIndex == 0) gridState.firstVisibleItemScrollOffset else 0 },
                    )
                }
            }
        }

        if (showTitle && !collectionPage) item(span = { GridItemSpan(maxLineSpan) }, key = "title") { Title(title, null, false) }

        state.series?.let { series ->
            if (onOpenSeries != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "series") {
                    TextButton(onClick = { onOpenSeries(series.id) }, modifier = Modifier.semantics { contentDescription = Strings.goToSeries(series.name) }) {
                        Text(series.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (onDownloadSeason != null && state.offersSeasonDownload) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "download") {
                SeasonDownloadRow(state, onDownloadSeason, onRemoveSeasonDownloads)
            }
        }

        if (collectionPage) {
            state.collectionGroups.forEach { (heading, members) ->
                // A row of posters per group, oldest first, as on Home: two huge posters in a grid look empty
                item(span = { GridItemSpan(maxLineSpan) }, key = "group-$heading") {
                    MediaRowSection(
                        row = MediaRow(heading, heading, items = members, loading = false),
                        margin = margin,
                        onOpen = onOpen,
                        onSeeAll = null,
                        onRetry = {},
                        tileWidth = if (wide) 168.dp else 132.dp,
                    )
                }
            }
        } else {
            items(state.items.distinctBy { it.id }, key = { it.id }) { item ->
                ArtworkTile(item, onClick = { onOpen(item) }, modifier = Modifier.animateItem())
            }
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
