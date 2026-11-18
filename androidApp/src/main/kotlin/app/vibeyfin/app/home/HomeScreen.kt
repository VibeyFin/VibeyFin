package app.vibeyfin.app.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.ActionSnackbar
import app.vibeyfin.app.components.ContinueCard
import app.vibeyfin.app.components.ContinueSkeletonCards
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.MediaRowSection
import app.vibeyfin.app.components.MenuHeader
import app.vibeyfin.app.components.PosterSkeletonRow
import app.vibeyfin.app.components.ROW_FADE_MS
import app.vibeyfin.app.components.RowPhase
import app.vibeyfin.app.components.SectionTitle
import app.vibeyfin.app.components.SkeletonLine
import app.vibeyfin.app.components.TabHeader
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.bleed
import app.vibeyfin.app.downloads.DownloadActionMenuItem
import app.vibeyfin.app.downloads.DownloadBadgeIcon
import app.vibeyfin.app.downloads.RemoveConfirmDialog
import app.vibeyfin.app.downloads.rememberDownloadNotificationPermission
import app.vibeyfin.app.navigation.LocalBottomBarPadding
import app.vibeyfin.app.seerr.SeerrRowSection
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.pageInsetsPadding
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.domain.Download
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.domain.model.SeerrTitle
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.downloads.DownloadAction
import app.vibeyfin.ui.downloads.DownloadBadge
import app.vibeyfin.ui.downloads.actions
import app.vibeyfin.ui.downloads.badge
import app.vibeyfin.ui.downloads.label
import app.vibeyfin.ui.home.CategoryKind
import app.vibeyfin.ui.home.ContinueRow
import app.vibeyfin.ui.home.HeroNav
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.home.HomeUiState
import app.vibeyfin.ui.home.HomeViewModel
import app.vibeyfin.ui.home.SeeAll
import app.vibeyfin.ui.seerr.SeerrHomeUiState
import app.vibeyfin.ui.seerr.SeerrHomeViewModel
import app.vibeyfin.ui.seerr.SeerrListKind
import org.koin.androidx.compose.koinViewModel
import app.vibeyfin.ui.home.ContinueCard as ContinueCardState

private const val PLACEHOLDER_ROWS = 2

