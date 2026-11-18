package app.vibeyfin.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.MediaRowSection
import app.vibeyfin.app.components.TabHeader
import app.vibeyfin.app.navigation.LocalBottomBarPadding
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.pageInsetsPadding
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.home.OfflineHomeUiState
import app.vibeyfin.ui.home.OfflineHomeViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Home with no server: what the profile can watch from the device. A card or a movie plays at once, since there is no page of it
 * to open without the server; a show opens the Downloads tab, where its episodes are.
 */
@Composable
fun OfflineHomeRoute(
    onPlay: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onExit: (HomeExit) -> Unit,
    viewModel: OfflineHomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.exit) { state.exit?.let(onExit) }
    OfflineHomeScreen(state, onPlay, onOpenDownloads)
}

@Composable
fun OfflineHomeScreen(state: OfflineHomeUiState, onPlay: (String) -> Unit, onOpenDownloads: () -> Unit) {
    val margin = screenMargin()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().pageInsetsPadding()) {
            TabHeader(Strings.home)

            when {
                state.loading -> Box(Modifier.fillMaxSize())

                state.isEmpty -> Column(
                    Modifier.fillMaxSize().padding(horizontal = margin).padding(bottom = VibeyFinTokens.Space.s7),
                    verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Strings.downloadsEmptyTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                    Text(Strings.downloadsEmptyHelp, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }

                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4)) {
                    item(key = "continue") {
                        Box(Modifier.padding(horizontal = margin)) {
                            ContinueWatching(state.continueRow, margin, onPlay, onDetails = onPlay, onLongPress = {}, onRetry = {})
                        }
                    }

                    items(state.rows, key = { it.id }) { row ->
                        Box(Modifier.padding(horizontal = margin)) {
                            MediaRowSection(row, margin, { item: MediaItem -> if (item.kind == ItemKind.Movie) onPlay(item.id) else onOpenDownloads() }, onSeeAll = null, onRetry = {})
                        }
                    }
                }
            }
        }
    }
}
