package app.vibeyfin.app.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.ActionSnackbar
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.ChipButton
import app.vibeyfin.app.components.ChipInset
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.components.ItemSkeleton
import app.vibeyfin.app.components.LoadError
import app.vibeyfin.app.components.MediaRowSection
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
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
    viewModel: ItemViewModel = koinViewModel(parameters = { parametersOf(itemId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exit) {
        state.exit?.let(onExit)
    }
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
    )
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
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            val item = state.item
            when {
                state.loading -> Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Spacer(Modifier.height(BackButtonSpace))
                    ItemSkeleton(Modifier.padding(horizontal = screenMargin()))
                }
                state.message != null -> Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Spacer(Modifier.height(BackButtonSpace))
                    LoadError(message = state.message!!, onRetry = onRetry, modifier = Modifier.padding(horizontal = screenMargin()))
                }
                // A tablet, or a foldable unfolded, gets the wide layout; a phone, and a foldable closed, the narrow one.
                item != null -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    ItemPage(
                        item, state, wide = maxWidth >= WIDE_FROM, screenWidth = maxWidth, screenHeight = maxHeight,
                        onPlay, onPlayEpisode, onOpen, onOpenSeries, onToggleFavorite, onTogglePlayed, onToggleEpisodePlayed, onSelectSeason, onRetrySimilar, onRetryCollections, onRetrySeasons, onRetryEpisodes, onOpenPerson, onRetryExtras,
                    )
                }
            }
            FloatingBackButton(onBack)
            ActionSnackbar(state.actionMessage, onRetry = onRetryAction, onDismiss = onDismissMessage)
        }
    }
}

/** From this width the page uses its tablet layout: the backdrop at 16:9 with the details in a column on the left. */
private val WIDE_FROM = 600.dp

private val POSTER_WIDE = 168.dp
private val CAST_TILE_NARROW = 88.dp
private val EXTRA_NARROW = 200.dp
private val EXTRA_WIDE = 260.dp
private val CAST_TILE_WIDE = 112.dp

/**
 * The hero, then what the page has below it: a show's seasons and episodes, the cast, the collections it is in, more like this
 * and the file's details. On a phone the hero is the first thing on the page and scrolls away. On a tablet (or an unfolded
 * foldable, [wide]) the backdrop fills the screen and stays put, the first screen leaves it clear with the details in a column
 * on the left, and the rest scrolls up over it while it darkens.
 */
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
) {
    // Larger margins and posters on a tablet (`screens.md`).
    val margin = if (wide) VibeyFinTokens.Space.s6 else screenMargin()
    val sections: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(horizontal = margin, vertical = VibeyFinTokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s5),
        ) {
            if (item.kind == ItemKind.Series) {
                SeasonsSection(state.seasons, margin, wide, onSelectSeason, onPlayEpisode, onToggleEpisodePlayed, onOpen, onRetrySeasons, onRetryEpisodes)
            }
            if (item.cast.isNotEmpty()) CastSection(item.cast, margin, tile = if (wide) CAST_TILE_WIDE else CAST_TILE_NARROW, onOpenPerson = onOpenPerson)
            // The collections it is part of: a poster each, and one opens the collection's members.
            // Behind the scenes and the like: wide tiles, as they are clips, not posters.
            state.extras?.takeIf { it.isVisible }?.let { row ->
                MediaRowSection(row, margin, onOpen, onSeeAll = null, onRetry = onRetryExtras, tileWidth = if (wide) EXTRA_WIDE else EXTRA_NARROW)
            }
            state.collections?.takeIf { it.isVisible }?.let { row ->
                MediaRowSection(row, margin, onOpen, onSeeAll = null, onRetry = onRetryCollections, tileWidth = if (wide) POSTER_WIDE else VibeyFinTokens.Size.posterWidth)
            }
            state.similar?.takeIf { it.isVisible }?.let { row ->
                MediaRowSection(row, margin, onOpen, onSeeAll = null, onRetry = onRetrySimilar, tileWidth = if (wide) POSTER_WIDE else VibeyFinTokens.Size.posterWidth)
            }
            if (state.technical.isNotEmpty()) TechnicalInfo(state.technical)
            Spacer(Modifier.height(VibeyFinTokens.Space.s5))
        }
    }
    val scroll = rememberScrollState()
    if (!wide) {
        // The picture stays put behind the page and darkens; the text goes up over it, and the rest of the page covers it.
        val heroHeight = screenWidth * PHONE_HERO_BY_WIDTH
        Box(Modifier.fillMaxSize()) {
            PinnedBackdrop(item, heroHeight, scroll)
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                // At least the picture's height; taller when the text needs more room, so nothing is cut off.
                Box(Modifier.fillMaxWidth().heightIn(min = heroHeight)) {
                    Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = margin).padding(bottom = VibeyFinTokens.Space.s4)) {
                        HeroDetails(item, state, centered = true, onPlay, onOpenSeries, onToggleFavorite, onPlayTrailer = onPlayEpisode, onTogglePlayed = onTogglePlayed)
                    }
                }
                Box(Modifier.fillMaxWidth().background(VibeyFinTokens.Colors.bg)) { sections() }
            }
        }
        return
    }
    // The backdrop is one screen high; it is fully dark by the time the page has scrolled that far.
    val firstScreenPx = with(LocalDensity.current) { (screenHeight * FIRST_SCREEN).toPx() }
    val progress = if (firstScreenPx > 0f) scroll.value / firstScreenPx else 0f
    Box(Modifier.fillMaxSize()) {
        ItemBackdrop(item, screenWidth, screenHeight, progress, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            // The top of the first screen is the picture, clear; the details sit at the bottom of it.
            Spacer(Modifier.height(screenHeight * FIRST_SCREEN_CLEAR))
            Column(Modifier.fillMaxWidth(0.46f).widthIn(max = 520.dp).padding(horizontal = margin)) {
                HeroDetails(item, state, centered = false, onPlay, onOpenSeries, onToggleFavorite, onPlayTrailer = onPlayEpisode, onTogglePlayed = onTogglePlayed)
            }
            sections()
        }
    }
}

/** A phone's picture is 2:3: this tall relative to the width. */
private const val PHONE_HERO_BY_WIDTH = 1.5f

/** How far the page has to scroll for the backdrop to be fully darkened: the first screen. */
private const val FIRST_SCREEN = 0.7f

/** The share of the first screen left clear above the details on a tablet. */
private const val FIRST_SCREEN_CLEAR = 0.3f

