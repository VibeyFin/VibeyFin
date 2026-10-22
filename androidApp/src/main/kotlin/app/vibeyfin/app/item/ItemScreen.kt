package app.vibeyfin.app.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
        onRetry = viewModel::onRetry,
        onTogglePlayed = viewModel::onTogglePlayed,
        onToggleEpisodePlayed = viewModel::onToggleEpisodePlayed,
        onSelectSeason = viewModel::onSelectSeason,
        onRetrySimilar = viewModel::onRetrySimilar,
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
    onRetry: () -> Unit,
    onTogglePlayed: () -> Unit,
    onToggleEpisodePlayed: (String) -> Unit,
    onSelectSeason: (String) -> Unit,
    onRetrySimilar: () -> Unit,
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
                        item, state, wide = maxWidth >= WIDE_FROM, columns = if (maxWidth >= TWO_COLUMNS_FROM) 2 else 1,
                        onPlay, onPlayEpisode, onOpen, onToggleEpisodePlayed, onSelectSeason, onRetrySimilar, onRetrySeasons, onRetryEpisodes,
                    )
                }
            }
            FloatingBackButton(onBack)
            if (state.canMark) OverflowMenu(state.markLabel, if (state.item?.played == true) Icons.Default.Clear else Icons.Default.Check, onTogglePlayed)
            ActionSnackbar(state.actionMessage, onRetry = onRetryAction, onDismiss = onDismissMessage)
        }
    }
}

/** From this width the page uses its tablet layout: the backdrop at 16:9 with the details in a column on the left. */
private val WIDE_FROM = 600.dp

/** From this width a season's episodes sit in two columns. */
private val TWO_COLUMNS_FROM = 840.dp

private val POSTER_WIDE = 168.dp
private val CAST_TILE_NARROW = 88.dp
private val CAST_TILE_WIDE = 112.dp

/** The hero under the status bar, then what the page has below it: a show's seasons and episodes, the cast, more like this, the file's details. */
@Composable
private fun ItemPage(
    item: MediaItem,
    state: ItemUiState,
    wide: Boolean,
    columns: Int,
    onPlay: (startOver: Boolean) -> Unit,
    onPlayEpisode: (String) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onToggleEpisodePlayed: (String) -> Unit,
    onSelectSeason: (String) -> Unit,
    onRetrySimilar: () -> Unit,
    onRetrySeasons: () -> Unit,
    onRetryEpisodes: () -> Unit,
) {
    // Larger margins and posters on a tablet (`screens.md`).
    val margin = if (wide) VibeyFinTokens.Space.s6 else screenMargin()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ItemHero(item, state, wide, margin, onPlay)
        Column(
            modifier = Modifier.padding(horizontal = margin, vertical = VibeyFinTokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s5),
        ) {
            if (item.kind == ItemKind.Series) {
                SeasonsSection(state.seasons, margin, columns, onSelectSeason, onPlayEpisode, onToggleEpisodePlayed, onRetrySeasons, onRetryEpisodes)
            }
            if (item.cast.isNotEmpty()) CastSection(item.cast, margin, tile = if (wide) CAST_TILE_WIDE else CAST_TILE_NARROW)
            state.similar?.takeIf { it.isVisible }?.let { row ->
                MediaRowSection(row, margin, onOpen, onSeeAll = null, onRetry = onRetrySimilar, tileWidth = if (wide) POSTER_WIDE else VibeyFinTokens.Size.posterWidth)
            }
            if (state.technical.isNotEmpty()) TechnicalInfo(state.technical)
            Spacer(Modifier.height(VibeyFinTokens.Space.s5))
        }
    }
}

/** The "⋮" chip in the top-right corner, opposite the back button, and the menu it opens: Mark as watched or unwatched. */
@Composable
private fun BoxScope.OverflowMenu(markLabel: String, markIcon: ImageVector, onTogglePlayed: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .safeDrawingPadding()
            .padding(end = screenMargin() - ChipInset, top = VibeyFinTokens.Space.s2),
    ) {
        ChipButton(rememberVectorPainter(Icons.Default.MoreVert), Strings.moreActions, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(markLabel) },
                leadingIcon = { Icon(imageVector = markIcon, contentDescription = null) },
                onClick = { open = false; onTogglePlayed() },
            )
        }
    }
}
