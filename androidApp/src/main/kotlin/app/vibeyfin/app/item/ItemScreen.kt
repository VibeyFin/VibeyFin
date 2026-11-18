package app.vibeyfin.app.item

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.ActionSnackbar
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.DangerConfirmDialog
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.components.ItemSkeleton
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.MediaRowSection
import app.vibeyfin.app.downloads.DownloadConfirmDialog
import app.vibeyfin.app.downloads.RemoveConfirmDialog
import app.vibeyfin.app.downloads.rememberDownloadNotificationPermission
import app.vibeyfin.app.navigation.LocalBottomBarPadding
import app.vibeyfin.app.navigation.sharedPoster
import app.vibeyfin.app.subtitles.SubtitleDeleteSheet
import app.vibeyfin.app.subtitles.SubtitleSearch
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.detailMargin
import app.vibeyfin.app.theme.pageInsetsPadding
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.domain.model.SeerrTitle
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.item.ItemUiState
import app.vibeyfin.ui.item.ItemViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ItemRoute(
    itemId: String,
    onBack: () -> Unit,
    /** Plays [itemId] (the item, or for a show its next episode), from the start when asked. */
    onPlay: (itemId: String, startOver: Boolean) -> Unit,
    onOpen: (MediaItem) -> Unit,
    /** An episode's or a season's page leads back to its show. */
    onOpenSeries: (String) -> Unit,
    /** A cast member's picture or name opens their page. */
    onOpenPerson: (String) -> Unit,
    onExit: (HomeExit) -> Unit,
    /** A Seerr title in "More like this" opens its Seerr page. */
    onOpenSeerr: (SeerrTitle) -> Unit = {},
    viewModel: ItemViewModel = koinViewModel(parameters = { parametersOf(itemId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchingSubtitles by rememberSaveable { mutableStateOf(false) }
    var deletingSubtitles by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.exit) {
        state.exit?.let(onExit)
    }

    SubtitleSearch(
        itemId = itemId,
        visible = searchingSubtitles,
        onDismiss = { searchingSubtitles = false },
        onAdded = viewModel::onSubtitleAdded,
        onFailedWhileClosed = viewModel::onNotice,
    )

    val ensureNotifications = rememberDownloadNotificationPermission()

    ItemScreen(
        state = state,
        onBack = onBack,
        onPlay = { startOver -> onPlay(state.playTarget?.id ?: itemId, startOver) },
        onPlayEpisode = { episodeId -> onPlay(episodeId, false) },
        onOpen = onOpen,
        onOpenSeries = onOpenSeries,
        onOpenPerson = onOpenPerson,
        onToggleFavorite = viewModel::onToggleFavorite,
        onRetry = viewModel::onRetry,
        onTogglePlayed = viewModel::onTogglePlayed,
        onToggleEpisodePlayed = viewModel::onToggleEpisodePlayed,
        onSelectSeason = viewModel::onSelectSeason,
        onRetrySimilar = viewModel::onRetrySimilar,
        onRetryCollections = viewModel::onRetryCollections,
        onRetryExtras = viewModel::onRetryExtras,
        onRetrySeasons = viewModel::onRetrySeasons,
        onRetryEpisodes = viewModel::onRetryEpisodes,
        onRetryAction = viewModel::onRetryAction,
        onDismissMessage = viewModel::onDismissMessage,
        subtitles = SubtitleActions(onSearch = { searchingSubtitles = true }, onDelete = { deletingSubtitles = true }),
        onDismissNotice = viewModel::onDismissNotice,
        downloads = DownloadCallbacks(
            onDownload = viewModel::onDownload,
            onDownloadEpisode = viewModel::onDownloadEpisode,
            onAction = viewModel::onDownloadAction,
            onDownloadSeries = viewModel::onDownloadSeries,
            onDownloadSeason = viewModel::onDownloadSeason,
            onRemoveSeries = viewModel::onRemoveSeriesDownloads,
        ),
        onOpenSeerr = onOpenSeerr,
    )

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

    if (deletingSubtitles && state.offersSubtitleDelete) {
        SubtitleDeleteSheet(
            state.deletableSubtitles,
            onChoose = { streamIndex ->
                deletingSubtitles = false
                viewModel.onDeleteSubtitle(streamIndex)
            },
            onDismiss = { deletingSubtitles = false },
        )
    }
    state.subtitleDeletePrompt?.let {
        DangerConfirmDialog(Strings.deleteSubtitleTitle(it.name), Strings.deleteSubtitleText, Strings.deleteSubtitleConfirm, onConfirm = viewModel::onConfirmDeleteSubtitle, onDismiss = viewModel::onCancelDeleteSubtitle)
    }
}

@Composable
fun ItemScreen(
    state: ItemUiState,
    onBack: () -> Unit,
    onPlay: (startOver: Boolean) -> Unit,
    onPlayEpisode: (String) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onRetry: () -> Unit,
    onTogglePlayed: () -> Unit,
    onToggleEpisodePlayed: (String) -> Unit,
    onSelectSeason: (String) -> Unit,
    onRetrySimilar: () -> Unit,
    onRetryCollections: () -> Unit,
    onRetryExtras: () -> Unit,
    onRetrySeasons: () -> Unit,
    onRetryEpisodes: () -> Unit,
    onRetryAction: () -> Unit,
    onDismissMessage: () -> Unit,
    subtitles: SubtitleActions,
    onDismissNotice: () -> Unit,
    downloads: DownloadCallbacks = DownloadCallbacks.None,
    onOpenSeerr: (SeerrTitle) -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            val item = state.item

            // Gray shapes of the page fade into it, so opening a movie or a show is not a sudden cut; the phase decides what each
            // layer draws, so one fading out keeps its own picture.
            val phase = when {
                state.loading -> ItemPhase.Loading
                state.message != null -> ItemPhase.Failed
                item != null -> ItemPhase.Ready
                else -> ItemPhase.Loading
            }

            Crossfade(targetState = phase, animationSpec = tween(ITEM_FADE_MS), label = "item page") { shown ->
                when (shown) {
                    ItemPhase.Loading -> BoxWithConstraints(Modifier.fillMaxSize()) {
                        ItemSkeleton(wide = maxWidth >= WIDE_FROM, screenWidth = maxWidth, screenHeight = maxHeight, margin = detailMargin(maxWidth >= WIDE_FROM))
                    }

                    ItemPhase.Failed -> Column(Modifier.fillMaxSize().pageInsetsPadding()) {
                        Spacer(Modifier.height(BackButtonSpace))
                        LoadError(message = state.message.orEmpty(), onRetry = onRetry, modifier = Modifier.padding(horizontal = screenMargin()))
                    }

                    // A tablet, or a foldable unfolded, gets the wide layout; a phone, and a foldable closed, the narrow one
                    ItemPhase.Ready -> if (item != null) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            ItemPage(
                                item, state, wide = maxWidth >= WIDE_FROM, screenWidth = maxWidth, screenHeight = maxHeight,
                                onPlay, onPlayEpisode, onOpen, onOpenSeries, onToggleFavorite, onTogglePlayed, onToggleEpisodePlayed, onSelectSeason, onRetrySimilar, onRetryCollections, onRetrySeasons, onRetryEpisodes, onOpenPerson, onRetryExtras, subtitles,
                                downloads = downloads,
                                onOpenSeerr = onOpenSeerr,
                            )
                        }
                    }
                }
            }

            FloatingBackButton(onBack)
            ActionSnackbar(state.actionMessage, onRetry = onRetryAction, onDismiss = onDismissMessage)
            ActionSnackbar(state.notice, onRetry = {}, onDismiss = onDismissNotice, actionLabel = null, duration = SnackbarDuration.Short)
        }
    }
}