@Composable
fun HomeRoute(
    onExit: (HomeExit) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onPlay: (String) -> Unit,
    onDetails: (String) -> Unit,
    onOpenList: (HomeFilter) -> Unit,
    onOpenSeerr: (SeerrTitle) -> Unit = {},
    onSeerrSeeAll: (SeerrListKind) -> Unit = {},
    onMyRequests: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
    seerrModel: SeerrHomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val seerr by seerrModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.exit) {
        state.exit?.let(onExit)
    }

    // Back from another screen, or from the background: something may have been watched elsewhere
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onForeground()
        seerrModel.onForeground()
    }

    LaunchedEffect(state.openList) {
        state.openList?.let {
            onOpenList(it)
            viewModel.onListOpened()
        }
    }

    LaunchedEffect(state.heroNav) {
        when (val nav = state.heroNav) {
            is HeroNav.Play -> onPlay(nav.itemId)
            is HeroNav.Details -> onDetails(nav.itemId)
            null -> return@LaunchedEffect
        }

        viewModel.onHeroNavHandled()
    }

    HomeScreen(
        state = state,
        onOpen = onOpen,
        onPlay = onPlay,
        onDetails = onDetails,
        onHeroMyList = viewModel::onHeroMyList,
        onHeroPlay = viewModel::onHeroPlay,
        onMarkWatched = viewModel::onMarkWatched,
        onRemoveFromContinueWatching = viewModel::onRemoveFromContinueWatching,
        onDownload = viewModel::onDownload,
        onDownloadAction = viewModel::onDownloadAction,
        onConfirmRemove = viewModel::onConfirmRemove,
        onDismissRemoval = viewModel::onDismissRemoval,
        onRetryAction = viewModel::onRetryAction,
        onDismissMessage = viewModel::onDismissMessage,
        onRefresh = {
            viewModel.onRefresh()
            seerrModel.onRefresh()
        },
        seerr = seerr,
        onOpenSeerr = onOpenSeerr,
        onSeerrSeeAll = onSeerrSeeAll,
        onMyRequests = onMyRequests,
        onRetryContinue = viewModel::onRetryContinue,
        onRetryRow = viewModel::onRetryRow,
        onSeeAll = { target ->
            when (target) {
                is SeeAll.Library -> onOpen(target.library)
                is SeeAll.Genre -> onOpenList(target.filter)
                SeeAll.MyList -> onOpenList(HomeFilter.MyList)
            }
        },
        onCategory = viewModel::onCategory,
        onPickOption = viewModel::onPickOption,
        onDismissSheet = viewModel::onDismissSheet,
        onRetrySheet = viewModel::onRetrySheet,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpen: (MediaItem) -> Unit,
    onPlay: (String) -> Unit,
    onDetails: (String) -> Unit,
    onHeroMyList: (String) -> Unit,
    onHeroPlay: (String) -> Unit,
    onMarkWatched: (String) -> Unit,
    onRemoveFromContinueWatching: (String) -> Unit,
    onDownload: (String) -> Unit,
    onDownloadAction: (String, DownloadAction) -> Unit,
    onConfirmRemove: () -> Unit,
    onDismissRemoval: () -> Unit,
    onRetryAction: () -> Unit,
    onDismissMessage: () -> Unit,
    onRefresh: () -> Unit,
    onRetryContinue: () -> Unit,
    onRetryRow: (String) -> Unit,
    onSeeAll: (SeeAll) -> Unit,
    onCategory: (CategoryKind) -> Unit,
    onPickOption: (String) -> Unit,
    onDismissSheet: () -> Unit,
    onRetrySheet: () -> Unit,
    seerr: SeerrHomeUiState = SeerrHomeUiState(),
    onOpenSeerr: (SeerrTitle) -> Unit = {},
    onSeerrSeeAll: (SeerrListKind) -> Unit = {},
    onMyRequests: () -> Unit = {},
) {
    val margin = screenMargin()

    var menuCard by remember { mutableStateOf<ContinueCardState?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // No side margin on the column: the Continue watching row runs to the screen edge, so the
        // header and the grid each apply the margin themselves.
        Column(modifier = Modifier.fillMaxSize().pageInsetsPadding()) {
            TabHeader(Strings.home)

            if (state.serverId != null) {
                CategoryButtonRow(
                    buttons = state.buttons,
                    contentPadding = PaddingValues(start = margin, end = margin, bottom = VibeyFinTokens.Space.s3),
                    onTap = { onCategory(it.kind) },
                )
                Content(
                    state, margin, onOpen, onPlay, onDetails, onHeroMyList, onHeroPlay, { menuCard = it }, onRefresh, onRetryContinue, onRetryRow, onSeeAll,
                    seerr, onOpenSeerr, onSeerrSeeAll, onMyRequests,
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            ActionSnackbar(
                state.actionMessage,
                onRetry = onRetryAction,
                onDismiss = onDismissMessage,
                actionLabel = state.actionLabel,
                duration = if (state.canUndo) SnackbarDuration.Short else SnackbarDuration.Long,
            )
        }
    }

    state.sheet?.let { sheet -> CategorySheet(sheet, onPickOption, onDismissSheet, onRetrySheet) }
    menuCard?.let { card ->
        ModalBottomSheet(onDismissRequest = { menuCard = null }) {
            Column(Modifier.padding(bottom = VibeyFinTokens.Space.s5)) {
                MenuHeader(card.title, card.subtitle, card.imageUrl, aspectRatio = 16f / 9f, pictureWidth = 72.dp)
                HorizontalDivider(Modifier.padding(horizontal = margin), color = MaterialTheme.colorScheme.outlineVariant)
                SheetAction(Strings.details, Icons.Default.Info) {
                    menuCard = null

                    onDetails(card.id)
                }

                SheetAction(Strings.markWatched, Icons.Default.Check) {
                    menuCard = null

                    onMarkWatched(card.id)
                }

                if (card.canRemove) {
                    SheetAction(Strings.removeFromContinueWatching, Icons.Default.Close) {
                        menuCard = null

                        onRemoveFromContinueWatching(card.id)
                    }
                }

                // Download without opening the page: a movie or an episode goes into the queue; once there is one, a tap opens its actions
                val download = state.downloads[card.id]

                if (state.canDownload || download != null) {
                    SheetDownloadAction(
                        download,
                        onStart = {
                            menuCard = null
                            onDownload(card.id)
                        },
                        onAction = { action ->
                            menuCard = null
                            onDownloadAction(card.id, action)
                        },
                    )
                }
            }
        }
    }

    state.removePrompt?.let { RemoveConfirmDialog(it, onConfirm = onConfirmRemove, onDismiss = onDismissRemoval) }
}

@Composable
private fun SheetDownloadAction(download: Download?, onStart: () -> Unit, onAction: (DownloadAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val ensureNotifications = rememberDownloadNotificationPermission()
    val badge = download?.badge ?: DownloadBadge.None

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (download == null) {
                        ensureNotifications()
                        onStart()
                    } else {
                        menu = true
                    }
                }
                .padding(horizontal = screenMargin(), vertical = VibeyFinTokens.Space.s4),
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DownloadBadgeIcon(badge, download?.progress, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = badge.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }

        VibeyFinMenu(expanded = menu, onDismiss = { menu = false }) {
            download?.actions?.forEach { action ->
                DownloadActionMenuItem(action) {
                    menu = false

                    onAction(action)
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    state: HomeUiState,
    margin: Dp,
    onOpen: (MediaItem) -> Unit,
    onPlay: (String) -> Unit,
    onDetails: (String) -> Unit,
    onHeroMyList: (String) -> Unit,
    onHeroPlay: (String) -> Unit,
    onLongPress: (ContinueCardState) -> Unit,
    onRefresh: () -> Unit,
    onRetryContinue: () -> Unit,
    onRetryRow: (String) -> Unit,
    onSeeAll: (SeeAll) -> Unit,
    seerr: SeerrHomeUiState,
    onOpenSeerr: (SeerrTitle) -> Unit,
    onSeerrSeeAll: (SeerrListKind) -> Unit,
    onMyRequests: () -> Unit,
) {
    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        state = refreshState,
        modifier = Modifier.fillMaxSize(),
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
            contentPadding = PaddingValues(start = margin, top = 0.dp, end = margin, bottom = margin + LocalBottomBarPadding.current),
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.heroes.isNotEmpty() || state.heroLoading) {
                fullWidth(key = "hero", animate = false) {
                    val wideHero = LocalConfiguration.current.screenWidthDp >= 600

                    // Keyed on which titles they are, not on their data: a tap on My List changes the data, and only the heart
                    // should redraw, not the whole card fading into a copy of itself.
                    Crossfade(targetState = state.heroes.map { it.id }, animationSpec = tween(ROW_FADE_MS), label = "hero") { ids ->
                        val heroes = state.heroes

                        if (ids.isEmpty() || heroes.isEmpty()) {
                            HomeHeroSkeleton(wideHero, dots = state.heroSlots >= 2)
                        } else {
                            HomeHeroCarousel(heroes, wideHero, onHeroPlay, onDetails, onHeroMyList)
                        }
                    }
                }
            }

            fullWidth(key = "continue") { ContinueWatching(state.continueRow, margin, onPlay, onDetails, onLongPress, onRetryContinue) }

            // The profile's own Seerr news sits with resume, above discovery
            seerr.requests?.let { row -> fullWidth(key = "seerr-requests", contentType = SEERR_ROW) { SeerrRowSection(row, margin, onOpenSeerr, onSeeAll = onMyRequests) } }

            if (state.loadingLibraries && state.rows.isEmpty()) {
                repeat(PLACEHOLDER_ROWS) { index ->
                    fullWidth(key = "placeholder-$index", contentType = PLACEHOLDER_ROW) { RowPlaceholder(margin) }
                }
            }

            // The library's rows, then Seerr's discover rows, then the genres
            state.rows.forEachIndexed { index, row ->
                if (index == state.firstGenreRow) seerrDiscover(seerr, margin, onOpenSeerr, onSeerrSeeAll)
                fullWidth(key = row.id, contentType = MEDIA_ROW) { MediaRowSection(row, margin, onOpen, onSeeAll, onRetry = { onRetryRow(row.id) }) }
            }
            if (state.firstGenreRow >= state.rows.size) seerrDiscover(seerr, margin, onOpenSeerr, onSeerrSeeAll)
        }
    }
}

private fun LazyGridScope.seerrDiscover(seerr: SeerrHomeUiState, margin: Dp, onOpen: (SeerrTitle) -> Unit, onSeeAll: (SeerrListKind) -> Unit) {
    seerr.discover.forEach { row ->
        val kind = row.kind ?: return@forEach

        fullWidth(key = "seerr-${kind.name}", contentType = SEERR_ROW) { SeerrRowSection(row, margin, onOpen, onSeeAll = { onSeeAll(kind) }) }
    }
}

private fun LazyGridScope.fullWidth(key: String, animate: Boolean = true, contentType: String = key, content: @Composable () -> Unit) {
    // Rows that arrive, leave or change height slide and fade into place instead of snapping, so nothing jumps. The hero does not:
    // its gray card is simply there, and the hero fades into it in place.
    // One content type per kind of row, so a row scrolled away is reused only for a row drawn the same way (a library row for a
    // library row, a Seerr row for a Seerr row), instead of being torn down and rebuilt as a different one mid-fling.
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = contentType) { Box(if (animate) Modifier.animateItem() else Modifier) { content() } }
}

/** The row of what the user is in the middle of. Its cards run past the grid's margins to the screen edge. */
@Composable
internal fun ContinueWatching(
    row: ContinueRow,
    margin: Dp,
    onPlay: (String) -> Unit,
    onDetails: (String) -> Unit,
    onLongPress: (ContinueCardState) -> Unit,
    onRetry: () -> Unit,
) {
    // Nothing to show (it came back empty): no title and no gap, like any other row
    if (!row.isVisible) return

    Column(Modifier.padding(bottom = VibeyFinTokens.Space.s2), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        SectionTitle(Strings.continueWatching)

        val phase = if (row.loading) {
            RowPhase.Loading
        } else if (row.message != null) {
            RowPhase.Failed
        } else {
            RowPhase.Ready
        }

        Crossfade(targetState = phase, animationSpec = tween(ROW_FADE_MS), modifier = Modifier.animateContentSize(tween(ROW_FADE_MS))) { shown ->
            when (shown) {
                RowPhase.Loading -> ContinueSkeletonCards(Modifier.bleed(margin).padding(horizontal = margin))

                RowPhase.Failed -> LoadError(row.message ?: "", onRetry)

                RowPhase.Ready -> {
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
                        items(row.cards.distinctBy { it.id }, key = { it.id }) { card -> ContinueCard(card, onPlay = { onPlay(card.id) }, onDetails = { onDetails(card.id) }, onLongClick = { onLongPress(card) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowPlaceholder(margin: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        SkeletonLine(220.dp, MaterialTheme.typography.titleLarge)
        PosterSkeletonRow(Modifier.bleed(margin).padding(horizontal = margin))
    }
}

private const val MEDIA_ROW = "media-row"
private const val SEERR_ROW = "seerr-row"
private const val PLACEHOLDER_ROW = "placeholder-row"
