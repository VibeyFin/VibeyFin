package app.vibeyfin.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.R
import app.vibeyfin.app.components.MediaRowSection
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.theme.VibeyFinTokens
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
    onSettings: () -> Unit,
    onExit: (HomeExit) -> Unit,
    viewModel: OfflineHomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exit) { state.exit?.let(onExit) }
    OfflineHomeScreen(state, onPlay, onOpenDownloads, onSettings)
}

@Composable
fun OfflineHomeScreen(state: OfflineHomeUiState, onPlay: (String) -> Unit, onOpenDownloads: () -> Unit, onSettings: () -> Unit) {
    val margin = screenMargin()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = margin, vertical = VibeyFinTokens.Space.s3),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1),
                    modifier = Modifier.weight(1f).heightIn(min = VibeyFinTokens.Size.touch),
                ) {
                    Icon(painterResource(R.drawable.ic_logo), contentDescription = Strings.appName, tint = VibeyFinTokens.Colors.ink, modifier = Modifier.size(32.dp))
                    Text(Strings.home, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
                }
                OfflineProfileMenu(state.userName, onSettings)
            }
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
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4)) {
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

/** The profile's letter at the top right; with no server there is only Settings to go to (to switch Offline mode off). */
@Composable
private fun OfflineProfileMenu(userName: String?, onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(VibeyFinTokens.Size.touch)
                .clip(CircleShape)
                .background(VibeyFinTokens.Colors.surface3)
                .clickable(role = Role.Button) { open = true }
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
            VibeyFinMenuItem(Strings.settings, Icons.Filled.Settings) { open = false; onSettings() }
        }
    }
}