private enum class ItemPhase { Loading, Failed, Ready }

private const val ITEM_FADE_MS = 320

private val WIDE_FROM = 600.dp

private val POSTER_WIDE = 168.dp
private val CAST_TILE_NARROW = 88.dp
private val EXTRA_NARROW = 200.dp
private val EXTRA_WIDE = 260.dp
private val CAST_TILE_WIDE = 112.dp

// On a tablet the backdrop stays put and darkens while the rest scrolls up over it
@Composable
private fun ItemPage(
    item: MediaItem,
    state: ItemUiState,
    wide: Boolean,
    screenWidth: Dp,
    screenHeight: Dp,
    onPlay: (startOver: Boolean) -> Unit,
    onPlayEpisode: (String) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onOpenSeries: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit,
    onToggleEpisodePlayed: (String) -> Unit,
    onSelectSeason: (String) -> Unit,
    onRetrySimilar: () -> Unit,
    onRetryCollections: () -> Unit,
    onRetrySeasons: () -> Unit,
    onRetryEpisodes: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onRetryExtras: () -> Unit,
    subtitles: SubtitleActions,
    downloads: DownloadCallbacks,
    onOpenSeerr: (SeerrTitle) -> Unit,
) {
    val margin = detailMargin(wide)
    val sections: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(horizontal = margin, vertical = VibeyFinTokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s5),
        ) {
            if (item.kind == ItemKind.Series) {
                SeasonsSection(state.seasons, margin, wide, onSelectSeason, onPlayEpisode, onToggleEpisodePlayed, onOpen, onRetrySeasons, onRetryEpisodes, state.downloads, state.canDownload, downloads)
            }

            if (item.cast.isNotEmpty()) CastSection(item.cast, margin, tile = if (wide) CAST_TILE_WIDE else CAST_TILE_NARROW, onOpenPerson = onOpenPerson)

            state.extras?.takeIf { it.isVisible }?.let { row ->
                MediaRowSection(row, margin, onOpen, onSeeAll = null, onRetry = onRetryExtras, tileWidth = if (wide) EXTRA_WIDE else EXTRA_NARROW, skeletonAspect = 16f / 9f)
            }

            state.collections?.takeIf { it.isVisible }?.let { row ->
                MediaRowSection(row, margin, onOpen, onSeeAll = null, onRetry = onRetryCollections, tileWidth = if (wide) POSTER_WIDE else VibeyFinTokens.Size.posterWidth)
            }

            state.similar?.takeIf { it.isVisible || state.similarTiles.isNotEmpty() }?.let { row ->
                SimilarSection(row, state.similarTiles, margin, onOpen, onOpenSeerr, onRetry = onRetrySimilar, tileWidth = if (wide) POSTER_WIDE else VibeyFinTokens.Size.posterWidth)
            }

            if (state.technical.isNotEmpty()) TechnicalInfo(state.technical, subtitles)

            Spacer(Modifier.height(VibeyFinTokens.Space.s5))
        }
    }

    val scroll = rememberScrollState()

    if (!wide) {
        // The picture stays put behind the page and darkens; the text goes up over it, and the rest of the page covers it
        val heroHeight = screenWidth * PHONE_HERO_BY_WIDTH

        Box(Modifier.fillMaxSize()) {
            PinnedBackdrop(item, heroHeight, scroll, Modifier.sharedPoster(item.id, source = false))
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                // At least the picture's height; taller when the text needs more room, so nothing is cut off
                Box(Modifier.fillMaxWidth().heightIn(min = heroHeight)) {
                    Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = margin).padding(bottom = VibeyFinTokens.Space.s4)) {
                        HeroDetails(item, state, centered = true, onPlay, onOpenSeries, onToggleFavorite, onPlayTrailer = onPlayEpisode, onTogglePlayed = onTogglePlayed, downloads = downloads)
                    }
                }

                Box(Modifier.fillMaxWidth().background(VibeyFinTokens.Colors.bg).padding(bottom = LocalBottomBarPadding.current)) { sections() }
            }
        }

        return
    }

    // The backdrop is one screen high; it is fully dark by the time the page has scrolled that far
    val firstScreenPx = with(LocalDensity.current) { (screenHeight * FIRST_SCREEN).toPx() }
    val progress = if (firstScreenPx > 0f) scroll.value / firstScreenPx else 0f

    Box(Modifier.fillMaxSize()) {
        ItemBackdrop(item, screenWidth, screenHeight, progress, Modifier.fillMaxSize().sharedPoster(item.id, source = false))
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(screenHeight * FIRST_SCREEN_CLEAR))
            Column(Modifier.fillMaxWidth(0.46f).widthIn(max = 520.dp).padding(horizontal = margin)) {
                HeroDetails(item, state, centered = false, onPlay, onOpenSeries, onToggleFavorite, onPlayTrailer = onPlayEpisode, onTogglePlayed = onTogglePlayed, downloads = downloads)
            }

            sections()
            Spacer(Modifier.height(LocalBottomBarPadding.current))
        }
    }
}

private const val PHONE_HERO_BY_WIDTH = 1.5f

private const val FIRST_SCREEN = 0.7f

private const val FIRST_SCREEN_CLEAR = 0.3f
