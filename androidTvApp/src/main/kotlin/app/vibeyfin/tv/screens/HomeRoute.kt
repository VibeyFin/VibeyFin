package app.vibeyfin.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.vibeyfin.design.BrandDisplay
import app.vibeyfin.tv.components.ContinueRowSection
import app.vibeyfin.tv.components.HeroBillboard
import app.vibeyfin.tv.components.HeroPlaceholder
import app.vibeyfin.tv.components.PosterRow
import app.vibeyfin.tv.components.RowError
import app.vibeyfin.tv.components.RowTitle
import app.vibeyfin.tv.components.SecondaryAction
import app.vibeyfin.tv.theme.TvMarginHorizontal
import app.vibeyfin.tv.theme.TvMarginVertical
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.HeroNav
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.home.HomeViewModel
import app.vibeyfin.ui.home.MediaRow
import org.koin.androidx.compose.koinViewModel

/** A poster plays what can be played; shows and collections have no page yet. */
@Composable
fun HomeRoute(
    onExit: (HomeExit) -> Unit,
    onPlay: (String) -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exit) { state.exit?.let(onExit) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onForeground() }
    LaunchedEffect(state.heroNav) {
        when (val nav = state.heroNav) {
            is HeroNav.Play -> onPlay(nav.itemId)
            is HeroNav.Details, null -> Unit
        }
        if (state.heroNav != null) viewModel.onHeroNavHandled()
    }

    val playFocus = remember { FocusRequester() }
    val hero = state.heroes.firstOrNull()
    LaunchedEffect(hero?.id) { if (hero != null) runCatching { playFocus.requestFocus() } }
    val rows = remember(state.rows) { state.rows.filter { it.isVisible } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = TvMarginVertical * 2),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header") {
            Column(Modifier.keepWholeInView()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TvMarginHorizontal, vertical = TvMarginVertical),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Strings.appName,
                        style = BrandDisplay.copy(fontSize = BrandDisplay.fontSize * 0.6f),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    state.userName?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SecondaryAction(Strings.switchProfile, viewModel::onSwitchProfile)
                    SecondaryAction(Strings.signOut, viewModel::onSignOut)
                }
                if (hero != null) {
                    HeroBillboard(
                        hero,
                        onPlay = { viewModel.onHeroPlay(hero.id) },
                        onMyList = { viewModel.onHeroMyList(hero.id) },
                        playModifier = Modifier.focusRequester(playFocus)
                    )
                } else if (state.heroLoading) {
                    HeroPlaceholder()
                }
            }
        }
        if (state.continueRow.cards.isNotEmpty()) {
            item(key = "continue") {
                ContinueRowSection(
                    Strings.continueWatching,
                    state.continueRow.cards,
                    onPlay
                )
            }
        } else {
            state.continueRow.message?.let { message ->
                item(key = "continue-error") {
                    RowTitle(Strings.continueWatching)
                    RowError(message, viewModel::onRetryContinue)
                }
            }
        }
        items(rows, key = { it.id }, contentType = { "row" }) { row ->
            PosterRow(
                row,
                onOpen = { if (!it.isFolder) onPlay(it.id) },
                onRetry = { viewModel.onRetryRow(row.id) })
        }
    }
}

/** A focused child asks the list to show this whole block, not just itself, so the header is never left half cut off. */
private class KeepWholeInViewNode : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(childCoordinates: LayoutCoordinates, boundsProvider: () -> Rect?) = bringIntoView(null)
}

private object KeepWholeInViewElement : ModifierNodeElement<KeepWholeInViewNode>() {
    override fun create() = KeepWholeInViewNode()

    override fun update(node: KeepWholeInViewNode) = Unit

    override fun hashCode() = 0

    override fun equals(other: Any?) = other === this
}

private fun Modifier.keepWholeInView(): Modifier = this then KeepWholeInViewElement
