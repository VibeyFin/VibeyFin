package app.vibeyfin.app.home

import app.vibeyfin.app.downloads.DownloadActionMenuItem
import app.vibeyfin.app.downloads.DownloadBadgeIcon
import app.vibeyfin.app.downloads.RemoveConfirmDialog
import app.vibeyfin.app.downloads.rememberDownloadNotificationPermission
import app.vibeyfin.domain.Download
import app.vibeyfin.ui.downloads.DownloadAction
import app.vibeyfin.ui.downloads.DownloadBadge
import app.vibeyfin.ui.downloads.actions
import app.vibeyfin.ui.downloads.badge
import app.vibeyfin.ui.downloads.label
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.R
import app.vibeyfin.app.components.ActionSnackbar
import app.vibeyfin.app.components.ContinueCard
import app.vibeyfin.app.components.ContinueSkeletonCards
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.bleed
import app.vibeyfin.app.components.POSTER_CAPTION_LINES
import app.vibeyfin.app.components.SectionTitle
import app.vibeyfin.app.components.ROW_FADE_MS
import app.vibeyfin.app.components.RowPhase
import app.vibeyfin.app.components.MediaRowSection
import app.vibeyfin.app.components.PosterSkeletonRow
import app.vibeyfin.app.components.SkeletonLine
import app.vibeyfin.app.components.skeletonTiles
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.ui.home.CategoryButton
import app.vibeyfin.ui.home.MediaRow
import app.vibeyfin.ui.home.SeeAll
import app.vibeyfin.ui.home.CategoryKind
import app.vibeyfin.ui.home.ContinueCard as ContinueCardState
import app.vibeyfin.ui.home.ContinueRow
import app.vibeyfin.ui.home.HeroNav
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.home.HomeUiState
import app.vibeyfin.app.settings.PinSheet
import app.vibeyfin.ui.home.HomeViewModel
import org.koin.androidx.compose.koinViewModel

/** The app logo at the top of Home. */
private val LOGO_SIZE = 32.dp

/** How many grey rows stand in for the rows that are still being worked out. */
private const val PLACEHOLDER_ROWS = 2

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
    // The hero's Play found what to play (an episode for a show): the player opens on it, or the page when none was found.
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
        onDownload = viewModel::onDownload,
        onDownloadAction = viewModel::onDownloadAction,
        onConfirmRemove = viewModel::onConfirmRemove,
        onDismissRemoval = viewModel::onDismissRemoval,
        onRetryAction = viewModel::onRetryAction,
        onDismissMessage = viewModel::onDismissMessage,
        onRefresh = viewModel::onRefresh,
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
        onSwitchProfile = viewModel::onSwitchProfile,
        onSwitchServer = viewModel::onSwitchServer,
        onSettings = onSettings,
        onSignOut = viewModel::onSignOut,
        onSubmitPin = viewModel::onSubmitPin,
        onForgotPin = viewModel::onForgotPin,
        onSubmitPassword = viewModel::onSubmitPassword,
        onDismissPin = viewModel::onDismissPin,
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
    onSwitchProfile: () -> Unit,
    onSwitchServer: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
    onSubmitPin: (String) -> Unit,
    onForgotPin: () -> Unit,
    onSubmitPassword: (String) -> Unit,
    onDismissPin: () -> Unit,
) {
    state.pinPrompt?.let { prompt ->
        PinSheet(prompt, onSubmitPin, onForgotPin, onSubmitPassword, onDismissPin)
    }
    val margin = screenMargin()
    // The card whose actions are showing, after a long press.
    var menuCard by remember { mutableStateOf<ContinueCardState?>(null) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // No side margin on the column: the Continue watching row runs to the screen edge, so the
        // header and the grid each apply the margin themselves.
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                // The logo, the page name and the profile are centred in the header, with the same space above and below.
                modifier = Modifier.fillMaxWidth().padding(horizontal = margin, vertical = VibeyFinTokens.Space.s3),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
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
                ProfileMenu(state.userName, state.kidsProfile, onSwitchProfile, onSwitchServer, onSettings, onSignOut)
            }
            if (state.serverId != null) {
                Content(
                    state, margin, onOpen, onPlay, onDetails, onHeroMyList, onHeroPlay, { menuCard = it }, onRefresh, onRetryContinue, onRetryRow, onSeeAll,
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
                // Download without opening the page: a movie or an episode goes into the queue; once there is one, a tap opens its actions.
                val download = state.downloads[card.id]
                if (state.canDownload || download != null) {
                    SheetDownloadAction(
                        download,
                        onStart = { menuCard = null; onDownload(card.id) },
                        onAction = { action -> menuCard = null; onDownloadAction(card.id, action) },
                    )
                }
            }
        }
    }
    // The question before a card's download is deleted.
    state.removePrompt?.let { RemoveConfirmDialog(it, onConfirm = onConfirmRemove, onDismiss = onDismissRemoval) }
}

/** The Download entry of a Continue watching card's menu: its badge and words, a tap starts it, or with a download opens its actions. */
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
            download?.actions?.forEach { action -> DownloadActionMenuItem(action) { menu = false; onAction(action) } }
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

/** Continue watching and the rows under it in one scrolling grid, with a pull to refresh over it. */
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
            contentPadding = PaddingValues(start = margin, top = 0.dp, end = margin, bottom = margin),
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
            // The featured item, when there is one: full width, under the buttons and above what is being watched.
            // While it is being picked a grey card of the same size stands in and fades into the hero, so it does not just appear.
            if (state.heroes.isNotEmpty() || state.heroLoading) {
                fullWidth(key = "hero", animate = false) {
                    val wideHero = LocalConfiguration.current.screenWidthDp >= 600
                    // Keyed on which title it is, not on the title's data: a tap on My List changes the data, and only the heart
                    // should redraw, not the whole card fading into a copy of itself.
                    Crossfade(targetState = state.heroes.firstOrNull()?.id, animationSpec = tween(ROW_FADE_MS), label = "hero") { heroId ->
                        val hero = state.heroes.firstOrNull()
                        if (heroId == null || hero == null) HomeHeroSkeleton(wideHero) else HomeHeroSection(hero, wideHero, { onHeroPlay(hero.id) }, onDetails, { onHeroMyList(hero.id) })
                    }
                }
            }
            fullWidth(key = "continue") { ContinueWatching(state.continueRow, margin, onPlay, onDetails, onLongPress, onRetryContinue) }
            // Until the libraries are known two grey rows hold the place, so the rows that replace them don't push
            // the genre rows down from under the user's eyes.
            if (state.loadingLibraries && state.rows.isEmpty()) {
                repeat(PLACEHOLDER_ROWS) { index ->
                    fullWidth(key = "placeholder-$index") { RowPlaceholder(margin) }
                }
            }
            // Recently added in each library, then a few genres: each loads and fails on its own, and an empty one is not here.
            state.rows.forEach { row ->
                fullWidth(key = row.id) { MediaRowSection(row, margin, onOpen, onSeeAll, onRetry = { onRetryRow(row.id) }) }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, animate: Boolean = true, content: @Composable () -> Unit) {
    // Rows that arrive, leave or change height slide and fade into place instead of snapping, so nothing jumps. The hero does not:
    // its grey card is simply there, and the hero fades into it in place.
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = "row") { Box(if (animate) Modifier.animateItem() else Modifier) { content() } }
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
    // Nothing to show (it came back empty): no title and no gap, like any other row.
    if (!row.isVisible) return
    Column(Modifier.padding(bottom = VibeyFinTokens.Space.s2), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        SectionTitle(Strings.continueWatching)
        val phase = if (row.loading) RowPhase.Loading else if (row.message != null) RowPhase.Failed else RowPhase.Ready
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

/** A row's header and posters in grey, for the moment before the rows are known. */
@Composable
private fun RowPlaceholder(margin: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        SkeletonLine(220.dp, MaterialTheme.typography.titleLarge)
        PosterSkeletonRow(Modifier.bleed(margin).padding(horizontal = margin))
    }
}

/** The profile button at the top right and the menu it opens. */
@Composable
private fun ProfileMenu(
    userName: String?,
    kidsProfile: Boolean,
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
            // A kids profile cannot sign out; the view model ignores it too.
            if (!kidsProfile) VibeyFinMenuItem(Strings.signOut, Icons.AutoMirrored.Filled.ExitToApp) { open = false; onSignOut() }
        }
    }
}
